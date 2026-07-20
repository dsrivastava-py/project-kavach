package com.kavach.app.data.repository

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.domain.model.GraphEdge
import com.kavach.app.domain.model.GraphNode
import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.domain.repository.GraphRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import javax.inject.Inject

class GraphRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
) : GraphRepository {

    override suspend fun getMuleRing(phone: String, depth: Int): NetworkResult<RingSubgraph> {
        val result = safeApiCall { api.getMuleRing(phone, depth) }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                RingSubgraph(
                    nodes     = result.data.nodes.map { GraphNode(it.id, it.label, it.group) },
                    edges     = result.data.edges.map { GraphEdge(it.source, it.target, it.relationship) },
                    nodeCount = result.data.nodeCount,
                    edgeCount = result.data.edgeCount,
                )
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }
}
