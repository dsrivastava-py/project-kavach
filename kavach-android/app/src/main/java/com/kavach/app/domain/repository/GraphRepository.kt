package com.kavach.app.domain.repository

import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.utils.NetworkResult

/**
 * Fraud-graph repository interface.
 * Investigator role only.
 */
interface GraphRepository {

    /**
     * Fetch the mule-ring subgraph reachable from [phone] within [depth] hops.
     * [depth] must be 1–6.
     */
    suspend fun getMuleRing(phone: String, depth: Int = 3): NetworkResult<RingSubgraph>
}
