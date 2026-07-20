package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── Request DTOs ──────────────────────────────────────────────

/**
 * POST /api/v1/guardians/pair
 * No JWT required — uses short-lived Redis pairing code.
 */
@JsonClass(generateAdapter = true)
data class PairGuardianRequest(
    @Json(name = "pairing_code")   val pairingCode: String,
    @Json(name = "guardian_phone") val guardianPhone: String,
)

// ── Response DTOs ─────────────────────────────────────────────

/**
 * Response from POST /api/v1/guardians/pair
 */
@JsonClass(generateAdapter = true)
data class PairGuardianResponse(
    @Json(name = "guardian_id") val guardianId: String,
    @Json(name = "token")       val token: String,
)

/**
 * Response from POST /api/v1/guardians/generate-pairing-code
 * Requires JWT with role=elder.
 */
@JsonClass(generateAdapter = true)
data class PairingCodeResponse(
    @Json(name = "pairing_code")       val pairingCode: String,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int,
)
