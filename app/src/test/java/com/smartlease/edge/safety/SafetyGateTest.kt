package com.smartlease.edge.safety

import com.smartlease.edge.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is pitched as the one piece of architectural novelty in this project — a
 * deterministic, non-ML veto that no model output can talk its way past — and until now it
 * had no tests at all. These are the cases that matter: that every declared hazard keyword
 * actually fires, that matching is case- and position-insensitive because real labels are
 * sentences, and that an ordinary defect label is *not* escalated, which is the direction
 * that would quietly turn every finding into an alarm.
 */
class SafetyGateTest {

    @Test
    fun `every declared hazard keyword escalates`() {
        val hazards = listOf(
            "exposed wire", "exposed wiring", "bare wire",
            "water leak", "active leak", "flooding",
            "gas smell", "burning smell", "smoke"
        )
        for (h in hazards) {
            val v = SafetyGate.evaluate(h)
            assertEquals("'$h' should escalate", Severity.STOP_ESCALATE, v.escalatedSeverity)
            assertEquals("'$h' should not be allowed as-is", false, v.allowAsIs)
            assertNotNull("'$h' should carry a reason", v.reason)
        }
    }

    @Test
    fun `matching is case insensitive`() {
        val v = SafetyGate.evaluate("EXPOSED WIRE near the meter board")
        assertEquals(Severity.STOP_ESCALATE, v.escalatedSeverity)
    }

    @Test
    fun `a keyword mid-sentence still fires`() {
        val v = SafetyGate.evaluate("Tenant reports a burning smell from the geyser switch")
        assertEquals(Severity.STOP_ESCALATE, v.escalatedSeverity)
    }

    @Test
    fun `an ordinary defect label is not escalated`() {
        val v = SafetyGate.evaluate("crack in wall surface, ~0.75 sq ft (YOLOv8n-Seg, 88% confidence)")
        assertTrue(v.allowAsIs)
        assertNull(v.escalatedSeverity)
        assertNull(v.reason)
    }

    @Test
    fun `an acoustic verdict is not escalated`() {
        val v = SafetyGate.evaluate("LIKELY_HOLLOW (Hollow at 91% confidence)")
        assertTrue(v.allowAsIs)
        assertNull(v.escalatedSeverity)
    }

    @Test
    fun `an empty label is not escalated`() {
        val v = SafetyGate.evaluate("")
        assertTrue(v.allowAsIs)
        assertNull(v.escalatedSeverity)
    }

    @Test
    fun `the reason names the keyword that matched, so a user can see why`() {
        val v = SafetyGate.evaluate("OCR: DANGER exposed wiring behind panel")
        assertTrue(
            "reason should quote the keyword, was: ${v.reason}",
            v.reason?.contains("exposed wiring") == true
        )
    }

    /**
     * The gate may only ever escalate. If a future edit lets it *lower* a severity, a model
     * could suppress a hazard — which is precisely the property the class doc promises it
     * does not have.
     */
    @Test
    fun `the gate never returns a severity below STOP_ESCALATE`() {
        val labels = listOf(
            "crack in wall surface", "peeling paint", "staining or mould growth",
            "exposed wire", "water leak", "", "smoke"
        )
        for (l in labels) {
            val s = SafetyGate.evaluate(l).escalatedSeverity
            assertTrue(
                "'$l' produced $s",
                s == null || s == Severity.STOP_ESCALATE
            )
        }
    }
}
