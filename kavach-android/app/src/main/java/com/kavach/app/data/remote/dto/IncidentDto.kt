package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Request DTOs ──────────────────────────────────────────────

/**
 * POST /api/v1/incidents/{incident_id}/resolve
 * resolution: "resolved" | "false_positive"
 */
@JsonClass(generateAdapter = true)
data class ResolveIncidentRequest(
    @Json(name = "resolution") val resolution: String,
    @Json(name = "note")       val note: String? = null,
)

// ── Response DTOs ─────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ResolveIncidentResponse(
    @Json(name = "incident_id") val incidentId: String,
    @Json(name = "status")      val status: String,
    @Json(name = "resolved_at") val resolvedAt: String,
)

@JsonClass(generateAdapter = true)
data class EvidenceResponse(
    @Json(name = "incident_id")  val incidentId: String,
    @Json(name = "pdf_ref")      val pdfRef: String,
    @Json(name = "download_url") val downloadUrl: String?,
)
