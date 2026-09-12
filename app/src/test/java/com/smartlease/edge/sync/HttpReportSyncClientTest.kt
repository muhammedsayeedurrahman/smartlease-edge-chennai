package com.smartlease.edge.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [HttpReportSyncClient] against a real OkHttp [MockWebServer] -- pure JVM, no
 * device or emulator needed. Covers every [SyncResult] branch the client can produce: the
 * happy path, each documented HTTP error, a body that does not parse, and the two failure
 * modes that never reach the server at all (local validation, and the sync-disabled switch).
 */
class HttpReportSyncClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        syncEnabled: Boolean = true,
        readTimeoutMs: Long = SyncConfig.DEFAULT_READ_TIMEOUT_MS,
        apiKey: String? = TEST_API_KEY
    ): ReportSyncClient = SyncClientFactory.create(
        SyncConfig(
            baseUrl = server.url("/api/v1/").toString(),
            connectTimeoutMs = 2_000,
            readTimeoutMs = readTimeoutMs,
            writeTimeoutMs = 2_000,
            syncEnabled = syncEnabled,
            apiKey = apiKey
        )
    )

    private val validDigest = "a".repeat(64)
    private val reportToken = "token-issued-when-this-report-was-created"

    private companion object {
        const val TEST_API_KEY = "test-api-key"
    }

    private fun uploadRequest() = ReportUploadRequest(
        reportId = "11111111-1111-1111-1111-111111111111",
        propertyRef = "flat-12b",
        sessionType = SessionType.MOVE_OUT,
        createdAtEpochMs = 1_757_000_000_000L,
        digestSha256 = validDigest,
        appVersion = "0.1.0-hackathon",
        findingsCount = 4,
        depositRupees = 20_000,
        totalDeductionRupees = 3_500
    )

    // -- checkHealth --------------------------------------------------------------------

    @Test
    fun `checkHealth returns Success for a 200 with a well-formed body`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"status":"ok","version":"1.2.3"}""")
        )

        val result = client().checkHealth()

        assertEquals(SyncResult.Success(HealthStatus("ok", "1.2.3")), result)
    }

    @Test
    fun `checkHealth returns MalformedResponse for unparsable JSON`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = client().checkHealth()

        assertTrue(result is SyncResult.MalformedResponse)
    }

    // -- uploadReport: success ------------------------------------------------------------

    @Test
    fun `uploadReport returns Success on 201 Created`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"reportId":"${uploadRequest().reportId}","digestSha256":"$validDigest","serverReceivedAtEpochMs":1757000005000}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertEquals(
            SyncResult.Success(
                ReportUploadAck(uploadRequest().reportId, validDigest, 1_757_000_005_000L)
            ),
            result
        )
    }

    @Test
    fun `uploadReport returns Success on 200 for an idempotent re-upload`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"reportId":"${uploadRequest().reportId}","digestSha256":"$validDigest","serverReceivedAtEpochMs":1757000005000}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `uploadReport sends only the documented fields, never media bytes`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"reportId":"x","digestSha256":"$validDigest","serverReceivedAtEpochMs":1}"""
            )
        )

        client().uploadReport(uploadRequest())

        val sentBody = server.takeRequest().body.readUtf8()
        val expectedKeys = setOf(
            "reportId", "propertyRef", "sessionType", "createdAtEpochMs",
            "digestSha256", "appVersion", "findingsCount", "depositRupees", "totalDeductionRupees"
        )
        for (key in expectedKeys) assertTrue("missing key $key in $sentBody", sentBody.contains("\"$key\""))
        // No field name in this DTO could ever hold image/audio/video bytes -- there is no
        // such field to send, which is the point: it is not merely omitted at call time.
        assertFalse(sentBody.contains("image"))
        assertFalse(sentBody.contains("bitmap"))
        assertFalse(sentBody.contains("audio"))
        assertFalse(sentBody.contains("video"))
    }

    // -- uploadReport: server-side rejections ----------------------------------------------

    @Test
    fun `uploadReport returns TamperConflict on 409`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"DIGEST_MISMATCH","message":"reportId already exists with a different digest"}}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertEquals(
            SyncResult.TamperConflict("reportId already exists with a different digest"),
            result
        )
    }

    @Test
    fun `uploadReport returns ValidationRejected on 422`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"error":{"code":"INVALID_FIELD","message":"propertyRef is required"}}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertEquals(SyncResult.ValidationRejected("propertyRef is required"), result)
    }

    @Test
    fun `uploadReport returns ServerError on 500`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error":{"code":"INTERNAL","message":"database unavailable"}}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertEquals(SyncResult.ServerError(500, "database unavailable"), result)
    }

    @Test
    fun `uploadReport returns ServerError with a fallback message when the error body is not JSON`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream timeout"))

        val result = client().uploadReport(uploadRequest())

        assertEquals(SyncResult.ServerError(503, "upstream timeout"), result)
    }

    @Test
    fun `uploadReport returns MalformedResponse for a 201 with unparsable JSON`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{not json"))

        val result = client().uploadReport(uploadRequest())

        assertTrue(result is SyncResult.MalformedResponse)
    }

    // -- uploadReport: local (outgoing) validation, never reaches the network -------------

    @Test
    fun `uploadReport rejects an invalid request before making any network call`() = runBlocking {
        val invalid = uploadRequest().copy(digestSha256 = "not-a-digest")

        val result = client().uploadReport(invalid)

        assertEquals(
            SyncResult.ValidationRejected("digestSha256 must be 64 lowercase hex characters"),
            result
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `uploadReport rejects deduction exceeding deposit before making any network call`() = runBlocking {
        val invalid = uploadRequest().copy(depositRupees = 1_000, totalDeductionRupees = 5_000)

        val result = client().uploadReport(invalid)

        assertTrue(result is SyncResult.ValidationRejected)
        assertEquals(0, server.requestCount)
    }

    // -- uploadReport: sync disabled --------------------------------------------------------

    @Test
    fun `uploadReport never calls the network when sync is disabled`() = runBlocking {
        val result = client(syncEnabled = false).uploadReport(uploadRequest())

        assertTrue(result is SyncResult.NetworkUnavailable)
        assertEquals(0, server.requestCount)
    }

    // -- uploadReport: network-level failures ----------------------------------------------

    @Test
    fun `uploadReport returns Timeout when the server never responds`() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val result = client(readTimeoutMs = 300).uploadReport(uploadRequest())

        assertTrue(result is SyncResult.Timeout)
    }

    @Test
    fun `uploadReport returns NetworkUnavailable when the server is unreachable`() = runBlocking {
        val unreachableConfig = SyncConfig(
            baseUrl = server.url("/api/v1/").toString(),
            connectTimeoutMs = 500,
            readTimeoutMs = 500,
            writeTimeoutMs = 500,
            // Explicit: this test wants a real connection attempt against a server that has
            // just been shut down, not the disabled-config short-circuit (syncEnabled now
            // defaults to false, which would otherwise return the same SyncResult type for
            // the wrong reason).
            syncEnabled = true
        )
        server.shutdown()

        val result = SyncClientFactory.create(unreachableConfig).uploadReport(uploadRequest())

        assertTrue(result is SyncResult.NetworkUnavailable)
    }

    // -- countersign --------------------------------------------------------------------

    @Test
    fun `countersign returns Success on 201`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val request = CountersignRequest(SignerRole.TENANT, validDigest, 1_757_000_006_000L)

        val result = client().countersign("report-1", reportToken, request)

        assertEquals(
            SyncResult.Success(CountersignAck("report-1", SignerRole.TENANT, 1_757_000_006_000L)),
            result
        )
    }

    @Test
    fun `countersign returns TamperConflict on 409 when the role already signed`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"ALREADY_SIGNED","message":"TENANT already countersigned this report"}}"""
            )
        )
        val request = CountersignRequest(SignerRole.TENANT, validDigest, 1_757_000_006_000L)

        val result = client().countersign("report-1", reportToken, request)

        assertEquals(
            SyncResult.TamperConflict("TENANT already countersigned this report"),
            result
        )
    }

    @Test
    fun `countersign rejects a malformed signature before making any network call`() = runBlocking {
        val request = CountersignRequest(SignerRole.LANDLORD, "bad-signature", 1_757_000_006_000L)

        val result = client().countersign("report-1", reportToken, request)

        assertTrue(result is SyncResult.ValidationRejected)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `countersign rejects a blank reportId before making any network call`() = runBlocking {
        val request = CountersignRequest(SignerRole.LANDLORD, validDigest, 1_757_000_006_000L)

        val result = client().countersign("  ", reportToken, request)

        assertEquals(SyncResult.ValidationRejected("reportId must not be blank"), result)
        assertEquals(0, server.requestCount)
    }

    // -- authentication and per-report authorization ---------------------------------------
    //
    // These exist because the first cut of this integration had neither. The server would
    // accept a countersignature -- the artefact the whole tamper-evidence story rests on --
    // from anyone who could reach it. Each test below pins one half of the fix in place on
    // the client side; server/tests/test_auth.py pins the other half.

    @Test
    fun `every request carries the configured API key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","serverVersion":"0.1.0"}"""))

        client().checkHealth()

        assertEquals(TEST_API_KEY, server.takeRequest().getHeader("X-API-Key"))
    }

    @Test
    fun `a build with no API key sends no key header rather than an empty one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        client(apiKey = null).checkHealth()

        // An empty header would read on the server as "wrong key" instead of "no key at all",
        // which is a materially different thing to see in a log while debugging a demo.
        assertNull(server.takeRequest().getHeader("X-API-Key"))
    }

    @Test
    fun `countersign sends the per-report token as its own header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val request = CountersignRequest(SignerRole.TENANT, validDigest, 1_757_000_006_000L)

        client().countersign("report-1", reportToken, request)

        val recorded = server.takeRequest()
        assertEquals(reportToken, recorded.getHeader("X-Report-Token"))
        assertEquals(TEST_API_KEY, recorded.getHeader("X-API-Key"))
    }

    @Test
    fun `countersign with no token held omits the header instead of sending null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val request = CountersignRequest(SignerRole.TENANT, validDigest, 1_757_000_006_000L)

        client().countersign("report-1", null, request)

        assertNull(server.takeRequest().getHeader("X-Report-Token"))
    }

    @Test
    fun `a 401 is Unauthenticated, not a generic ServerError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"unauthorized","message":"Missing or invalid X-API-Key header."}}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        // Retrying will never fix this: the build itself has the wrong credential.
        assertEquals(SyncResult.Unauthenticated("Missing or invalid X-API-Key header."), result)
    }

    @Test
    fun `a 403 is NotAuthorized, not a generic ServerError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"code":"forbidden","message":"Missing or invalid X-Report-Token for this report."}}"""
            )
        )
        val request = CountersignRequest(SignerRole.TENANT, validDigest, 1_757_000_006_000L)

        val result = client().countersign("report-1", null, request)

        assertEquals(
            SyncResult.NotAuthorized("Missing or invalid X-Report-Token for this report."),
            result
        )
    }

    @Test
    fun `uploadReport captures the one-time report token from a 201`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"reportId":"report-1","digestSha256":"$validDigest","serverReceivedAtEpochMs":1757000001000,"reportToken":"$reportToken"}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        // Losing this would mean losing the ability to countersign the report server-side.
        assertEquals(reportToken, (result as SyncResult.Success).value.reportToken)
    }

    @Test
    fun `uploadReport tolerates a response with no token`() = runBlocking {
        // What a 200 idempotent re-upload looks like: the token was issued the first time and
        // the server keeps only its hash, so it cannot hand one back again.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"reportId":"report-1","digestSha256":"$validDigest","serverReceivedAtEpochMs":1757000001000}"""
            )
        )

        val result = client().uploadReport(uploadRequest())

        assertNull((result as SyncResult.Success).value.reportToken)
    }

    // -- verifyReport ---------------------------------------------------------------------

    @Test
    fun `verifyReport returns Success with matches true`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"reportId":"report-1","matches":true}"""))

        val result = client().verifyReport("report-1", validDigest)

        assertEquals(SyncResult.Success(VerifyOutcome("report-1", true)), result)
    }

    @Test
    fun `verifyReport returns Success with matches false`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"reportId":"report-1","matches":false}"""))

        val result = client().verifyReport("report-1", validDigest)

        assertEquals(SyncResult.Success(VerifyOutcome("report-1", false)), result)
    }

    @Test
    fun `verifyReport rejects a malformed digest before making any network call`() = runBlocking {
        val result = client().verifyReport("report-1", "too-short")

        assertTrue(result is SyncResult.ValidationRejected)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `verifyReport propagates a 404 as ServerError`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":"NOT_FOUND","message":"no such report"}}"""
            )
        )

        val result = client().verifyReport("missing-report", validDigest)

        assertEquals(SyncResult.ServerError(404, "no such report"), result)
    }
}
