package com.smartlease.edge.narration

import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtReportNarratorTest {

    private fun sampleFinding(
        label: String = "Wall hairline crack",
        severity: Severity = Severity.INFO
    ) = InspectionEntity(
        sessionId = "session-test",
        timestampEpochMillis = 1757000000000L,
        findingType = FindingType.VISUAL_DEFECT,
        label = label,
        detailJson = "{}",
        severity = severity
    )

    @Test
    fun `valid audited narrative is attributed to LITERT_GEMMA source`() = runBlocking {
        val findings = listOf(
            sampleFinding("Minor paint peeling on bedroom wall"),
            sampleFinding("Hairline crack near living room window")
        )

        val generatedText = "During the inspection, 2 findings were noted regarding minor paint peeling on bedroom wall and a hairline crack near the living room window."

        val narrator = LiteRtReportNarrator.createForTesting("gemma-4-E4B-it.litertlm") {
            generatedText
        }

        val result = narrator.narrate("Living Room", findings)

        assertEquals(NarrationSource.LITERT_GEMMA, result.source)
        assertEquals(generatedText, result.text)
    }

    @Test
    fun `narrative with money figure is rejected and falls back to template`() = runBlocking {
        val findings = listOf(sampleFinding("Damaged electrical switchboard"))

        // LLM generates unauthorized repair estimate in rupees
        val generatedText = "The inspection recorded 1 finding with damaged electrical switchboard requiring repair estimated at ₹1,500 rupees."

        val narrator = LiteRtReportNarrator.createForTesting("gemma-4-E4B-it.litertlm") {
            generatedText
        }

        val result = narrator.narrate("Living Room", findings)

        assertEquals(NarrationSource.TEMPLATE, result.source)
        assertTrue(result.note?.contains("money figure") == true)
        // Ensure template text is emitted
        assertTrue(result.text.contains("Damaged electrical switchboard"))
    }

    @Test
    fun `narrative inventing hazard not in findings is rejected`() = runBlocking {
        val findings = listOf(sampleFinding("Minor scuff mark on door", Severity.INFO))

        // LLM hallucinates severe hazard / structural failure
        val generatedText = "1 finding recorded. Exposed wire hazard requires immediate stop escalate."

        val narrator = LiteRtReportNarrator.createForTesting("gemma-4-E4B-it.litertlm") {
            generatedText
        }

        val result = narrator.narrate("Entryway", findings)

        assertEquals(NarrationSource.TEMPLATE, result.source)
        assertTrue(result.note?.contains("hazard") == true)
    }

    @Test
    fun `engine exception falls back cleanly to template with failure note`() = runBlocking {
        val findings = listOf(sampleFinding("Loose door handle"))

        val narrator = LiteRtReportNarrator.createForTesting("gemma-4-E4B-it.litertlm") {
            throw RuntimeException("Engine OOM or model initialization fault")
        }

        val result = narrator.narrate("Bedroom", findings)

        assertEquals(NarrationSource.TEMPLATE, result.source)
        assertTrue(result.note?.contains("generation failed") == true)
        assertTrue(result.text.contains("Loose door handle"))
    }
}
