package com.kavach.app.domain.repository

import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.utils.NetworkResult
import java.io.File

/**
 * Deep-check repository interface.
 * Fire-and-poll: create session → poll for completion.
 */
interface DeepCheckRepository {

    /**
     * Start a deep-check session by uploading an audio file.
     * Returns 202 Accepted immediately — poll [pollSession] for results.
     */
    suspend fun createSession(
        audioFile: File,
        elderId: String,
        incidentId: String? = null,
    ): NetworkResult<DeepCheckSession>

    /**
     * Poll the status of a deep-check session.
     * Returns full results when status == "done".
     */
    suspend fun pollSession(sessionId: String): NetworkResult<DeepCheckSession>
}
