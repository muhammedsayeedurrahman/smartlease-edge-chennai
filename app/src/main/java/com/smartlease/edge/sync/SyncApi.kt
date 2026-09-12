package com.smartlease.edge.sync

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Raw Retrofit surface for the `/api/v1` backend contract. Deliberately not the public API of
 * this package -- [HttpReportSyncClient] is the only caller, and it is the one place that
 * turns Retrofit's [Response] (status code + body-or-error-body) into a [SyncResult]. Every
 * method returns [Response] rather than the unwrapped body so that 4xx/5xx responses come
 * back as data to inspect instead of a thrown `HttpException`.
 */
internal interface SyncApi {

    @GET("health")
    suspend fun health(): Response<HealthStatus>

    @POST("reports")
    suspend fun createReport(@Body body: ReportUploadRequest): Response<ReportUploadAck>

    // Response<ResponseBody> rather than a parsed type: the contract does not specify a
    // response schema for this endpoint (only the 201 status), and a converter that eagerly
    // tries to decode an empty or unspecified body would throw before HttpReportSyncClient
    // ever gets a chance to turn that into a typed SyncResult.
    // The per-report token goes on this call specifically: it is the endpoint that writes a
    // signature in a tenant's or landlord's name, so the shared API key alone must not be
    // enough to reach it. Retrofit omits a null @Header entirely, which is what makes the
    // "no token held" case arrive at the server as a clean 403 rather than a literal "null".
    @POST("reports/{reportId}/countersign")
    suspend fun countersign(
        @Path("reportId") reportId: String,
        @Header("X-Report-Token") reportToken: String?,
        @Body body: CountersignRequest
    ): Response<ResponseBody>

    @GET("reports/{reportId}/verify")
    suspend fun verify(
        @Path("reportId") reportId: String,
        @Query("digest") digest: String
    ): Response<VerifyOutcome>
}
