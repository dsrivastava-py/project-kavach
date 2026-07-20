package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A single plan returned by GET /api/v1/billing/plans
 */
@JsonClass(generateAdapter = true)
data class PlanDto(
    @Json(name = "id")            val id: String,
    @Json(name = "name")          val name: String,
    @Json(name = "price_inr")     val priceInr: Int,
    @Json(name = "billing_cycle") val billingCycle: String?,
    @Json(name = "features")      val features: List<String>,
)

/**
 * Wrapper for GET /api/v1/billing/plans response.
 * { "plans": [...] }
 */
@JsonClass(generateAdapter = true)
data class PlansResponse(
    @Json(name = "plans") val plans: List<PlanDto>,
)
