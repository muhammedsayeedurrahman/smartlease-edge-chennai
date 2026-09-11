package com.smartlease.edge.acoustic

import android.content.Context
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trained hollow/solid verdict, fitted in ml/acoustic and served here.
 *
 * Two things have to line up with training or the model is being fed something it never saw:
 *
 *  - Sample rate. AudioRecord captures at 44.1 kHz; the model was fitted at 22.05 kHz. The
 *    2:1 decimation below averages sample pairs rather than dropping every other sample,
 *    because plain decimation folds everything above 11 kHz back down into the band the
 *    classifier reads as "hollow".
 *  - Windowing. Training clips were cut to 250 ms from the onset and peak-normalised, so the
 *    same crop is applied here.
 */
class TrainedTapClassifier(
    private val bundle: AcousticModelBundle,
    private val extractor: AcousticFeatureExtractor
) {

    /** Probability threshold above which a tap is called hollow. */
    private val decisionThreshold = 0.5f

    /** Below this margin either side of the threshold the model is not confident enough. */
    private val inconclusiveMargin = 0.15f

    data class Verdict(
        val tapVerdict: AcousticTapClassifier.TapVerdict,
        val hollowProbability: Float,
        val note: String
    )

    /**
     * @param pcm raw mono 16-bit samples at [captureSampleRate]
     * @return null when the clip is too short or too quiet to score
     */
    fun classify(pcm: ShortArray, captureSampleRate: Int): Verdict? {
        val clip = prepareClip(pcm, captureSampleRate) ?: return null
        val features = extractor.extract(clip)
        val p = bundle.hollowProbability(features)

        val verdict = when {
            abs(p - decisionThreshold) < inconclusiveMargin -> AcousticTapClassifier.TapVerdict.INCONCLUSIVE
            p > decisionThreshold -> AcousticTapClassifier.TapVerdict.LIKELY_HOLLOW
            else -> AcousticTapClassifier.TapVerdict.LIKELY_SOLID
        }

        // The accuracy quoted here is the grouped cross-validated figure with its interval,
        // never the training-set score -- the report this feeds is shown to a landlord.
        val accuracy = "model trained on ${bundle.trainedTaps} taps, " +
            "cross-validated ${(bundle.groupedAccuracy * 100).roundToInt()}% " +
            "(95% CI ${(bundle.groupedAccuracyCi.first * 100).roundToInt()}-" +
            "${(bundle.groupedAccuracyCi.second * 100).roundToInt()}%)"

        val note = when (verdict) {
            AcousticTapClassifier.TapVerdict.LIKELY_HOLLOW ->
                "Hollow at ${(p * 100).roundToInt()}% confidence — $accuracy"
            AcousticTapClassifier.TapVerdict.LIKELY_SOLID ->
                "Solid at ${((1f - p) * 100).roundToInt()}% confidence — $accuracy"
            AcousticTapClassifier.TapVerdict.INCONCLUSIVE ->
                "Too close to call (${(p * 100).roundToInt()}% hollow) — not reported as a finding"
        }
        return Verdict(verdict, p, note)
    }

    /** Locate the tap, crop 250 ms, resample to the model's rate, peak-normalise. */
    private fun prepareClip(pcm: ShortArray, captureSampleRate: Int): FloatArray? {
        if (pcm.isEmpty()) return null

        var peakIndex = 0
        var peakValue = 0
        for (i in pcm.indices) {
            val a = abs(pcm[i].toInt())
            if (a > peakValue) { peakValue = a; peakIndex = i }
        }
        if (peakValue < MIN_PEAK_AMPLITUDE) return null

        // Keep a short pre-roll so the attack transient is inside the window.
        val preRoll = (captureSampleRate * PRE_ROLL_SECONDS).toInt()
        val windowLength = (captureSampleRate * WINDOW_SECONDS).toInt()
        val start = (peakIndex - preRoll).coerceIn(0, maxOf(0, pcm.size - 1))
        val available = pcm.size - start
        if (available < windowLength / 2) return null

        val captured = FloatArray(windowLength)
        for (i in 0 until minOf(windowLength, available)) {
            captured[i] = pcm[start + i] / 32768f
        }

        val resampled = resample(captured, captureSampleRate, bundle.sampleRate)
        val peak = resampled.maxOfOrNull { abs(it) } ?: 0f
        if (peak < 1e-5f) return null
        for (i in resampled.indices) resampled[i] = resampled[i] / peak
        return resampled
    }

    /**
     * Rate conversion with a box-average anti-alias filter.
     *
     * For the 44.1 -> 22.05 kHz case this averages each sample pair, which suppresses the
     * content that would otherwise alias down into the 2-11 kHz band the classifier weighs
     * most heavily. Adequate here; a polyphase filter would be better if the capture rate
     * ever stops being a clean multiple.
     */
    private fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input.copyOf()
        val ratio = fromRate.toDouble() / toRate
        val outLength = (input.size / ratio).toInt()
        val out = FloatArray(outLength)
        for (i in 0 until outLength) {
            val startIdx = (i * ratio).toInt()
            val endIdx = minOf(((i + 1) * ratio).toInt(), input.size)
            if (startIdx >= input.size) break
            var sum = 0f
            var count = 0
            for (j in startIdx until maxOf(endIdx, startIdx + 1)) {
                if (j < input.size) { sum += input[j]; count++ }
            }
            out[i] = if (count > 0) sum / count else 0f
        }
        return out
    }

    companion object {
        private const val TAG = "TrainedTapClassifier"
        private const val WINDOW_SECONDS = 0.25f
        private const val PRE_ROLL_SECONDS = 0.01f
        private const val MIN_PEAK_AMPLITUDE = 600   // ~ -35 dBFS; quieter than this is not a tap

        /** @return null when no trained model ships, so callers keep the heuristic. */
        fun create(context: Context): TrainedTapClassifier? {
            val bundle = AcousticModelBundle.load(context) ?: return null
            Log.i(TAG, "Trained acoustic model loaded (${bundle.trainedTaps} taps)")
            return TrainedTapClassifier(bundle, AcousticFeatureExtractor(bundle))
        }
    }
}
