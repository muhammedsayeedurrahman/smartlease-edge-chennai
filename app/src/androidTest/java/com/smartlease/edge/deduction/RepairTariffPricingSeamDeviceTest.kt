package com.smartlease.edge.deduction

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartlease.edge.vision.YoloSegConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device regression guard for the P0 pricing-seam bug: the walkthrough used to feed
 * [com.smartlease.edge.vision.DefectSegmenter.Defect.label] (the tenant-facing description,
 * e.g. "crack in wall surface") into [RepairTariff.rateFor], which is keyed on the canonical
 * class name (e.g. "crack"). Every real detection therefore fell through to "no agreed rate"
 * and nothing was ever priced, while [RepairTariffCoverageTest] — a JVM unit test with its own
 * fixture strings — stayed green throughout.
 *
 * `RepairTariffCoverageTest` already pins this on the JVM. This class re-asserts the same
 * contract as an instrumentation test running against the real on-device classes, so the seam
 * is also verified in the environment the app actually ships to (task item 4 of the on-device
 * instrumentation pass), rather than trusting the JVM unit test alone.
 */
@RunWith(AndroidJUnit4::class)
class RepairTariffPricingSeamDeviceTest {

    @Test
    fun everyCanonicalClassName_resolvesToARate() {
        YoloSegConfig.CLASS_NAMES.forEach { className ->
            val rate = RepairTariff.rateFor(className)
            Log.i(TAG, "rateFor(className='$className') -> $rate")
            assertNotNull(
                "RepairTariff.rateFor('$className') returned null. Every canonical class name " +
                    "the shipping model can emit must resolve to a rate, or a real detection " +
                    "will never be priced.",
                rate
            )
        }
    }

    @Test
    fun everyHumanReadableDescription_resolvesToNoRate() {
        YoloSegConfig.CLASS_DESCRIPTIONS.forEach { description ->
            val rate = RepairTariff.rateFor(description)
            Log.i(TAG, "rateFor(description='$description') -> $rate")
            assertNull(
                "RepairTariff.rateFor('$description') returned a rate. Descriptions are " +
                    "display-only strings; if one resolves to a rate the two vocabularies have " +
                    "merged and the exact P0 bug this test guards against (label passed where " +
                    "the class key belongs, pricing nothing at runtime) can silently return.",
                rate
            )
        }
    }

    private companion object {
        const val TAG = "RepairTariffSeamDeviceTest"
    }
}
