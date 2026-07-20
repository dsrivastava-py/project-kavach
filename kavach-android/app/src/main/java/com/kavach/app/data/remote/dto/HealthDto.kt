package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from GET /api/v1/health
 */
@JsonClass(generateAdapter = true)
data class HealthDto(
    @Json(name = "status") val status: String,
    @Json(name = "db")     val db: String,
    @Json(name = "redis")  val redis: String,
    @Json(name = "neo4j")  val neo4j: String,
)
