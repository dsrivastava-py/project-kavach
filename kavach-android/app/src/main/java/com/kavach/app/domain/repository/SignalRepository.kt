package com.kavach.app.domain.repository

import com.kavach.app.domain.model.SignalEvent
import com.kavach.app.domain.model.SignalIngestResult
import com.kavach.app.utils.NetworkResult

/**
 * Signal ingestion repository interface.
 * Uses the device API key (X-API-Key), not Bearer JWT.
 */
interface SignalRepository {

    /** Enqueue an event locally (offline-safe). */
    suspend fun enqueueEvent(event: SignalEvent)

    /**
     * Upload pending events to the backend in a single batch call.
     * Returns the number of events ingested and the Celery task ID.
     * Deletes successfully uploaded events from the local queue.
     */
    suspend fun flushPendingEvents(
        deviceId: String,
        elderId: String,
    ): NetworkResult<SignalIngestResult>

    /** Number of events currently waiting in the local queue. */
    suspend fun pendingEventCount(): Int
}
