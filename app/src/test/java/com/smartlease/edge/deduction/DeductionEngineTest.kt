package com.smartlease.edge.deduction

import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeductionEngine] is where a measured defect turns into a rupee figure that comes out of
 * someone's deposit, so the tests here are written from the dispute, not from the code: what
 * must never be priced (below-floor confidence, the colour heuristic, an unrated class) is
 * checked as hard as what must (a confident, trained, rated hit).
 */
class DeductionEngineTest {

    private fun entity(detail: FindingDetail?, type: FindingType = FindingType.VISUAL_DEFECT) =
        InspectionEntity(
            sessionId = SESSION,
            timestampEpochMillis = 1_757_000_000_000L,
            findingType = type,
            label = "test finding",
            detailJson = detail?.toJson() ?: "{}",
            severity = Severity.INFO
        )

    @Test
    fun `trained visual defect above the confidence floor with a known class is priced`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("crack", areaSqFt = 20f, confidence = 0.85f, fromTrainedModel = true))
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        assertEquals(1, summary.lines.size)
        // 20 sq ft * Rs.85/sq ft = Rs.1700, above the Rs.1500 call-out minimum.
        assertEquals(1_700, summary.lines[0].amountRupees)
        assertTrue(summary.clearedNotes.isEmpty())
    }

    @Test
    fun `the same defect below the confidence floor is not priced`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("crack", areaSqFt = 20f, confidence = 0.45f, fromTrainedModel = true))
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        assertTrue(summary.lines.isEmpty())
        assertEquals(1, summary.clearedNotes.size)
    }

    @Test
    fun `a colour-heuristic hit at high confidence is not priced`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("crack", areaSqFt = 20f, confidence = 0.95f, fromTrainedModel = false))
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        assertTrue(summary.lines.isEmpty())
        assertEquals(1, summary.clearedNotes.size)
    }

    @Test
    fun `an unknown defect class is not priced`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("mystery-defect", areaSqFt = 20f, confidence = 0.9f, fromTrainedModel = true))
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        assertTrue(summary.lines.isEmpty())
        assertEquals(1, summary.clearedNotes.size)
    }

    @Test
    fun `the call-out minimum wins over a tiny measured area`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("crack", areaSqFt = 0.1f, confidence = 0.9f, fromTrainedModel = true))
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        // 0.1 sq ft * Rs.85/sq ft = Rs.8.5, dwarfed by the Rs.1500 minimum call-out.
        assertEquals(1_500, summary.lines[0].amountRupees)
    }

    @Test
    fun `a hollow tap is priced, solid and unclear are not`() {
        val hollow = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.AcousticTap("hollow", confidence = 0.9f, fromTrainedModel = true), FindingType.ACOUSTIC_TAP))
        )
        assertEquals(1, hollow.lines.size)
        assertEquals(RepairTariff.HOLLOW_TILE_RUPEES, hollow.lines[0].amountRupees)

        val solid = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.AcousticTap("solid", confidence = 0.9f, fromTrainedModel = true), FindingType.ACOUSTIC_TAP))
        )
        assertTrue(solid.lines.isEmpty())

        val unclear = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.AcousticTap("unclear", confidence = 0f, fromTrainedModel = false), FindingType.ACOUSTIC_TAP))
        )
        assertTrue(unclear.lines.isEmpty())
    }

    @Test
    fun `a trained hollow tap's basis states the real confidence, not an invented one`() {
        val summary = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.AcousticTap("hollow", confidence = 0.87f, fromTrainedModel = true), FindingType.ACOUSTIC_TAP))
        )

        // 0.87f * 100 rounds to 87 -- the model's actual P(hollow), never a hardcoded 100%.
        assertTrue(summary.lines[0].basis.contains("87% confidence"))
    }

    @Test
    fun `a heuristic-sourced hollow tap is still priced but its basis claims no confidence score`() {
        val summary = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.AcousticTap("hollow", confidence = 0f, fromTrainedModel = false), FindingType.ACOUSTIC_TAP))
        )

        // Billing is unchanged regardless of source -- this is about wording, not price.
        assertEquals(1, summary.lines.size)
        assertEquals(RepairTariff.HOLLOW_TILE_RUPEES, summary.lines[0].amountRupees)

        val basis = summary.lines[0].basis
        // A heuristic reading has no real probability behind it. "0% confidence" would be
        // exactly as dishonest in the other direction as a hardcoded 100% would be.
        assertTrue("expected the heuristic wording, got: $basis", basis.contains("heuristic"))
        assertTrue("must not print a fabricated percentage: $basis", !basis.contains("% confidence"))
    }

    @Test
    fun `a non-functional appliance is priced, a functional one is not`() {
        val broken = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.ApplianceCheck("Voltas AC", functional = false), FindingType.IR_APPLIANCE_CHECK))
        )
        assertEquals(1, broken.lines.size)
        assertEquals(RepairTariff.APPLIANCE_NOT_FUNCTIONAL_RUPEES, broken.lines[0].amountRupees)

        val working = DeductionEngine.summarise(
            DEPOSIT,
            listOf(entity(FindingDetail.ApplianceCheck("Voltas AC", functional = true), FindingType.IR_APPLIANCE_CHECK))
        )
        assertTrue(working.lines.isEmpty())
    }

    @Test
    fun `total is the sum of the lines and refund is deposit minus total`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("crack", areaSqFt = 20f, confidence = 0.85f, fromTrainedModel = true)),
            entity(FindingDetail.AcousticTap("hollow", confidence = 0.9f, fromTrainedModel = true), FindingType.ACOUSTIC_TAP)
        )

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        val expectedTotal = 1_700 + RepairTariff.HOLLOW_TILE_RUPEES
        assertEquals(expectedTotal, summary.totalDeductionRupees)
        assertEquals(DEPOSIT - expectedTotal, summary.refundRupees)
    }

    @Test
    fun `refund goes negative when deductions exceed the deposit -- the engine does not clamp`() {
        val findings = listOf(
            entity(FindingDetail.VisualDefect("spalling", areaSqFt = 200f, confidence = 0.9f, fromTrainedModel = true))
        )

        val summary = DeductionEngine.summarise(1_000, findings)

        // 200 sq ft * Rs.150/sq ft = Rs.30,000, far past a Rs.1,000 deposit.
        assertEquals(30_000, summary.totalDeductionRupees)
        assertEquals(1_000 - 30_000, summary.refundRupees)
        assertTrue(summary.refundRupees < 0)
    }

    @Test
    fun `a finding with an empty detail payload is skipped entirely`() {
        val findings = listOf(InspectionEntity(
            sessionId = SESSION,
            timestampEpochMillis = 1_757_000_000_000L,
            findingType = FindingType.VISUAL_DEFECT,
            label = "legacy row written before structured details existed",
            detailJson = "{}",
            severity = Severity.NOTABLE
        ))

        val summary = DeductionEngine.summarise(DEPOSIT, findings)

        assertTrue(summary.lines.isEmpty())
        assertTrue(summary.clearedNotes.isEmpty())
        assertEquals(DEPOSIT, summary.refundRupees)
    }

    private companion object {
        const val SESSION = "3f2b9c11-7a4e-4f1d-9b23-58c0d7e14a6f"
        const val DEPOSIT = 90_000
    }
}
