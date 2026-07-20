package com.kavach.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local queue for signal events pending upload to the backend.
 *
 * Events are written here first (offline-safe), then batch-uploaded
 * every [Constants.SIGNAL_BATCH_INTERVAL_MS] ms by [SignalMonitorService].
 * Successfully ingested rows are deleted immediately after the API call.
 */
@Entity(
    tableName = "signal_event_queue",
    indices = [Index(value = ["uploaded"]), Index(value = ["occurred_at"])],
)
data class SignalEventQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "elder_id")
    val elderId: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** JSON-encoded Map<String,String> payload. */
    @ColumnInfo(name = "payload_json")
    val payloadJson: String = "{}",

    @ColumnInfo(name = "occurred_at")
    val occurredAt: String,       // ISO-8601

    @ColumnInfo(name = "uploaded")
    val uploaded: Boolean = false,
)
