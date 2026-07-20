package com.kavach.app.data.remote.api

import com.kavach.app.data.remote.dto.SignalIngestRequest
import com.kavach.app.data.remote.dto.SignalIngestResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Separate Retrofit interface for signal ingestion.
 *
 * Signal ingestion uses a device API key (X-API-Key header), NOT a Bearer JWT.
 * This interface is backed by a separate OkHttp client configured with
 * [DeviceKeyInterceptor] instead of [AuthInterceptor].
 *
 * IMPORTANT: batch events — backend expects 1–100 events per call.
 * Do NOT call this per individual event.
 */
interface SignalApiService {

    @POST("api/v1/signals/ingest")
    suspend fun ingestSignals(
        @Body request: SignalIngestRequest,
    ): Response<SignalIngestResponse>
}
