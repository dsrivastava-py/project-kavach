package com.kavach.app.domain.repository

import com.kavach.app.domain.model.Plan
import com.kavach.app.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Billing repository interface.
 * Cache-first: returns cached plans immediately while refreshing in background.
 */
interface BillingRepository {

    /** Observe cached plans as a live [Flow]. */
    fun observePlans(): Flow<List<Plan>>

    /** Refresh plans from remote, updating the local cache. */
    suspend fun refreshPlans(): NetworkResult<List<Plan>>
}
