package com.smartlease.edge.narration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.FindingDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The audit is the only thing standing between a language model and a document that prices
 * someone's deposit, so these tests are written as attacks on it rather than as a demonstration
 * that it works on a happy path.
 *
 * Instrumented because [InspectionEntity.detailJson] is built through `org.json.JSONObject`,
 * which is stubbed to throw in the local unit-test JVM. Building the findings any other way
 * would be testing a fixture rather than the shape the report pipeline actually carries.
 */
@RunWith(AndroidJUnit4::class)
class NarrationAuditDeviceTest {

    private fun finding(
        label: String = "Hall -- Sides: peeling paint",
        severity: Severity = Severity.NOTABLE
    ) = InspectionEntity(
        sessionId = SESSION_ID,
        timestampEpochMillis = 1_757_000_000_000L,
        findingType = FindingType.VISUAL_DEFECT,
        label = label,
        detailJson = FindingDetail.VisualDefect(
            defectClass = "peeling_paint",
            areaSqFt = 1.2f,
            confidence = 0.71f,
            fromTrainedModel = true
        ).toJson(),
        severity = severity,
        sessionType = SessionType.MOVE_OUT,
        propertyLabel = "Flat 12B"
    )

    private fun rejectionReason(result: NarrationAudit.Result): String {
        assertTrue("expected a rejection, got $result", result is NarrationAudit.Result.Rejected)
        return (result as NarrationAudit.Result.Rejected).reason
    }

    @Test
    fun accurateProseIsAccepted() {
        val result = NarrationAudit.check(
            "Two areas of peeling paint were recorded on the hall walls during this inspection. " +
                "Both were captured on camera and are listed above with their measured areas.",
            listOf(finding(), finding())
        )
        assertEquals(NarrationAudit.Result.Accepted, result)
    }

    @Test
    fun aFabricatedHazardIsRejected() {
        // The single most damaging thing a model can do here: invent an emergency. The verdict
        // line at the top of the report is computed from severities, so prose claiming a hazard
        // would contradict it -- and a tenant reads the prose.
        val reason = rejectionReason(
            NarrationAudit.check(
                "The inspection found peeling paint in the hall, alongside exposed wiring near " +
                    "the ceiling which should be treated as urgent.",
                listOf(finding())
            )
        )
        assertTrue(reason, reason.contains("hazard no finding recorded"))
    }

    @Test
    fun omittingARealHazardIsAlsoRejected() {
        // The mirror case. Softening a STOP_ESCALATE finding into pleasant prose leaves the
        // report saying "attention required" above a paragraph that sounds routine.
        val reason = rejectionReason(
            NarrationAudit.check(
                "Some general wear was noted in the hall during this inspection, consistent " +
                    "with normal use of the property over the tenancy.",
                listOf(finding(label = "Hall -- Sides: exposed wire", severity = Severity.STOP_ESCALATE))
            )
        )
        assertTrue(reason, reason.contains("omits a hazard"))
    }

    @Test
    fun aWrongFindingCountIsRejected() {
        val reason = rejectionReason(
            NarrationAudit.check(
                "A total of 9 findings were recorded across the hall during this inspection, " +
                    "all relating to surface paint condition.",
                listOf(finding(), finding())
            )
        )
        assertTrue(reason, reason.contains("says 9 finding(s); there are 2"))
    }

    @Test
    fun aCorrectFindingCountIsAccepted() {
        val result = NarrationAudit.check(
            "A total of 2 findings were recorded across the hall during this inspection, " +
                "both relating to surface paint condition.",
            listOf(finding(), finding())
        )
        assertEquals(NarrationAudit.Result.Accepted, result)
    }

    @Test
    fun proseThatStatesNoCountIsNotPenalised() {
        // Not stating a count is not the same as stating a wrong one. Demanding a tally would
        // reject accurate descriptive prose, which would push every report back to templates.
        val result = NarrationAudit.check(
            "Peeling paint was recorded on the hall walls, captured on camera and measured " +
                "during the walkthrough of this property.",
            listOf(finding(), finding(), finding())
        )
        assertEquals(NarrationAudit.Result.Accepted, result)
    }

    @Test
    fun anyMoneyFigureIsRejected() {
        // The deduction engine prints a balance sheet from the rate card on the same page. A
        // rupee figure in prose would be the model's own arithmetic sitting beside real
        // arithmetic, with nothing to tell a reader which is which.
        listOf(
            "Repairs for the recorded peeling paint are likely to cost around ₹2,400 in total.",
            "Repairs for the recorded peeling paint are likely to cost around Rs 2,400 in total.",
            // "Rs." with the full stop is the most natural way to write this in Indian English
            // and was the case the first version of the pattern could not match at all.
            "Repairs for the recorded peeling paint are likely to cost around Rs. 2,400 in total.",
            "Repairs for the recorded peeling paint are likely to cost around INR 2400 in total.",
            "Repairs for the recorded peeling paint are likely to cost around 2,400 rupees in total."
        ).forEach { text ->
            val reason = rejectionReason(NarrationAudit.check(text, listOf(finding())))
            assertTrue(reason, reason.contains("money figure"))
        }
    }

    @Test
    fun aMeasurementIsNotMistakenForMoney() {
        // Areas and confidences are legitimately numeric. If the money check caught those, it
        // would reject the most useful prose the model can write.
        val result = NarrationAudit.check(
            "Peeling paint covering roughly 1.2 sq ft was recorded on the hall wall, detected " +
                "at 71 percent confidence during the walkthrough.",
            listOf(finding())
        )
        assertEquals(NarrationAudit.Result.Accepted, result)
    }

    @Test
    fun emptyOrTrivialOutputIsRejected() {
        listOf("", "   ", "No issues.").forEach { text ->
            val reason = rejectionReason(NarrationAudit.check(text, listOf(finding())))
            assertTrue(reason, reason.contains("too short"))
        }
    }

    @Test
    fun aRunawayGenerationIsRejected() {
        val reason = rejectionReason(
            NarrationAudit.check("The hall was inspected. ".repeat(200), listOf(finding()))
        )
        assertTrue(reason, reason.contains("too long"))
    }

    @Test
    fun theTemplateNarratorsOwnOutputPassesItsOwnAudit() {
        // The fallback must never itself be rejectable: if it were, a rejection would have
        // nowhere to fall back to. This pins the two against each other.
        val findings = listOf(finding(), finding(), finding())
        val text = TemplateReportNarrator.compose(findings)
        assertEquals(NarrationAudit.Result.Accepted, NarrationAudit.check(text, findings))
    }

    @Test
    fun theTemplateNarratorMentionsAHazardWhenOneExists() {
        // Same pairing for the hazard rule: the template copies each finding's label verbatim,
        // so a STOP_ESCALATE label lands in the prose and satisfies the audit. If the template
        // ever starts summarising labels away, this fails before a report does.
        val findings = listOf(
            finding(label = "Hall -- Sides: exposed wire", severity = Severity.STOP_ESCALATE),
            finding()
        )
        val text = TemplateReportNarrator.compose(findings)
        assertEquals(NarrationAudit.Result.Accepted, NarrationAudit.check(text, findings))
    }

    private companion object {
        const val SESSION_ID = "11111111-1111-1111-1111-111111111111"
    }
}
