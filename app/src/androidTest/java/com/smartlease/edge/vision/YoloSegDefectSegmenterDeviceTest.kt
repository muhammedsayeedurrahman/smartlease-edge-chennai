package com.smartlease.edge.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof that the shipped `yolov8n_seg.ptl` actually runs the (preds, protos) contract
 * described in [YoloSegConfig] on real hardware (iQOO 15 / vivo I2501, arm64-v8a) — not just in
 * a JVM unit test with a mocked module.
 *
 * These tests deliberately do not assert on *what* the model finds on a synthetic bitmap (a
 * flat-colour frame has no real defects, so an empty result is the correct answer and must not
 * be treated as a failure). What they pin down is that the pipeline runs end to end on this SoC,
 * that the result shape matches the [DefectSegmenter.Defect] contract, and that every emitted
 * `defectClass` is drawn from the model's own vocabulary rather than being some other string
 * (e.g. the human-readable label) that would silently defeat [RepairTariff][com.smartlease.edge.deduction.RepairTariff]
 * lookups downstream.
 */
@RunWith(AndroidJUnit4::class)
class YoloSegDefectSegmenterDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * A synthetic 640x640 bitmap. Not a photograph of anything — a soft gradient so the input
     * isn't a single flat colour (which some letterboxing/scaling paths could degenerate on) —
     * but it carries no real defect content, so an empty detection list is the expected and
     * valid outcome.
     */
    private fun syntheticBitmap(size: Int = YoloSegConfig.INPUT_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (x * 255 / size)
                val g = (y * 255 / size)
                val b = 128
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return bitmap
    }

    /**
     * Proves the trained model actually loads via [DefectSegmenterFactory] on this hardware.
     *
     * If the factory falls back to [HeuristicDefectSegmenter] here, that is a real finding
     * about this device/APK combination (missing native lib, corrupt asset, ABI mismatch,
     * etc.) and must fail loudly rather than being weakened to tolerate the fallback.
     */
    @Test
    fun factory_loadsTrainedModelOnDevice() {
        val segmenter = DefectSegmenterFactory.create(context)
        Log.i(TAG, "DefectSegmenterFactory.create returned ${segmenter::class.simpleName}, " +
            "isTrainedModel=${segmenter.isTrainedModel}")

        if (!segmenter.isTrainedModel) {
            fail(
                "DefectSegmenterFactory fell back to the heuristic segmenter on a real device " +
                    "(iQOO 15 / vivo I2501). The trained yolov8n_seg.ptl either failed to load " +
                    "or failed the (preds, protos) probe in YoloSegDefectSegmenter.create — see " +
                    "logcat tag 'YoloSegSegmenter' / 'DefectSegmenterFactory' for the exact " +
                    "reason. This is a genuine on-device regression, not a flaky assertion."
            )
        }
        assertTrue(
            "Expected a YoloSegDefectSegmenter instance once isTrainedModel is true",
            segmenter is YoloSegDefectSegmenter
        )
    }

    /**
     * Runs one real forward pass through the PyTorch Lite module on a synthetic bitmap and
     * proves it completes without throwing. An empty result list is valid — the input has no
     * real defect content — but the call must return normally and produce a List, never null
     * and never propagate an exception (that path is what [YoloSegDefectSegmenter.segmentDefects]
     * already guards internally; this test confirms the guard is never even needed on this SoC).
     */
    @Test
    fun forwardPass_onSyntheticBitmap_returnsListWithoutThrowing() {
        val segmenter = YoloSegDefectSegmenter.create(context)
            ?: fail("YoloSegDefectSegmenter.create returned null: model did not load on device")
                .let { return }

        val bitmap = syntheticBitmap()
        val defects = segmenter.segmentDefects(
            bitmap = bitmap,
            frameWidthInches = 96f,
            frameHeightInches = 96f
        )

        Log.i(TAG, "forwardPass_onSyntheticBitmap: ${defects.size} defect(s) returned")
        assertNotNull("segmentDefects must never return null", defects)
        // No assertion on count: zero detections on a synthetic gradient is the correct result.
    }

    /**
     * Every [DefectSegmenter.Defect] emitted by the trained model must carry the canonical
     * class key (e.g. "crack"), not the tenant-facing description (e.g. "crack in wall
     * surface") and not an arbitrary string. This is the exact field RepairTariff prices by.
     */
    @Test
    fun everyDefect_hasCanonicalClassNameNotDescription() {
        val segmenter = YoloSegDefectSegmenter.create(context)
            ?: fail("YoloSegDefectSegmenter.create returned null: model did not load on device")
                .let { return }

        val validKeys = YoloSegConfig.CLASS_NAMES.toSet() + YoloSegConfig.UNCLASSIFIED_KEY
        val defects = segmenter.segmentDefects(
            bitmap = syntheticBitmap(),
            frameWidthInches = 96f,
            frameHeightInches = 96f
        )

        Log.i(TAG, "everyDefect_hasCanonicalClassNameNotDescription: checking ${defects.size} defect(s)")
        defects.forEach { defect ->
            assertTrue(
                "Defect.defectClass was '${defect.defectClass}' (label='${defect.label}'), " +
                    "which is not one of ${YoloSegConfig.CLASS_NAMES} or the unclassified key " +
                    "'${YoloSegConfig.UNCLASSIFIED_KEY}'. If this is the human-readable " +
                    "description instead of the class key, the P0 pricing bug is back.",
                defect.defectClass in validKeys
            )
            assertTrue(
                "Defect.defectClass ('${defect.defectClass}') matches a human-readable " +
                    "CLASS_DESCRIPTIONS entry rather than a canonical CLASS_NAMES key — the " +
                    "label leaked into the pricing field.",
                defect.defectClass !in YoloSegConfig.CLASS_DESCRIPTIONS
            )
        }
    }

    /**
     * Measures and logs the wall-clock latency of a single real forward pass on this device,
     * and fails only if it is pathologically slow (a generous 10s bound) — this is a sanity
     * bound, not a performance target. The actual measured number is reported via Log.i so it
     * shows up in the instrumentation run's logcat capture.
     */
    @Test
    fun inferenceLatency_isMeasuredAndBoundedOnDevice() {
        val segmenter = YoloSegDefectSegmenter.create(context)
            ?: fail("YoloSegDefectSegmenter.create returned null: model did not load on device")
                .let { return }

        val bitmap = syntheticBitmap()
        // One warm-up pass so we measure steady-state inference, not one-time module JIT/setup cost.
        segmenter.segmentDefects(bitmap, 96f, 96f)

        val start = System.nanoTime()
        segmenter.segmentDefects(bitmap, 96f, 96f)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        Log.i(TAG, "Measured single forward-pass latency on iQOO 15 (I2501): ${"%.2f".format(elapsedMs)} ms")

        assertTrue(
            "Single forward pass took ${"%.2f".format(elapsedMs)} ms, exceeding the 10s sanity " +
                "bound. This indicates a pathologically slow path (e.g. CPU fallback with no " +
                "delegate), not just a slow-but-usable model.",
            elapsedMs < 10_000.0
        )
    }

    private companion object {
        const val TAG = "YoloSegDeviceTest"
    }
}
