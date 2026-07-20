package com.kavach.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GET /api/v1/graph/ring/{phone} — investigator role only.
 */
@JsonClass(generateAdapter = true)
data class GraphNodeDto(
    @Json(name = "id")    val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "group") val group: String,
)

@JsonClass(generateAdapter = true)
data class GraphEdgeDto(
    @Json(name = "source")       val source: String,
    @Json(name = "target")       val target: String,
    @Json(name = "relationship") val relationship: String,
)

@JsonClass(generateAdapter = true)
data class RingSubgraphDto(
    @Json(name = "nodes")      val nodes: List<GraphNodeDto>,
    @Json(name = "edges")      val edges: List<GraphEdgeDto>,
    @Json(name = "node_count") val nodeCount: Int,
    @Json(name = "edge_count") val edgeCount: Int,
)
