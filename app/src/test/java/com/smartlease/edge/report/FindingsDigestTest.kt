package com.smartlease.edge.report

import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The digest printed on every page of the report is only worth printing if it is stable
 * for the same findings and different for different ones. Both directions are checked here.
 *
 * The failure this guards against is not a crash — it is a digest that quietly changes for
 * reasons a reader cannot see (row order out of the database, a locale that formats numbers
 * differently, a tab inside a label colliding with the field separator). Any of those turns
 * "this report is unaltered" into a claim that cannot be relied on.
 */
class FindingsDigestTest {

    private fun finding(
        ts: Long = 1_757_000_000_000L,
        type: FindingType = FindingType.VISUAL_DEFECT,
        label: String = "crack in wall surface, ~0.75 sq ft (YOLOv8n-Seg, 88% confidence)",
        severity: Severity = Severity.INFO,
        detail: String = "{}"
    ) = InspectionEntity(
        sessionId = SESSION,
        timestampEpochMillis = ts,
        findingType = type,
        label = label,
        detailJson = detail,
        severity = severity
    )

    @Test
    fun `same findings produce the same digest`() {
        val a = listOf(finding(), finding(ts = 1_757_000_001_000L, type = FindingType.ACOUSTIC_TAP))
        val b = listOf(finding(), finding(ts = 1_757_000_001_000L, type = FindingType.ACOUSTIC_TAP))

        assertEquals(
            FindingsDigest.sha256Hex(SESSION, a),
            FindingsDigest.sha256Hex(SESSION, b)
        )
    }

    @Test
    fun `one changed character changes the digest`() {
        val before = listOf(finding(label = "crack in wall surface, ~0.75 sq ft"))
        val after = listOf(finding(label = "crack in wall surface, ~0.05 sq ft"))

        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, before),
            FindingsDigest.sha256Hex(SESSION, after)
        )
    }

    @Test
    fun `a changed severity changes the digest`() {
        val info = listOf(finding(severity = Severity.INFO))
        val escalated = listOf(finding(severity = Severity.STOP_ESCALATE))

        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, info),
            FindingsDigest.sha256Hex(SESSION, escalated)
        )
    }

    @Test
    fun `row order out of the database does not change the digest`() {
        val first = finding(ts = 1L, label = "first")
        val second = finding(ts = 2L, label = "second")

        assertEquals(
            FindingsDigest.sha256Hex(SESSION, listOf(first, second)),
            FindingsDigest.sha256Hex(SESSION, listOf(second, first))
        )
    }

    @Test
    fun `dropping a finding changes the digest`() {
        val both = listOf(finding(ts = 1L, label = "kept"), finding(ts = 2L, label = "omitted"))
        val one = listOf(finding(ts = 1L, label = "kept"))

        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, both),
            FindingsDigest.sha256Hex(SESSION, one)
        )
    }

    @Test
    fun `the session id is covered by the digest`() {
        val f = listOf(finding())
        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, f),
            FindingsDigest.sha256Hex("00000000-0000-4000-8000-000000000000", f)
        )
    }

    /**
     * A tab in a label must not be able to impersonate a field boundary. With tab-separated
     * fields these two produce the identical canonical line and therefore the identical
     * digest — a forgery primitive. Length-prefixing is what stops it.
     */
    @Test
    fun `a tab inside a label cannot forge a field boundary`() {
        val withTab = listOf(finding(label = "crack\tINFO", detail = "{}"))
        val plain = listOf(finding(label = "crack", detail = "INFO\t{}"))

        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, withTab),
            FindingsDigest.sha256Hex(SESSION, plain)
        )
    }

    /** With a delimiter instead of a length prefix, these two collide exactly. */
    @Test
    fun `shifting a character between adjacent fields cannot collide`() {
        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, listOf(finding(label = "ab", detail = "c"))),
            FindingsDigest.sha256Hex(SESSION, listOf(finding(label = "a", detail = "bc")))
        )
    }

    @Test
    fun `a changed finding type changes the digest`() {
        assertNotEquals(
            FindingsDigest.sha256Hex(SESSION, listOf(finding(type = FindingType.VISUAL_DEFECT))),
            FindingsDigest.sha256Hex(SESSION, listOf(finding(type = FindingType.ACOUSTIC_TAP)))
        )
    }

    @Test
    fun `digest is 64 lowercase hex characters`() {
        val hex = FindingsDigest.sha256Hex(SESSION, listOf(finding()))
        assertEquals(64, hex.length)
        assertTrue("not lowercase hex: $hex", hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun `an empty session still digests`() {
        val hex = FindingsDigest.sha256Hex(SESSION, emptyList())
        assertEquals(64, hex.length)
        assertNotEquals(hex, FindingsDigest.sha256Hex(SESSION, listOf(finding())))
    }

    @Test
    fun `canonical form is versioned and starts with the session id`() {
        val canon = FindingsDigest.canonicalize(SESSION, listOf(finding()))
        val lines = canon.lines()
        assertEquals(FindingsDigest.FORMAT_VERSION, lines[0])
        // Fields are length-prefixed: "36:<uuid>".
        assertEquals("${SESSION.toByteArray(Charsets.UTF_8).size}:$SESSION", lines[1])
    }

    @Test
    fun `grouped form is readable off a page`() {
        val hex = "0123456789abcdef".repeat(4)
        assertEquals("0123 4567 89AB CDEF 0123 4567 89AB CDEF", FindingsDigest.grouped(hex))
    }

    private companion object {
        const val SESSION = "3f2b9c11-7a4e-4f1d-9b23-58c0d7e14a6f"
    }
}
