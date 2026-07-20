package com.kavach.app.utils

import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wraps a suspend Retrofit call in a [NetworkResult].
 *
 * Usage in repository:
 * ```kotlin
 * return safeApiCall { apiService.getPlans() }
 * ```
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): NetworkResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.Error("Empty response body", response.code())
            }
        } else {
            val errorMsg = when (response.code()) {
                400 -> "Bad request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Not found"
                409 -> "Conflict"
                413 -> "Payload too large"
                422 -> "Validation error"
                429 -> "Too many requests"
                500 -> "Server error"
                502 -> "Bad gateway"
                else -> "HTTP ${response.code()}"
            }
            NetworkResult.Error(errorMsg, response.code())
        }
    } catch (e: SocketTimeoutException) {
        NetworkResult.Error("Request timed out", throwable = e)
    } catch (e: UnknownHostException) {
        NetworkResult.Error("No internet connection", throwable = e)
    } catch (e: IOException) {
        NetworkResult.Error("Network error: ${e.message}", throwable = e)
    } catch (e: HttpException) {
        NetworkResult.Error("HTTP error: ${e.message}", e.code(), throwable = e)
    } catch (e: Exception) {
        NetworkResult.Error("Unexpected error: ${e.message}", throwable = e)
    }
}

/** Format a risk score (0.0–1.0) as a percentage string. */
fun Double.toRiskPercent(): String = "${(this * 100).toInt()}%"

/** Truncate a UUID string for display. */
fun String.shortId(): String = take(8) + "…"
