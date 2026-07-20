package com.kavach.app.domain.usecases.deepcheck

import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.domain.model.DeepCheckStatus
import com.kavach.app.domain.repository.DeepCheckRepository
import com.kavach.app.utils.Constants
import com.kavach.app.utils.NetworkResult
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

/**
 * Starts a deep-check session and polls until completion or timeout.
 *
 * Fire-and-poll flow:
 * 1. POST /deepcheck/sessions → session_id
 * 2. Poll GET /deepcheck/sessions/{id} every 3 seconds
 * 3. Return when status == "done" or "error", or after [Constants.DEEPCHECK_MAX_POLLS]
 *
 * The caller receives a [Flow] of intermediate [DeepCheckSession] states via
 * the ViewModel pattern — this use case emits each poll result.
 */
class StartDeepCheckUseCase @Inject constructor(
    private val deepCheckRepository: DeepCheckRepository,
) {
    /**
     * Launches the session. [onPoll] is invoked after each poll with the latest session state.
     * Returns the final [DeepCheckSession] when done, or an [NetworkResult.Error] on failure.
     */
    suspend operator fun invoke(
        audioFile: File,
        elderId: String,
        incidentId: String? = null,
        onPoll: (suspend (DeepCheckSession) -> Unit)? = null,
    ): NetworkResult<DeepCheckSession> {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return NetworkResult.Error("Audio file is empty or does not exist")
        }
        if (audioFile.length() > 25 * 1024 * 1024) {
            return NetworkResult.Error("Audio file exceeds 25 MB limit")
        }

        // Step 1: Create session
        val createResult = deepCheckRepository.createSession(audioFile, elderId, incidentId)
        if (createResult is NetworkResult.Error) return createResult
        if (createResult !is NetworkResult.Success) return NetworkResult.Error("Failed to start session")

        val sessionId = createResult.data.sessionId

        // Step 2: Poll until terminal state
        repeat(Constants.DEEPCHECK_MAX_POLLS) { attempt ->
            delay(Constants.DEEPCHECK_POLL_INTERVAL_MS)

            val pollResult = deepCheckRepository.pollSession(sessionId)
            if (pollResult is NetworkResult.Success) {
                val session = pollResult.data
                onPoll?.invoke(session)

                if (session.status.isTerminal) {
                    return NetworkResult.Success(session)
                }
            } else if (pollResult is NetworkResult.Error && attempt > 3) {
                // Allow a few transient errors before giving up
                return pollResult
            }
        }

        return NetworkResult.Error("Deep check timed out after ${Constants.DEEPCHECK_MAX_POLLS} polls")
    }
}
