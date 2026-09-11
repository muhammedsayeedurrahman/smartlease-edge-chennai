package com.smartlease.edge.acoustic

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlin.math.log10
import kotlin.math.max

/**
 * Acoustic tap classifier — deliberately built as an amplitude/decay-time heuristic,
 * NOT a claim of a trained YamNet-style classifier.
 *
 * Design-doc honesty note this code follows: YamNet's pretrained weights recognize general
 * sound events ("knock", "tap") but do not natively distinguish a hollow-tile tap from a
 * solid-masonry tap — that distinction needs training on labeled data this project does not
 * yet have (see the 12-day plan, Days 1-5). Rather than pretend that training already
 * happened, this classifier does real signal analysis that's honestly described:
 * a sharp tap with fast energy decay and energy concentrated in a higher-frequency band
 * reads as "likely hollow"; a duller tap with slower decay and more low-frequency energy
 * reads as "likely solid". This is a real, working, deterministic heuristic — swap the
 * `classify()` body for a trained-model call once Days 3-5 of the plan produce one, without
 * touching any other file, since TapResult is the stable contract.
 */
object AcousticTapClassifier {

    private const val SAMPLE_RATE = 44100
    private const val WINDOW_MS = 250
    private const val WINDOW_SAMPLES = 4096 // power of two, ~93ms at 44.1kHz — good enough for a tap transient

    enum class TapVerdict { LIKELY_HOLLOW, LIKELY_SOLID, INCONCLUSIVE }

