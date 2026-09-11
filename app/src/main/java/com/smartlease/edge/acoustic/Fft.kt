package com.smartlease.edge.acoustic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal iterative radix-2 Cooley-Tukey FFT, real-valued input.
 * No external DSP dependency — self-contained so this module has zero third-party risk.
 * Input length must be a power of two; callers pad/trim to the nearest power of two.
 */
object Fft {

    fun magnitudeSpectrum(samples: FloatArray): FloatArray {
        val n = samples.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT input length must be a power of two, was $n" }

        val re = DoubleArray(n) { samples[it].toDouble() }
        val im = DoubleArray(n)

        fftInPlace(re, im)

        val half = n / 2
        return FloatArray(half) { i ->
            val magnitude = kotlin.math.sqrt(re[i] * re[i] + im[i] * im[i])
            magnitude.toFloat()
        }
    }

    /**
     * Power spectrum with n/2 + 1 bins, i.e. DC through Nyquist inclusive.
     *
     * [magnitudeSpectrum] drops the Nyquist bin (it returns n/2), which is fine for the
     * band-ratio heuristic but not for the trained classifier: the mel filterbank exported
     * from Python is shaped (40, n/2 + 1), so a 256-bin input would silently misalign every
     * filter against the wrong frequencies.
     */
    fun powerSpectrum(samples: FloatArray): FloatArray {
        val n = samples.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT input length must be a power of two, was $n" }

        val re = DoubleArray(n) { samples[it].toDouble() }
        val im = DoubleArray(n)
        fftInPlace(re, im)

        return FloatArray(n / 2 + 1) { i ->
            (re[i] * re[i] + im[i] * im[i]).toFloat()
        }
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        // Iterative butterfly
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var curWr = 1.0
                var curWi = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curWr - im[i + k + len / 2] * curWi
                    val vIm = re[i + k + len / 2] * curWi + im[i + k + len / 2] * curWr

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm

                    val nextWr = curWr * wr - curWi * wi
                    val nextWi = curWr * wi + curWi * wr
                    curWr = nextWr
                    curWi = nextWi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
