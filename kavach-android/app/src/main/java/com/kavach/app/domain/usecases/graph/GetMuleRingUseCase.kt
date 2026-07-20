package com.kavach.app.domain.usecases.graph

import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.domain.repository.GraphRepository
import com.kavach.app.utils.NetworkResult
import javax.inject.Inject

/**
 * Fetch the fraud-ring subgraph for a given phone number.
 * Validates depth range (1–6) before calling the API.
 */
class GetMuleRingUseCase @Inject constructor(
    private val graphRepository: GraphRepository,
) {
    suspend operator fun invoke(
        phone: String,
        depth: Int = 3,
    ): NetworkResult<RingSubgraph> {
        if (phone.isBlank()) return NetworkResult.Error("Phone number is required")
        if (depth < 1 || depth > 6) return NetworkResult.Error("Depth must be between 1 and 6")
        return graphRepository.getMuleRing(phone, depth)
    }
}
