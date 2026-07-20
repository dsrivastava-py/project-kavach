package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from:
 *   POST /api/v1/deepcheck/sessions  (status=pending initially)
 *   GET  /api/v1/deepcheck/sessions/{id}  (status evolves to done)
 *
 * When status == "done", [spoofScore] is present with [assistiveOnly]=true.
 * HARD RULE: never interpret [spoofScore] as definitive — always show [spoofDisclaimer].
 */
@JsonClass(generateAdapter = true)
data class DeepCheckSessionDto(
    @Json(name = "session_id")       val sessionId: String,
    @Json(name = "status")           val status: String,            // pending | processing | done | error
    @Json(name = "transcript")       val transcript: String? = null,
    @Json(name = "red_flags")        val redFlags: List<String>? = null,
    @Json(name = "spoof_score")      val spoofScore: Double? = null,
    @Json(name = "assistive_only")   val assistiveOnly: Boolean? = null,
    @Json(name = "spoof_disclaimer") val spoofDisclaimer: String? = null,
    @Json(name = "summary")          val summary: String? = null,
    @Json(name = "confidence")       val confidence: Double? = null,
)
