package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Request DTOs ──────────────────────────────────────────────

/**
 * Single signal event sent in a batch to POST /api/v1/signals/ingest.
 */
@JsonClass(generateAdapter = true)
data class SignalEventRequest(
    @Json(name = "event_type")   val eventType: String,
    @Json(name = "payload")      val payload: Map<String, String> = emptyMap(),
    @Json(name = "occurred_at")  val occurredAt: String,         // ISO-8601
)

/**
 * Body for POST /api/v1/signals/ingest.
 * Backend expects batches of up to 100 events — never send one event per call.
 */
@JsonClass(generateAdapter = true)
data class SignalIngestRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "elder_id")  val elderId: String,
    @Json(name = "events")    val events: List<SignalEventRequest>,
)

// ── Response DTOs ─────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SignalIngestResponse(
    @Json(name = "ingested") val ingested: Int,
    @Json(name = "task_id")  val taskId: String,
)
