package com.kavach.app.domain.model

/**
 * Domain model for the backend health probe response.
 */
data class HealthStatus(
    val overall: String,    // "ok" | "degraded"
    val db: String,
    val redis: String,
    val neo4j: String,
) {
    val isHealthy: Boolean get() = overall == "ok"
}
