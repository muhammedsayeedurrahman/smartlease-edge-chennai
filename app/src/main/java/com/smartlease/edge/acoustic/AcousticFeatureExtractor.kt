package com.smartlease.edge.acoustic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Kotlin mirror of ml/acoustic/android_features.py.
 *
 * The two implementations must agree numerically or the model is served different numbers
 * than it was fitted on -- train/serve skew, which degrades accuracy silently and logs
 * nothing. Two things keep them aligned:
 *
 *  1. The mel filterbank and DCT-II basis are not rebuilt here. They are exported as plain
 *     matrices in acoustic_tap_model.json and multiplied as-is, so there is no chance of a
 *     mel-scale or normalisation convention diverging between the two languages.
 *  2. AcousticFeatureExtractorTest checks this code against golden feature values produced
 *     by the Python exporter. If either side changes, that test fails.
 *
 * Feature order is positional and must match the Python docstring exactly:
 *   [0:13] MFCC mean, [13:26] MFCC std, [26] centroid mean, [27] centroid std,
 *   [28] rolloff85, [29] bandwidth, [30] flatness, [31] ZCR, [32] decay ms,
 *   [33] low ratio, [34] mid ratio, [35] high ratio
 */
class AcousticFeatureExtractor(private val config: AcousticModelBundle) {

    private val bins = config.nFft / 2 + 1
    private val window = FloatArray(config.nFft) { i ->
        // Periodic Hann (fftbins=True), NOT the symmetric variant used by the heuristic path.
        (0.5 - 0.5 * cos(2.0 * PI * i / config.nFft)).toFloat()
    }
    private val freqs = FloatArray(bins) { i ->
        (config.sampleRate / 2.0f) * i / (bins - 1)
    }

    /**
     * @param clip amplitude-normalised mono samples at [AcousticModelBundle.sampleRate]
     * @return [AcousticModelBundle.featureCount] features, or all zeros if the clip is too
     *         short to yield a single frame
     */
    fun extract(clip: FloatArray): FloatArray {
        val frames = frameCount(clip.size)
        if (frames <= 0) return FloatArray(config.featureCount)

        // --- Power spectrogram, (bins x frames) stored column-major by frame -------------
        val power = Array(frames) { f ->
            val frame = FloatArray(config.nFft) { i -> clip[f * config.hop + i] * window[i] }
            Fft.powerSpectrum(frame)
        }

        // --- Mel projection, log, DCT -> MFCC -------------------------------------------
        val logMel = Array(frames) { f ->
            val mel = FloatArray(config.nMels)
            for (m in 0 until config.nMels) {
                var acc = 0.0
                val rowOffset = m * bins
                val col = power[f]
                for (b in 0 until bins) acc += config.melFilterbank[rowOffset + b] * col[b]
                mel[m] = (10.0 * log10(max(acc, config.amin))).toFloat()
            }
            mel
        }
        // top_db clamp is taken over the whole spectrogram, matching the Python side.
        var globalMax = Float.NEGATIVE_INFINITY
        for (f in 0 until frames) for (m in 0 until config.nMels) {
            if (logMel[f][m] > globalMax) globalMax = logMel[f][m]
        }
        val floor = globalMax - config.topDb.toFloat()
        for (f in 0 until frames) for (m in 0 until config.nMels) {
            if (logMel[f][m] < floor) logMel[f][m] = floor
        }

        val mfcc = Array(config.nMfcc) { k ->
            FloatArray(frames) { f ->
                var acc = 0.0
                val rowOffset = k * config.nMels
                for (m in 0 until config.nMels) acc += config.dctMatrix[rowOffset + m] * logMel[f][m]
                acc.toFloat()
            }
        }

        // --- Per-frame spectral moments --------------------------------------------------
        val centroid = FloatArray(frames)
        val bandwidth = FloatArray(frames)
        val rolloff = FloatArray(frames)
        val flatness = FloatArray(frames)

        for (f in 0 until frames) {
            val col = power[f]
            var energy = 0.0
            for (b in 0 until bins) energy += col[b]
            energy += 1e-12

            var weighted = 0.0
            for (b in 0 until bins) weighted += freqs[b] * col[b]
            val c = weighted / energy
            centroid[f] = c.toFloat()

            var spread = 0.0
            for (b in 0 until bins) {
                val d = freqs[b] - c
                spread += d * d * col[b]
            }
            bandwidth[f] = sqrt(spread / energy).toFloat()

            // Rolloff: lowest frequency below which 85% of the energy lies.
            val threshold = 0.85 * (energy - 1e-12)
            var cumulative = 0.0
            var idx = bins - 1
            for (b in 0 until bins) {
                cumulative += col[b]
                if (cumulative >= threshold) { idx = b; break }
            }
            rolloff[f] = freqs[idx]

            var logSum = 0.0
            var linSum = 0.0
            for (b in 0 until bins) {
                logSum += ln(col[b] + 1e-12)
                linSum += col[b]
            }
            flatness[f] = (exp(logSum / bins) / (linSum / bins + 1e-12)).toFloat()
        }

        // --- Time domain -----------------------------------------------------------------
        var crossings = 0
        for (i in 1 until clip.size) {
            if (abs(sign(clip[i]) - sign(clip[i - 1])) > 0f) crossings++
        }
        val zcr = if (clip.size > 1) crossings.toFloat() / (clip.size - 1) else 0f

        var peakValue = 0f
        var peakIndex = 0
        for (i in clip.indices) {
            val a = abs(clip[i])
            if (a > peakValue) { peakValue = a; peakIndex = i }
        }
        val norm = peakValue + 1e-12f
        var decaySamples = (clip.size - peakIndex).toFloat()
        for (i in peakIndex until clip.size) {
            if (abs(clip[i]) / norm < 0.1f) { decaySamples = (i - peakIndex).toFloat(); break }
        }
        val decayMs = decaySamples / config.sampleRate * 1000f

        // --- Band energy ratios ------------------------------------------------------------
        var low = 0.0; var mid = 0.0; var high = 0.0
        for (b in 0 until bins) {
            var acc = 0.0
            for (f in 0 until frames) acc += power[f][b]
            when {
                freqs[b] < 500f -> low += acc
                freqs[b] < 2000f -> mid += acc
                else -> high += acc
            }
        }
        val total = low + mid + high + 1e-12

        // --- Assemble in the fixed order ----------------------------------------------------
        val out = FloatArray(config.featureCount)
        for (k in 0 until config.nMfcc) {
            out[k] = mean(mfcc[k])
            out[config.nMfcc + k] = populationStd(mfcc[k], out[k])
        }
        var i = 2 * config.nMfcc
        out[i++] = mean(centroid)
        out[i++] = populationStd(centroid, mean(centroid))
        out[i++] = mean(rolloff)
        out[i++] = mean(bandwidth)
        out[i++] = mean(flatness)
        out[i++] = zcr
        out[i++] = decayMs
        out[i++] = (low / total).toFloat()
        out[i++] = (mid / total).toFloat()
        out[i] = (high / total).toFloat()
        return out
    }

    fun frameCount(sampleCount: Int): Int =
        if (sampleCount < config.nFft) 0 else 1 + (sampleCount - config.nFft) / config.hop

    private fun mean(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0.0
        for (v in values) sum += v
        return (sum / values.size).toFloat()
    }

    /** Population standard deviation (ddof = 0), matching numpy's default. */
    private fun populationStd(values: FloatArray, mean: Float): Float {
        if (values.isEmpty()) return 0f
        var sum = 0.0
        for (v in values) {
            val d = v - mean
            sum += d * d
        }
        return sqrt(sum / values.size).toFloat()
    }
}
