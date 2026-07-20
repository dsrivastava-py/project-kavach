package com.kavach.app.data.remote.interceptor

import com.kavach.app.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that attaches the Bearer JWT to every request.
 *
 * Uses [SessionManager] to read the stored token. Because OkHttp interceptors
 * are synchronous, we bridge to coroutines with [runBlocking] — acceptable here
 * because the read is a fast DataStore disk operation and happens once per request.
 *
 * On 401 from the server, [TokenExpiryInterceptor] (applied after this one)
 * handles the logout flow so this interceptor stays focused on attachment only.
 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionManager.getJwtTokenOnce() }

        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}
