package com.smartlease.edge.deduction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FindingDetail] is the only bridge between what a subsystem measured and what
 * [DeductionEngine] can charge for, so a round trip through [FindingDetail.toJson] and
 * [FindingDetail.fromJson] has to be exact -- and anything that isn't a value this sealed
 * type produced must come back null, never a best guess.
 */
class FindingDetailTest {

    @Test
    fun `visual defect round-trips through json`() {
        val original = FindingDetail.VisualDefect(
            defectClass = "crack",
            areaSqFt = 1.25f,
            confidence = 0.83f,
            fromTrainedModel = true
        )

        val restored = FindingDetail.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `acoustic tap round-trips through json`() {
        val original = FindingDetail.AcousticTap(
            verdict = "hollow",
            confidence = 0.91f,
            fromTrainedModel = true
        )

        val restored = FindingDetail.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `appliance check round-trips through json`() {
        val original = FindingDetail.ApplianceCheck(appliance = "Voltas AC", functional = false)

        val restored = FindingDetail.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `appliance check with a failed transmit round-trips irTransmitted and the failure reason`() {
        val original = FindingDetail.ApplianceCheck(
            appliance = "Voltas AC",
            functional = false,
            irTransmitted = false,
            failureReason = "No ConsumerIrManager available on this device"
        )

        val restored = FindingDetail.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `a legacy failed appliance check payload without irTransmitted infers the command was never sent`() {
        // Rows persisted before irTransmitted existed had exactly two writers: Success (wrote
        // functional = true) and Failure (wrote functional = false). A legacy row with
        // functional = false can therefore only have come from the Failure branch, so the
        // command was never transmitted -- defaulting to true here would fabricate a
        // transmission that never happened and re-open the bogus "not functional" deduction.
        val restored = FindingDetail.fromJson(
            """{"kind":"appliance_check","appliance":"Voltas AC","functional":false}"""
        )

        assertEquals(
            FindingDetail.ApplianceCheck(appliance = "Voltas AC", functional = false, irTransmitted = false),
            restored
        )
    }

    @Test
    fun `a legacy successful appliance check payload without irTransmitted defaults to transmitted`() {
        // The only other legacy writer was the Success branch, which wrote functional = true
        // and always sent the command -- so a legacy row with functional = true still defaults
        // irTransmitted to true.
        val restored = FindingDetail.fromJson(
            """{"kind":"appliance_check","appliance":"Voltas AC","functional":true}"""
        )

        assertEquals(
            FindingDetail.ApplianceCheck(appliance = "Voltas AC", functional = true, irTransmitted = true),
            restored
        )
    }

    @Test
    fun `note round-trips through json`() {
        val original = FindingDetail.Note(text = "pitch 2, roll -1. Held in memory for this session only.")

        val restored = FindingDetail.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `an empty object is not a finding detail`() {
        assertNull(FindingDetail.fromJson("{}"))
    }

    @Test
    fun `malformed json returns null rather than throwing`() {
        assertNull(FindingDetail.fromJson("not json"))
    }

    @Test
    fun `an unknown kind returns null`() {
        assertNull(FindingDetail.fromJson("""{"kind":"something_new"}"""))
    }
}
