package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.SignalEventQueueDao
import com.kavach.app.data.local.entity.SignalEventQueueEntity
import com.kavach.app.data.remote.api.SignalApiService
import com.kavach.app.data.remote.dto.SignalEventRequest
import com.kavach.app.data.remote.dto.SignalIngestRequest
import com.kavach.app.domain.model.SignalEvent
import com.kavach.app.domain.model.SignalIngestResult
import com.kavach.app.domain.repository.SignalRepository
import com.kavach.app.utils.Constants
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import javax.inject.Inject

/**
 * Signal repository — local-first with batch upload.
 *
 * 1. [enqueueEvent] writes to Room instantly (offline-safe).
 * 2. [flushPendingEvents] reads pending rows, POSTs to /signals/ingest,
 *    then marks rows uploaded and purges them from the queue.
 *
 * [SignalMonitorService] drives flush on a 30-second timer.
 */
class SignalRepositoryImpl @Inject constructor(
    private val api: SignalApiService,
    private val dao: SignalEventQueueDao,
) : SignalRepository {

    override suspend fun enqueueEvent(event: SignalEvent) {
        dao.enqueue(
            SignalEventQueueEntity(
                deviceId    = event.deviceId,
                elderId     = event.elderId,
                eventType   = event.eventType.value,
                payloadJson = event.payload.entries.joinToString(",") { "${it.key}:${it.value}" },
                occurredAt  = event.occurredAt,
            )
        )
    }

    override suspend fun flushPendingEvents(
        deviceId: String,
        elderId: String,
    ): NetworkResult<SignalIngestResult> {
        val pending = dao.getPending(Constants.SIGNAL_MAX_BATCH_SIZE)
        if (pending.isEmpty()) return NetworkResult.Success(SignalIngestResult(0, ""))

        val requestEvents = pending.map { entity ->
            SignalEventRequest(
                eventType   = entity.eventType,
                payload     = emptyMap(),
                occurredAt  = entity.occurredAt,
            )
        }

        val result = safeApiCall {
            api.ingestSignals(
                SignalIngestRequest(
                    deviceId = deviceId,
                    elderId  = elderId,
                    events   = requestEvents,
                )
            )
        }

        if (result is NetworkResult.Success) {
            dao.markUploaded(pending.map { it.localId })
            dao.purgeUploaded()
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                SignalIngestResult(result.data.ingested, result.data.taskId)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun pendingEventCount(): Int = dao.pendingCount()
}
