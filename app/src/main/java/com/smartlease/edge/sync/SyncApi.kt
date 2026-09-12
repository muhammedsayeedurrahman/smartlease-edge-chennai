package com.smartlease.edge.sync

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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
    @POST("reports/{reportId}/countersign")
    suspend fun countersign(
        @Path("reportId") reportId: String,
        @Body body: CountersignRequest
    ): Response<ResponseBody>

    @GET("reports/{reportId}/verify")
    suspend fun verify(
        @Path("reportId") reportId: String,
        @Query("digest") digest: String
    ): Response<VerifyOutcome>
}
