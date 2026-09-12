package com.smartlease.edge.sync

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds a real, network-backed [ReportSyncClient] from a [SyncConfig]. The only place in
 * this package that constructs Retrofit/OkHttp -- everything else depends on the
 * [ReportSyncClient] interface, so tests fake that interface instead of standing up HTTP
 * infrastructure (except [HttpReportSyncClient]'s own tests, which point this stack at an
 * OkHttp `MockWebServer`).
 */
object SyncClientFactory {

    /**
     * Lenient on the way in (an unexpected extra field from the server must not crash the
     * app -- "never trust the server's response shape"), strict on required fields (a missing
     * one surfaces as [SyncResult.MalformedResponse], not a silently-null value).
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun create(config: SyncConfig): ReportSyncClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return HttpReportSyncClient(retrofit.create(SyncApi::class.java), json, config)
    }
}
