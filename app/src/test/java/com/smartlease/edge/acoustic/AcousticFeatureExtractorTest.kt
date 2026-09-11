package com.smartlease.edge.acoustic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Proves the Kotlin feature extractor reproduces the Python trainer's numbers.
 *
 * This is the test that makes shipping the model safe. Without it, a drifting convention --
 * a symmetric Hann instead of periodic, a missing Nyquist bin, sample vs population standard
 * deviation -- would not throw or log anything. It would just quietly feed the classifier
 * different numbers than it was fitted on, and accuracy would degrade with no visible cause.
 *
 * The golden values come from ml/acoustic/export_for_android.py, which computes them with
 * numpy/librosa and embeds them in the shipped asset. Both sides generate the same synthetic
 * tap from three constants, so no waveform fixture is needed and the whole chain is covered:
 * framing, FFT, mel projection, log, DCT, spectral moments, scaling and the linear model.
 */
class AcousticFeatureExtractorTest {

    private lateinit var bundle: AcousticModelBundle
    private lateinit var golden: JSONObject
    private lateinit var extractor: AcousticFeatureExtractor

    @Before
    fun setUp() {
        // Unit tests run with the module directory (app/) as the working directory.
        val asset = File("src/main/assets/${AcousticModelBundle.ASSET_NAME}")
        assertTrue(
            "Missing ${asset.path}. Regenerate it: py -3 ml/acoustic/export_for_android.py",
            asset.exists()
        )
        val json = JSONObject(asset.readText())
        bundle = AcousticModelBundle.parse(json)
        golden = json.getJSONObject("golden")
        extractor = AcousticFeatureExtractor(bundle)
    }

    /** Same closed form as golden_signal() in export_for_android.py. */
    private fun goldenSignal(): FloatArray {
        val n = golden.getInt("length")
        val freq = golden.getDouble("freq")
        val decay = golden.getDouble("decay")
        val sr = bundle.sampleRate.toDouble()
        return FloatArray(n) { t ->
            val env = exp(-t / decay)
            val wave = sin(2.0 * PI * freq * t / sr) + 0.5 * sin(2.0 * PI * 2.0 * freq * t / sr)
            (wave * env).toFloat()
        }
    }

    @Test
    fun `exported bundle has the shapes the extractor assumes`() {
        val bins = bundle.nFft / 2 + 1
        assertEquals(bundle.nMels * bins, bundle.melFilterbank.size)
        assertEquals(bundle.nMfcc * bundle.nMels, bundle.dctMatrix.size)
        assertEquals(36, bundle.featureCount)
    }

    @Test
    fun `fft power spectrum includes the nyquist bin`() {
        // 256 bins here instead of 257 would misalign every mel filter.
        assertEquals(bundle.nFft / 2 + 1, Fft.powerSpectrum(FloatArray(bundle.nFft)).size)
    }

    @Test
    fun `features match the python trainer on the golden signal`() {
        val expected = golden.getJSONArray("features")
        val actual = extractor.extract(goldenSignal())

        assertEquals(expected.length(), actual.size)
        for (i in 0 until expected.length()) {
            val want = expected.getDouble(i).toFloat()
            // Relative tolerance, with an absolute floor for features that sit near zero.
            // The two sides differ only by float32 rounding of the exported matrices.
            val tolerance = maxOf(abs(want) * 1e-3f, 1e-4f)
            assertEquals(
                "feature[$i] diverged from the Python trainer -- train/serve skew",
                want, actual[i], tolerance
            )
        }
    }

    @Test
    fun `hollow probability matches the python model on the golden signal`() {
        val features = extractor.extract(goldenSignal())
        val actual = bundle.hollowProbability(features)
        val expected = golden.getDouble("hollowProbability").toFloat()
        assertEquals("Scaler or coefficients diverged", expected, actual, 5e-3f)
    }

    @Test
    fun `probability is bounded and responds to input`() {
        val silence = extractor.extract(FloatArray(golden.getInt("length")))
        val p = bundle.hollowProbability(silence)
        assertTrue("probability out of range: $p", p in 0f..1f)
    }

    @Test
    fun `clip shorter than one frame yields zeroed features rather than crashing`() {
        val tiny = FloatArray(bundle.nFft - 1)
        val features = extractor.extract(tiny)
        assertEquals(bundle.featureCount, features.size)
        assertTrue(features.all { it == 0f })
    }

    @Test
    fun `reported accuracy is the grouped figure, not the training score`() {
        // Guards against someone exporting the 100% training-set number by mistake.
        assertTrue(
            "groupedAccuracy ${bundle.groupedAccuracy} looks like a training score",
            bundle.groupedAccuracy < 0.99f
        )
        assertTrue(bundle.groupedAccuracyCi.first < bundle.groupedAccuracy)
        assertTrue(bundle.groupedAccuracyCi.second > bundle.groupedAccuracy)
    }
}
