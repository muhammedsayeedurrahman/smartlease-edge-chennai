package com.smartlease.edge.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [ReportSyncClient] backed by Retrofit/OkHttp. The only job of this class is translating
 * "what actually happened on the wire" into the closed [SyncResult] set -- no exception from
 * OkHttp, Retrofit, or kotlinx-serialization escapes a public method here.
 *
 * See [ReportSyncClient]'s doc for the privacy boundary this class inherits: it only ever
 * serializes [ReportUploadRequest] / [CountersignRequest], neither of which can carry media
 * bytes.
 */
internal class HttpReportSyncClient(
    private val api: SyncApi,
    private val json: Json,
    private val config: SyncConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReportSyncClient {

    override suspend fun checkHealth(): SyncResult<HealthStatus> =
        guarded {
            withContext(dispatcher) {
                toSyncResult(safeCall { api.health() }, "GET /health") { it }
            }
        }

    override suspend fun uploadReport(request: ReportUploadRequest): SyncResult<ReportUploadAck> {
        request.validationError()?.let { return SyncResult.ValidationRejected(it) }
        return guarded {
            withContext(dispatcher) {
                toSyncResult(safeCall { api.createReport(request) }, "POST /reports") { it }
            }
        }
    }

    override suspend fun countersign(
        reportId: String,
        request: CountersignRequest
    ): SyncResult<CountersignAck> {
        if (reportId.isBlank()) return SyncResult.ValidationRejected("reportId must not be blank")
        request.validationError()?.let { return SyncResult.ValidationRejected(it) }
        return guarded {
            withContext(dispatcher) {
                when (val outcome = safeCall { api.countersign(reportId, request) }) {
                    is CallOutcome.Failure -> outcome.result
                    is CallOutcome.Completed -> {
                        val response = outcome.response
                        if (response.isSuccessful) {
                            response.body()?.close()
                            SyncResult.Success(
                                CountersignAck(reportId, request.signerRole, request.signedAtEpochMs)
                            )
                        } else {
                            mapErrorResponse(response)
                        }
                    }
                }
            }
        }
    }

    override suspend fun verifyReport(
        reportId: String,
        digestSha256: String
    ): SyncResult<VerifyOutcome> {
        if (reportId.isBlank()) return SyncResult.ValidationRejected("reportId must not be blank")
        validateDigestHex(digestSha256)?.let { return SyncResult.ValidationRejected(it) }
        return guarded {
            withContext(dispatcher) {
                toSyncResult(safeCall { api.verify(reportId, digestSha256) }, "GET /reports/{id}/verify") { it }
            }
        }
    }

    /** Config-level kill switch: when sync is off, never touch the network at all. */
    private inline fun <T> guarded(block: () -> SyncResult<T>): SyncResult<T> =
        if (!config.syncEnabled) {
            SyncResult.NetworkUnavailable("Sync is disabled by configuration")
        } else {
            block()
        }

    private fun <T, R> toSyncResult(
        outcome: CallOutcome<T>,
        endpointLabel: String,
        onSuccess: (T) -> R
    ): SyncResult<R> = when (outcome) {
        is CallOutcome.Failure -> outcome.result
        is CallOutcome.Completed -> {
            val response = outcome.response
            if (!response.isSuccessful) {
                mapErrorResponse(response)
            } else {
                val body = response.body()
                if (body != null) SyncResult.Success(onSuccess(body))
                else SyncResult.MalformedResponse("Empty success body for $endpointLabel")
            }
        }
    }

    private fun mapErrorResponse(response: Response<*>): SyncResult<Nothing> {
        val message = errorMessage(response)
        return when (response.code()) {
            409 -> SyncResult.TamperConflict(message)
            422 -> SyncResult.ValidationRejected(message)
            else -> SyncResult.ServerError(response.code(), message)
        }
    }

    /** Never throws: an error body that is missing, empty, or not the documented shape still
     *  yields a usable message rather than a crash while reporting *another* error. */
    private fun errorMessage(response: Response<*>): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        if (raw.isNullOrBlank()) return "HTTP ${response.code()}"
        return try {
            json.decodeFromString(SyncErrorResponse.serializer(), raw).error.message
        } catch (e: SerializationException) {
            raw.take(MAX_RAW_ERROR_MESSAGE_LENGTH)
        } catch (e: IllegalArgumentException) {
            raw.take(MAX_RAW_ERROR_MESSAGE_LENGTH)
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): CallOutcome<T> =
        try {
            CallOutcome.Completed(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            CallOutcome.Failure(SyncResult.Timeout(e.message ?: "Request timed out"))
        } catch (e: IOException) {
            CallOutcome.Failure(SyncResult.NetworkUnavailable(e.message ?: "Network unavailable"))
        } catch (e: SerializationException) {
            CallOutcome.Failure(SyncResult.MalformedResponse(e.message ?: "Malformed response body"))
        } catch (e: IllegalArgumentException) {
            CallOutcome.Failure(SyncResult.MalformedResponse(e.message ?: "Malformed response body"))
        }

    private sealed interface CallOutcome<out T> {
        data class Completed<T>(val response: Response<T>) : CallOutcome<T>
        data class Failure(val result: SyncResult<Nothing>) : CallOutcome<Nothing>
    }

    private companion object {
        const val MAX_RAW_ERROR_MESSAGE_LENGTH = 200
    }
}
