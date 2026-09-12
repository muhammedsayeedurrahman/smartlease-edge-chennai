package com.smartlease.edge.sync

/**
 * Boundary validation for outgoing sync payloads -- run before anything touches the network,
 * so a bug upstream (a bad digest, a negative rupee figure) fails fast with a typed error
 * instead of round-tripping to the server to find out the same thing via a 422.
 *
 * Mirrors the 422 case exactly: whether the rejection happens here or on the server, the
 * caller gets back the same [SyncResult.ValidationRejected], just with a message that says
 * which check failed.
 */
private val DIGEST_PATTERN = Regex("^[0-9a-f]{64}$")

/** Null when [request] is well-formed; otherwise a human-readable reason it was rejected. */
fun ReportUploadRequest.validationError(): String? = when {
    reportId.isBlank() -> "reportId must not be blank"
    propertyRef.isBlank() -> "propertyRef must not be blank"
    appVersion.isBlank() -> "appVersion must not be blank"
    createdAtEpochMs <= 0 -> "createdAtEpochMs must be positive"
    !DIGEST_PATTERN.matches(digestSha256) -> "digestSha256 must be 64 lowercase hex characters"
    findingsCount < 0 -> "findingsCount must not be negative"
    depositRupees < 0 -> "depositRupees must not be negative"
    totalDeductionRupees < 0 -> "totalDeductionRupees must not be negative"
    totalDeductionRupees > depositRupees ->
        "totalDeductionRupees ($totalDeductionRupees) must not exceed depositRupees ($depositRupees)"
    else -> null
}

/** Null when [request] is well-formed; otherwise a human-readable reason it was rejected. */
fun CountersignRequest.validationError(): String? = when {
    !DIGEST_PATTERN.matches(signatureSha256) -> "signatureSha256 must be 64 lowercase hex characters"
    signedAtEpochMs <= 0 -> "signedAtEpochMs must be positive"
    else -> null
}

/** Null when [digestSha256] is a well-formed digest; otherwise a human-readable reason. */
fun validateDigestHex(digestSha256: String): String? =
    if (DIGEST_PATTERN.matches(digestSha256)) null else "digest must be 64 lowercase hex characters"
