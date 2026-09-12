package com.smartlease.edge.deduction

import com.smartlease.edge.vision.YoloSegConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the seam where the vision model's vocabulary meets the costing engine.
 *
 * This exists because the two sides drifted apart silently and every test stayed green.
 * `DeductionEngineTest` fed the engine the fixture string `"crack"`, but the walkthrough fed
 * it `DefectSegmenter.Defect.label` — the tenant-facing `"crack in wall surface"` — which is
 * not a key in the rate card. So on a real phone every trained detection fell through to
 * "no agreed rate for this defect type" and **no visual defect was ever priced**, while the
 * suite reported full confidence in the pricing path.
 *
 * A test that invents its own fixture strings cannot detect a vocabulary mismatch. These
 * assertions are written against the shipping constants, and the second one deliberately
 * pins the *wrong* string too — because "look the rate up by the label instead" is the
 * simplification a future reader will reach for, and it must fail loudly here.
 */
class RepairTariffCoverageTest {

    @Test
    fun `every class the shipping model can emit has a repair rate`() {
        for (index in 0 until YoloSegConfig.NUM_CLASSES) {
            val key = YoloSegConfig.classKey(index)
            assertNotNull(
                "No RepairTariff rate for model class '$key' — a defect the model detects " +
                    "would appear in the report but never be priced.",
                RepairTariff.rateFor(key)
            )
        }
    }

    @Test
    fun `the tenant-facing description is not a rate-card key, and must never be used as one`() {
        for (index in 0 until YoloSegConfig.NUM_CLASSES) {
            val description = YoloSegConfig.classDescription(index)
            assertNull(
                "'$description' resolved to a rate. Descriptions are display strings; if one " +
                    "becomes a valid key the two vocabularies have merged and the next rename " +
                    "will silently unprice every defect again. Cost by classKey(index).",
                RepairTariff.rateFor(description)
            )
        }
    }

    @Test
    fun `an out-of-range class index is unpriced rather than mispriced`() {
        val key = YoloSegConfig.classKey(YoloSegConfig.NUM_CLASSES + 1)
        assertEquals(YoloSegConfig.UNCLASSIFIED_KEY, key)
        assertNull(
            "An unrecognised class must fall through to human review, not borrow another " +
                "class's rate.",
            RepairTariff.rateFor(key)
        )
    }

    @Test
    fun `a rate carries a display name so the report never prints a raw dataset label`() {
        for (index in 0 until YoloSegConfig.NUM_CLASSES) {
            val rate = RepairTariff.rateFor(YoloSegConfig.classKey(index)) ?: continue
            assertEquals(
                "Display name for '${YoloSegConfig.classKey(index)}' leaks the underscore " +
                    "form into a tenant-facing PDF.",
                false,
                rate.displayName.contains('_')
            )
        }
    }
}
