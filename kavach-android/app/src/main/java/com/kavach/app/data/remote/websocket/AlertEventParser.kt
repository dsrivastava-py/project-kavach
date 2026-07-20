package com.kavach.app.data.remote.websocket

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses raw JSON frames from the guardian WebSocket stream.
 *
 * The backend pushes alert payloads whenever a risk evaluation graduates
 * an incident. Frame shape (from alert_dispatch.py):
 *
 * ```json
 * {
 *   "incident_id":  "uuid",
 *   "elder_id":     "uuid",
 *   "risk_score":   0.91,
 *   "status":       "graduated_2",
 *   "started_at":   "2024-01-01T10:00:00Z",
 *   "event_type":   "screen_share_start"
 * }
 * ```
 *
 * Heartbeat frames `{"type":"ping"}` are filtered out by [GuardianWebSocketClient]
 * before reaching this parser.
 */
@Singleton
class AlertEventParser @Inject constructor() {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AlertFrame::class.java).lenient()

    /**
     * Attempt to parse a raw JSON string into an [AlertFrame].
     * Returns null on any parse failure — callers should treat null as
     * an unknown/unstructured frame and display it raw.
     */
    fun parse(raw: String): AlertFrame? = runCatching { adapter.fromJson(raw) }.getOrNull()
}

/**
 * Typed representation of an alert frame pushed from the backend via Redis pub/sub.
 * All fields are nullable — the backend shape may evolve across phases.
 */
@JsonClass(generateAdapter = true)
data class AlertFrame(
    @Json(name = "incident_id") val incidentId: String?,
    @Json(name = "elder_id")    val elderId: String?,
    @Json(name = "risk_score")  val riskScore: Double?,
    @Json(name = "status")      val status: String?,
    @Json(name = "started_at")  val startedAt: String?,
    @Json(name = "event_type")  val eventType: String?,
    @Json(name = "type")        val frameType: String?,   // "ping" for heartbeats
)
