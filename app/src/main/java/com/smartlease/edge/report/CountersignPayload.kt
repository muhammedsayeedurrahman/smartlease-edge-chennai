package com.smartlease.edge.report

import org.json.JSONObject

/**
 * Wire payload for the two-phone joint-inspection countersign handshake, carried as the raw
 * text of a QR code between two phones both running this app. Bare JSON, not a URL or a
 * custom scheme — same reasoning as [QrCode]'s own payload: a URL would imply a server that
 * does not exist here.
 *
 * The handshake has exactly two message shapes:
 *  1. [Request] — phone A (holding the report) asks for a signature over its findings digest.
 *  2. [Response] — phone B (the other party) signs that digest with its own device key and
 *     hands the signature and its certificate back.
 *
 * [parse] is the only way either phone reads an incoming payload, so a stray QR scanned by
 * accident (a product barcode, a URL) is rejected rather than partially interpreted.
 */
object CountersignPayload {

    private const val TYPE_REQUEST = "req"
    private const val TYPE_RESPONSE = "resp"

    data class Request(val sessionId: String, val digestHex: String) {
        fun toJson(): String = JSONObject()
            .put("t", TYPE_REQUEST)
            .put("s", sessionId)
            .put("d", digestHex)
            .toString()
    }

    data class Response(
        val sessionId: String,
        val digestHex: String,
        val signatureBase64: String,
        val certificateBase64: String,
        /** As claimed by the signing device itself -- see [Parsed.Resp.hardwareBackedSelfReported]. */
        val hardwareBackedSelfReported: Boolean
    ) {
        fun toJson(): String = JSONObject()
            .put("t", TYPE_RESPONSE)
            .put("s", sessionId)
            .put("d", digestHex)
            .put("sig", signatureBase64)
            .put("cert", certificateBase64)
            .put("hw", hardwareBackedSelfReported)
            .toString()
    }

    sealed interface Parsed {
        data class Req(val sessionId: String, val digestHex: String) : Parsed
        data class Resp(
            val sessionId: String,
            val digestHex: String,
            val signatureBase64: String,
            val certificateBase64: String,
            /**
             * Whatever the responding device's own [ReportSigner.Attestation.hardwareBacked]
             * said about itself at signing time -- carried over the wire as plain JSON, so it
             * is exactly as trustworthy as the device that sent it. This receiving side never
             * validates the certificate chain against a hardware attestation root, so this
             * field must not be presented as a verified fact; it is what the other phone claims.
             */
            val hardwareBackedSelfReported: Boolean
        ) : Parsed
    }

    /** Null for anything that is not a well-formed payload from this handshake. */
    fun parse(raw: String): Parsed? {
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return null
        }
        val sessionId = obj.optString("s").ifBlank { return null }
        val digestHex = obj.optString("d").ifBlank { return null }
        return when (obj.optString("t")) {
            TYPE_REQUEST -> Parsed.Req(sessionId, digestHex)
            TYPE_RESPONSE -> {
                val signatureBase64 = obj.optString("sig").ifBlank { return null }
                val certificateBase64 = obj.optString("cert").ifBlank { return null }
                Parsed.Resp(
                    sessionId, digestHex, signatureBase64, certificateBase64,
                    hardwareBackedSelfReported = obj.optBoolean("hw", false)
                )
            }
            else -> null
        }
    }
}
