package com.kavach.app.domain.model

/**
 * Domain model for the fraud-ring subgraph.
 * Investigator role only.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val group: String,
)

data class GraphEdge(
    val source: String,
    val target: String,
    val relationship: String,
)

data class RingSubgraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val nodeCount: Int,
    val edgeCount: Int,
)
