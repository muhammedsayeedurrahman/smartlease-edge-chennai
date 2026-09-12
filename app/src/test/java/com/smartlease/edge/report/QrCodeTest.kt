package com.smartlease.edge.report

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encode, then **decode**, then compare.
 *
 * Eyeballing a QR proves nothing: a symbol with a wrong mask or wrong format bits still
 * looks like a QR and simply fails to scan — which would be discovered by a judge holding
 * their phone, not by us. Round-tripping through zxing's own reader is the closest thing to
 * "a camera app read it" that runs without a device, so every claim this feature makes is
 * checked here rather than assumed.
 */
class QrCodeTest {

    /** Minimal LuminanceSource over a BitMatrix — the JVM half of zxing has no Android deps. */
    private class MatrixLuminanceSource(private val m: BitMatrix) :
        LuminanceSource(m.width, m.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            for (x in 0 until width) out[x] = if (m.get(x, y)) 0 else 0xFF.toByte()
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                val off = y * width
                for (x in 0 until width) out[off + x] = if (m.get(x, y)) 0 else 0xFF.toByte()
            }
            return out
        }
    }

    private fun decode(text: String, size: Int = 512): String {
        val bmp = BinaryBitmap(HybridBinarizer(MatrixLuminanceSource(QrCode.matrix(text, size))))
        val hints = mapOf(DecodeHintType.PURE_BARCODE to true)
        return QRCodeReader().decode(bmp, hints).text
    }

    private fun finding(label: String) = InspectionEntity(
        sessionId = SESSION,
        timestampEpochMillis = 1_757_000_000_000L,
        findingType = FindingType.VISUAL_DEFECT,
        label = label,
        detailJson = "{}",
        severity = Severity.INFO
    )

    @Test
    fun `a real findings digest survives encode and decode`() {
        val digest = FindingsDigest.sha256Hex(SESSION, listOf(finding("crack in wall surface")))
        assertEquals(digest, decode(digest))
    }

    @Test
    fun `the payload is the bare digest, not a url or a scheme`() {
        val digest = FindingsDigest.sha256Hex(SESSION, listOf(finding("crack")))
        val scanned = decode(digest)
        assertTrue("scanned text should be 64 hex chars, was: $scanned", scanned.length == 64)
        assertTrue("must not carry a scheme", !scanned.contains("://"))
        assertTrue("must be lowercase hex", scanned.all { it in "0123456789abcdef" })
    }

    /**
     * The demo beat: judge scans, one finding is edited, report is regenerated, judge scans
     * again and reads a different code. If these two ever matched, the demo would be a lie.
     */
    @Test
    fun `editing one finding changes the scanned code`() {
        val before = FindingsDigest.sha256Hex(SESSION, listOf(finding("crack, ~0.75 sq ft")))
        val after = FindingsDigest.sha256Hex(SESSION, listOf(finding("crack, ~0.05 sq ft")))
        assertNotEquals(before, after)
        assertNotEquals(decode(before), decode(after))
        assertEquals(before, decode(before))
        assertEquals(after, decode(after))
    }

    @Test
    fun `the empty-session digest also encodes`() {
        val digest = FindingsDigest.sha256Hex(SESSION, emptyList())
        assertEquals(digest, decode(digest))
    }

    @Test
    fun `symbol is square and big enough to scan off a phone screen`() {
        val m = QrCode.matrix(FindingsDigest.sha256Hex(SESSION, listOf(finding("x"))), 512)
        assertEquals(m.width, m.height)
        assertTrue("unexpectedly small: ${m.width}", m.width >= 21)
    }

    private companion object {
        const val SESSION = "3f2b9c11-7a4e-4f1d-9b23-58c0d7e14a6f"
    }
}
