package com.kavach.app.data.remote.interceptor

import com.kavach.app.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the device API key as [X-API-Key] header for signal ingestion calls.
 * Used exclusively by the [SignalApiService] OkHttp client.
 */
class DeviceKeyInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = runBlocking { sessionManager.getDeviceApiKeyOnce() }

        val request = if (!apiKey.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("X-API-Key", apiKey)
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}
