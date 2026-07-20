package com.kavach.app.domain.model

/**
 * All event types the backend recognises for signal ingestion.
 * Mirrors the [signal_event_type] enum in the backend.
 */
enum class SignalEventType(val value: String) {
    CALL_START("call_start"),
    CALL_END("call_end"),
    VIDEO_CALL_START("video_call_start"),
    VIDEO_CALL_END("video_call_end"),
    SCREEN_SHARE_START("screen_share_start"),
    SCREEN_SHARE_END("screen_share_end"),
    FOREGROUND_APP("foreground_app"),
    UNKNOWN_NUMBER("unknown_number"),
    FIRST_TIME_PAYEE("first_time_payee"),
    BANKING_APP_OPENED("banking_app_opened");

    companion object {
        fun fromString(value: String): SignalEventType? =
            entries.firstOrNull { it.value == value }
    }
}

/**
 * A signal event ready to be enqueued locally before batch upload.
 */
data class SignalEvent(
    val deviceId: String,
    val elderId: String,
    val eventType: SignalEventType,
    val payload: Map<String, String> = emptyMap(),
    val occurredAt: String,       // ISO-8601
)

data class SignalIngestResult(
    val ingested: Int,
    val taskId: String,
)
