package com.smartlease.edge.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The whole point of validating outgoing sync payloads locally is to fail fast with a clear
 * reason instead of round-tripping to the server to learn the same thing via a 422. Every
 * check [ReportUploadRequest.validationError] can fail is exercised here, one at a time, so a
 * future edit that silently drops a check shows up as a missing failure rather than a
 * passing test.
 */
class ReportUploadValidationTest {

    private val validDigest = "a".repeat(64)

    private fun validRequest() = ReportUploadRequest(
        reportId = "report-1",
        propertyRef = "property-1",
        sessionType = SessionType.MOVE_IN,
        createdAtEpochMs = 1_757_000_000_000L,
        digestSha256 = validDigest,
        appVersion = "0.1.0-hackathon",
        findingsCount = 3,
        depositRupees = 20_000,
        totalDeductionRupees = 5_000
    )

    @Test
    fun `well-formed request passes validation`() {
        assertNull(validRequest().validationError())
    }

    @Test
    fun `blank reportId is rejected`() {
        assertEquals(
            "reportId must not be blank",
            validRequest().copy(reportId = " ").validationError()
        )
    }

    @Test
    fun `blank propertyRef is rejected`() {
        assertEquals(
            "propertyRef must not be blank",
            validRequest().copy(propertyRef = "").validationError()
        )
    }

    @Test
    fun `blank appVersion is rejected`() {
        assertEquals(
            "appVersion must not be blank",
            validRequest().copy(appVersion = "").validationError()
        )
    }

    @Test
    fun `non-positive createdAtEpochMs is rejected`() {
        assertEquals(
            "createdAtEpochMs must be positive",
            validRequest().copy(createdAtEpochMs = 0).validationError()
        )
    }

    @Test
    fun `uppercase digest is rejected`() {
        val error = validRequest().copy(digestSha256 = validDigest.uppercase()).validationError()
        assertEquals("digestSha256 must be 64 lowercase hex characters", error)
    }

    @Test
    fun `short digest is rejected`() {
        val error = validRequest().copy(digestSha256 = "abc123").validationError()
        assertEquals("digestSha256 must be 64 lowercase hex characters", error)
    }

    @Test
    fun `digest with non-hex characters is rejected`() {
        val error = validRequest().copy(digestSha256 = "g".repeat(64)).validationError()
        assertEquals("digestSha256 must be 64 lowercase hex characters", error)
    }

    @Test
    fun `negative findingsCount is rejected`() {
        assertEquals(
            "findingsCount must not be negative",
            validRequest().copy(findingsCount = -1).validationError()
        )
    }

    @Test
    fun `negative depositRupees is rejected`() {
        assertEquals(
            "depositRupees must not be negative",
            validRequest().copy(depositRupees = -1).validationError()
        )
    }

    @Test
    fun `negative totalDeductionRupees is rejected`() {
        assertEquals(
            "totalDeductionRupees must not be negative",
            validRequest().copy(totalDeductionRupees = -1).validationError()
        )
    }

    @Test
    fun `deduction exceeding deposit is rejected`() {
        val error = validRequest()
            .copy(depositRupees = 1_000, totalDeductionRupees = 1_001)
            .validationError()
        assertEquals("totalDeductionRupees (1001) must not exceed depositRupees (1000)", error)
    }

    @Test
    fun `deduction equal to deposit is allowed`() {
        val error = validRequest()
            .copy(depositRupees = 1_000, totalDeductionRupees = 1_000)
            .validationError()
        assertNull(error)
    }

    @Test
    fun `well-formed countersign request passes validation`() {
        val request = CountersignRequest(
            signerRole = SignerRole.TENANT,
            signatureSha256 = validDigest,
            signedAtEpochMs = 1_757_000_000_000L
        )
        assertNull(request.validationError())
    }

    @Test
    fun `countersign with malformed signature digest is rejected`() {
        val request = CountersignRequest(
            signerRole = SignerRole.TENANT,
            signatureSha256 = "not-hex",
            signedAtEpochMs = 1_757_000_000_000L
        )
        assertEquals("signatureSha256 must be 64 lowercase hex characters", request.validationError())
    }

    @Test
    fun `countersign with non-positive signedAtEpochMs is rejected`() {
        val request = CountersignRequest(
            signerRole = SignerRole.LANDLORD,
            signatureSha256 = validDigest,
            signedAtEpochMs = 0
        )
        assertEquals("signedAtEpochMs must be positive", request.validationError())
    }

    @Test
    fun `validateDigestHex accepts a well-formed digest`() {
        assertNull(validateDigestHex(validDigest))
    }

    @Test
    fun `validateDigestHex rejects a malformed digest`() {
        assertEquals(
            "digest must be 64 lowercase hex characters",
            validateDigestHex("short")
        )
    }
}
