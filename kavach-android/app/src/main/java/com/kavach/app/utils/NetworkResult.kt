package com.kavach.app.utils

/**
 * Sealed wrapper for all network/repo operations.
 *
 * Every repository function returns Flow<NetworkResult<T>> or
 * suspend fun … : NetworkResult<T>.
 *
 * ViewModels never touch raw exceptions — they handle [Error] only.
 */
sealed class NetworkResult<out T> {

    /** Call is in flight. */
    data object Loading : NetworkResult<Nothing>()

    /** Successful response with data. */
    data class Success<T>(val data: T) : NetworkResult<T>()

    /** Any failure — network, HTTP error, or unexpected exception. */
    data class Error(
        val message: String,
        val code: Int? = null,
        val throwable: Throwable? = null,
    ) : NetworkResult<Nothing>()
}

/** Convenience: returns true only for [NetworkResult.Success]. */
val <T> NetworkResult<T>.isSuccess: Boolean get() = this is NetworkResult.Success

/** Convenience: returns data or null. */
val <T> NetworkResult<T>.dataOrNull: T? get() = (this as? NetworkResult.Success)?.data
