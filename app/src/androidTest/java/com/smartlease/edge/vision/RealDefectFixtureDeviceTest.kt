package com.smartlease.edge.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartlease.edge.deduction.RepairTariff
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The companion to [YoloSegDefectSegmenterDeviceTest], which deliberately feeds the model a
 * synthetic gradient and therefore proves only that the pipeline *runs*. A frame with no defect
 * in it can never exercise decode or NMS, because there is nothing to decode — so that suite
 * cannot tell a working decoder apart from one that returns empty on every input.
 *
 * This test closes that gap with a real photograph, and takes the whole chain the money
 * actually travels down: JPEG -> letterbox -> forward pass -> decode -> NMS -> canonical
 * `defectClass` -> [RepairTariff] -> rupees.
 *
 * ## Why this fixture, and why it is allowed to assert a detection
 *
 * [FIXTURE] is a held-out test-split image from the internal defect dataset. Before it was
 * committed, the *shipped* `yolov8n_seg.ptl` (not the `.pt` it was exported from) was run over
 * 250 test images through the lite interpreter: 43 produced at least one box at
 * [YoloSegConfig.SCORE_THRESHOLD], and this one scored **0.934 on `crack`** — the highest of
 * the set, and notably on the class the audited metrics call the weakest.
 *
 * That margin is why `assertTrue(defects.isNotEmpty())` is a fair assertion here rather than a
 * flaky one: the fixture clears the 0.45 cutoff twice over. If this test ever goes red it is a
 * real signal — the model asset, the letterboxing, the decoder, or the threshold changed — and
 * the correct response is to investigate, never to relax the assertion.
 */
@RunWith(AndroidJUnit4::class)
class RealDefectFixtureDeviceTest {

    private companion object {
        const val TAG = "RealDefectFixture"

        /** Lives in `app/src/androidTest/assets/`, so it ships with the test APK only. */
        const val FIXTURE = "defect_crack_fixture.jpg"

        /**
         * The fixture is a wall photographed at roughly arm's length. The absolute value does
         * not matter to what is under test — it only converts coverage fraction into sq ft so
         * the tariff has something to price — but it must be non-zero or every area collapses
         * to 0 and the cost assertion would pass for the wrong reason.
         */
        const val FRAME_WIDTH_INCHES = 36f
        const val FRAME_HEIGHT_INCHES = 24f
    }

    /** Note: the *instrumentation* context, not the target context — the fixture is in the test APK. */
    private fun loadFixture(): Bitmap {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open(FIXTURE).use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode $FIXTURE from the test APK's assets")
    }

    @Test
    fun realDefectPhoto_producesDetections() {
        val segmenter = DefectSegmenterFactory.create(context = targetContext())
        assertTrue(
            "Expected the trained segmenter on this device, got ${segmenter.javaClass.simpleName}",
            segmenter.isTrainedModel
        )

        val bitmap = loadFixture()
        Log.i(TAG, "fixture decoded: ${bitmap.width}x${bitmap.height}")

        val defects = segmenter.segmentDefects(bitmap, FRAME_WIDTH_INCHES, FRAME_HEIGHT_INCHES)
        Log.i(TAG, "detections at threshold ${YoloSegConfig.SCORE_THRESHOLD}: ${defects.size}")
        defects.forEach {
            Log.i(TAG, "  class=${it.defectClass} conf=${"%.3f".format(it.confidence)} area=${it.areaSqFtEstimate}")
        }

        assertTrue(
            "The shipped model scored 0.934 on this fixture off-device, so an empty result " +
                "on-device means the model asset, letterboxing, decoder or threshold has changed.",
            defects.isNotEmpty()
        )
    }

    /**
     * The end-to-end assertion this whole suite exists for: a real photograph must produce a
     * detection whose class the rate card can actually price. The P0 bug this guards against
     * priced nothing while every unit test stayed green, because production passed the
     * human-readable label into a rate card keyed on the model's short class names.
     */
    @Test
    fun realDefectPhoto_detectionsResolveToRupees() {
        val segmenter = DefectSegmenterFactory.create(context = targetContext())
        val defects = segmenter.segmentDefects(loadFixture(), FRAME_WIDTH_INCHES, FRAME_HEIGHT_INCHES)
        assertTrue("No detections to price — see realDefectPhoto_producesDetections", defects.isNotEmpty())

        defects.forEach { defect ->
            val rate = RepairTariff.rateFor(defect.defectClass)
            assertNotNull(
                "defectClass '${defect.defectClass}' does not resolve to a rate. If this is a " +
                    "description like 'crack in wall surface' rather than a key like 'crack', " +
                    "the label/key seam has regressed and nothing will be priced in production.",
                rate
            )

            val cost = RepairTariff.costOf(defect.defectClass, defect.areaSqFtEstimate)
            assertNotNull("No cost computed for '${defect.defectClass}'", cost)
            assertTrue(
                "Cost for '${defect.defectClass}' should be at least the minimum charge",
                (cost ?: 0) >= rate!!.minimumRupees
            )
            Log.i(TAG, "priced ${defect.defectClass} -> Rs $cost (min Rs ${rate!!.minimumRupees})")
        }
    }

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext
}
