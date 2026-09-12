package com.smartlease.edge.sync

import java.security.MessageDigest
import java.util.Base64

/**
 * SHA-256 over the raw bytes of a base64-encoded countersignature, lowercase hex. Exists so
 * [com.smartlease.edge.ui.screens.CountersignScreen] can populate
 * [CountersignRequest.signatureSha256] without ever putting the signature itself on the wire
 * -- this package's whole contract (see [ReportSyncClient]'s class doc) is that only digests
 * and small metadata leave the device, never the underlying bytes.
 *
 * Pure JVM (`java.util.Base64`, available since API 26 -- this app's minSdk -- so no
 * `android.util.Base64` dependency is needed here), which keeps it unit-testable without an
 * Android runtime.
 *
 * @throws IllegalArgumentException if [signatureBase64] is not valid base64. Callers scanning
 * a QR handshake payload must treat that input as untrusted and handle the throw.
 */
fun sha256HexOfBase64(signatureBase64: String): String {
    val bytes = Base64.getDecoder().decode(signatureBase64)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val out = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return out.toString()
}

private const val HEX = "0123456789abcdef"
