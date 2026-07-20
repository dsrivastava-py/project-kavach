package com.kavach.app.domain.model

/**
 * Domain model for a subscription plan.
 * Maps from [PlanEntity] (local cache) and [PlanDto] (remote).
 */
data class Plan(
    val id: String,
    val name: String,
    val priceInr: Int,
    val billingCycle: String?,
    val features: List<String>,
)

/** Convenience: free tier check. */
val Plan.isFree: Boolean get() = priceInr == 0
