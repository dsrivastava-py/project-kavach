package com.kavach.app.data.remote.interceptor

import com.kavach.app.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Handles 401 Unauthorized responses by clearing the session and emitting
 * an auto-logout signal that the UI observes via [SessionManager.isLoggedIn].
 *
 * The backend has no refresh-token endpoint. When the JWT expires (default
 * 60 minutes), this interceptor clears the stored JWT so the app redirects
 * to the pairing screen on the next composition cycle.
 */
class TokenExpiryInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            runBlocking { sessionManager.clearSession() }
            // Return response so callers still see the 401 code and can
            // show an appropriate message before navigation.
        }

        return response
    }
}
