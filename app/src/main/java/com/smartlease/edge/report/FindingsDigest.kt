package com.smartlease.edge.report

import com.smartlease.edge.data.InspectionEntity
import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 over the findings of one session, so a report can state what it covers.
 *
 * This is the whole of the "tamper-evident" claim and it is deliberately modest: the digest
 * proves that the findings printed on the page are byte-for-byte the ones that were in the
 * database when the report was rendered. It does not prove who recorded them, when, or that
 * the device clock was honest. Those need a signing key and a trusted time source, and
 * neither ships. Say that out loud rather than letting "SHA-256" imply more.
 *
 * Everything here is pure — no Android types — so the canonicalisation is unit-tested on the
 * JVM. That matters more than it looks: a digest is only useful if the same findings always
 * produce the same bytes, and the easy way to get that wrong is locale-dependent number
 * formatting or map iteration order.
 */
object FindingsDigest {

    /** Bumped if [canonicalize] ever changes shape, so old and new digests are never confused. */
    const val FORMAT_VERSION = "sl1"

    /**
     * One line per finding, fields **length-prefixed** as `<utf8ByteLength>:<value>`, in a
     * fixed order, sorted so that database insertion order cannot change the result:
     *
     *   `sl1\n36:<sessionId>\n13:<ts>13:VISUAL_DEFECT4:INFO5:crack2:{}\n...`
     *
     * Length-prefixing rather than a separator character on purpose. With a delimiter, a
     * label containing that delimiter can impersonate a field boundary — a finding labelled
     * `"crack<TAB>INFO"` and one labelled `"crack"` with detail `"INFO<TAB>{}"` produce the
     * same line and therefore the same digest, which is a forgery primitive, not a bug in
     * formatting. Escaping fixes it only if the escaping is exactly right. Lengths are
     * correct by construction, so the question does not arise.
     */
    fun canonicalize(sessionId: String, findings: List<InspectionEntity>): String {
        val rows = findings
            .map { f ->
                field(f.timestampEpochMillis.toString()) +
                        field(f.findingType.name) +
                        field(f.severity.name) +
                        field(f.label) +
                        field(f.detailJson)
            }
            .sorted()

        return buildString {
            append(FORMAT_VERSION).append('\n')
            append(field(sessionId)).append('\n')
            for (row in rows) append(row).append('\n')
        }
    }

    /** Lowercase hex SHA-256 of [canonicalize]. 64 characters. */
    fun sha256Hex(sessionId: String, findings: List<InspectionEntity>): String =
        hex(MessageDigest.getInstance("SHA-256")
            .digest(canonicalize(sessionId, findings).toByteArray(Charsets.UTF_8)))

    /** `A1B2 C3D4 …` — grouped for a human reading it off a page and comparing by eye. */
    fun grouped(hex: String, groups: Int = 8): String =
        hex.uppercase(Locale.ROOT).chunked(4).take(groups).joinToString(" ")

    /** `<utf8ByteLength>:<value>` — see [canonicalize] for why length and not a delimiter. */
    private fun field(value: String): String =
        value.toByteArray(Charsets.UTF_8).size.toString() + ":" + value

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}
