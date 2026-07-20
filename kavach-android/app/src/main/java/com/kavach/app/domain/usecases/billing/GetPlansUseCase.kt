package com.kavach.app.domain.usecases.billing

import com.kavach.app.domain.model.Plan
import com.kavach.app.domain.repository.BillingRepository
import com.kavach.app.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a [Flow] of cached plans and triggers a background refresh.
 * The ViewModel collects the flow and calls refresh separately.
 */
class GetPlansUseCase @Inject constructor(
    private val billingRepository: BillingRepository,
) {
    operator fun invoke(): Flow<List<Plan>> = billingRepository.observePlans()

    suspend fun refresh(): NetworkResult<List<Plan>> = billingRepository.refreshPlans()
}