    data class TapResult(
        val verdict: TapVerdict,
        val peakDb: Float,
        val decayMillis: Float,
        val highFreqRatio: Float,
        val confidenceNote: String
    )

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun recordAndClassifyOneTap(
        recordMillis: Int = 1200,
        trained: TrainedTapClassifier? = null
    ): TapResult {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = max(minBufferBytes, WINDOW_SAMPLES * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        )

        val totalSamples = (SAMPLE_RATE.toLong() * recordMillis / 1000L).toInt()
        val pcm = ShortArray(totalSamples)

        try {
            recorder.startRecording()
            var read = 0
            while (read < totalSamples) {
                val n = recorder.read(pcm, read, totalSamples - read)
                if (n <= 0) break
                read += n
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        return classify(pcm, trained)
    }

    /**
     * Pure function, unit-testable without any Android audio hardware.
     *
     * When a [TrainedTapClassifier] is supplied its verdict wins, and the heuristic's
     * measurements (peak, decay, high-frequency ratio) are still reported alongside so the
     * two can be compared in the field. With no model the deterministic heuristic stands --
     * it is honest about being uncalibrated, which is preferable to a confident wrong answer.
     */
    fun classify(pcm: ShortArray, trained: TrainedTapClassifier? = null): TapResult {
        if (pcm.isEmpty()) {
            return TapResult(TapVerdict.INCONCLUSIVE, -120f, 0f, 0f, "Empty buffer")
        }

        val normalized = FloatArray(pcm.size) { pcm[it] / 32768f }
        val envelope = movingRmsEnvelope(normalized, windowSize = 256)

        val peakIndex = envelope.indices.maxByOrNull { envelope[it] } ?: 0
        val peakAmplitude = envelope[peakIndex].coerceAtLeast(1e-6f)
        val peakDb = 20f * log10(peakAmplitude)

        if (peakDb < -50f) {
            return TapResult(
                TapVerdict.INCONCLUSIVE, peakDb, 0f, 0f,
                "No clear tap detected (peak below -50dB) — ask the user to tap again, closer to the mic."
            )
        }

        val decaySamples = decayTimeToFraction(envelope, peakIndex, peakAmplitude, fraction = 0.3f)
        val decayMillis = decaySamples * 1000f / SAMPLE_RATE

        val windowStart = (peakIndex - WINDOW_SAMPLES / 2).coerceIn(0, max(0, normalized.size - WINDOW_SAMPLES))
        val windowEnd = (windowStart + WINDOW_SAMPLES).coerceAtMost(normalized.size)
        val window = FloatArray(WINDOW_SAMPLES)
        System.arraycopy(normalized, windowStart, window, 0, windowEnd - windowStart)

        val spectrum = Fft.magnitudeSpectrum(applyHannWindow(window))
        val highFreqRatio = highFrequencyEnergyRatio(spectrum, sampleRate = SAMPLE_RATE, splitHz = 2000)

        // Heuristic thresholds — starting points, not trained. Days 1-5 of the 12-day plan
        // call for calibrating these against real recorded hollow/solid tap samples before
        // the hackathon; treat these numbers as a first pass, not a finished model.
        val verdict = when {
            decayMillis < 40f && highFreqRatio > 0.55f -> TapVerdict.LIKELY_HOLLOW
            decayMillis > 70f && highFreqRatio < 0.40f -> TapVerdict.LIKELY_SOLID
            else -> TapVerdict.INCONCLUSIVE
        }

        val note = when (verdict) {
            TapVerdict.LIKELY_HOLLOW -> "Fast decay (${decayMillis.toInt()}ms) + high-frequency-heavy tap — consistent with a hollow cavity behind the surface."
            TapVerdict.LIKELY_SOLID -> "Slow decay (${decayMillis.toInt()}ms) + low-frequency-heavy tap — consistent with solid masonry."
            TapVerdict.INCONCLUSIVE -> "Decay/frequency profile didn't clearly match either pattern — uncalibrated thresholds, or an ambiguous tap. Not reported as a finding."
        }

        val modelVerdict = trained?.classify(pcm, SAMPLE_RATE)
        if (modelVerdict != null) {
            return TapResult(
                verdict = modelVerdict.tapVerdict,
                peakDb = peakDb,
                decayMillis = decayMillis,
                highFreqRatio = highFreqRatio,
                confidenceNote = modelVerdict.note
            )
        }

        return TapResult(verdict, peakDb, decayMillis, highFreqRatio, note)
    }

    private fun movingRmsEnvelope(samples: FloatArray, windowSize: Int): FloatArray {
        val envelope = FloatArray(samples.size)
        var sumSquares = 0.0
        for (i in samples.indices) {
            val incoming = samples[i].toDouble() * samples[i]
            sumSquares += incoming
            if (i >= windowSize) {
                val outgoing = samples[i - windowSize].toDouble() * samples[i - windowSize]
                sumSquares -= outgoing
            }
            val count = minOf(i + 1, windowSize)
            envelope[i] = kotlin.math.sqrt(sumSquares / count).toFloat()
        }
        return envelope
    }

    private fun decayTimeToFraction(
        envelope: FloatArray, peakIndex: Int, peakAmplitude: Float, fraction: Float
    ): Int {
        val target = peakAmplitude * fraction
        var i = peakIndex
        while (i < envelope.size && envelope[i] > target) i++
        return (i - peakIndex).coerceAtLeast(1)
    }

    private fun applyHannWindow(samples: FloatArray): FloatArray {
        val n = samples.size
        return FloatArray(n) { i ->
            val w = 0.5f * (1f - kotlin.math.cos(2f * kotlin.math.PI.toFloat() * i / (n - 1)))
            samples[i] * w
        }
    }

    private fun highFrequencyEnergyRatio(spectrum: FloatArray, sampleRate: Int, splitHz: Int): Float {
        val binHz = sampleRate.toFloat() / (spectrum.size * 2)
        val splitBin = (splitHz / binHz).toInt().coerceIn(0, spectrum.size - 1)
        var lowEnergy = 0.0
        var highEnergy = 0.0
        for (i in spectrum.indices) {
            val e = spectrum[i].toDouble() * spectrum[i]
            if (i < splitBin) lowEnergy += e else highEnergy += e
        }
        val total = lowEnergy + highEnergy
        return if (total < 1e-9) 0f else (highEnergy / total).toFloat()
    }
}
