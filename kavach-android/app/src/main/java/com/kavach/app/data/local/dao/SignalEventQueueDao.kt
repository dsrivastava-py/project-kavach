package com.kavach.app.data.local.dao

import androidx.room.*
import com.kavach.app.data.local.entity.SignalEventQueueEntity

/**
 * DAO for the local signal-event upload queue.
 * Write-heavy — inserts happen on every monitored device event.
 */
@Dao
interface SignalEventQueueDao {

    /** Enqueue a new event for later batch upload. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(event: SignalEventQueueEntity)

    /** Get up to [limit] pending (not yet uploaded) events. */
    @Query(
        "SELECT * FROM signal_event_queue WHERE uploaded = 0 " +
            "ORDER BY occurred_at ASC LIMIT :limit"
    )
    suspend fun getPending(limit: Int): List<SignalEventQueueEntity>

    /** Mark a list of local IDs as uploaded (soft-delete pattern for auditability). */
    @Query("UPDATE signal_event_queue SET uploaded = 1 WHERE local_id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    /** Hard-delete uploaded events to keep the queue lean. */
    @Query("DELETE FROM signal_event_queue WHERE uploaded = 1")
    suspend fun purgeUploaded()

    @Query("SELECT COUNT(*) FROM signal_event_queue WHERE uploaded = 0")
    suspend fun pendingCount(): Int
}
