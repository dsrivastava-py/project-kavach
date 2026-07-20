package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.PlanCacheDao
import com.kavach.app.data.local.entity.PlanEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.domain.model.Plan
import com.kavach.app.domain.repository.BillingRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BillingRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
    private val dao: PlanCacheDao,
) : BillingRepository {

    override fun observePlans(): Flow<List<Plan>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshPlans(): NetworkResult<List<Plan>> {
        val result = safeApiCall { api.getBillingPlans() }

        if (result is NetworkResult.Success) {
            val entities = result.data.plans.map { dto ->
                PlanEntity(
                    id           = dto.id,
                    name         = dto.name,
                    priceInr     = dto.priceInr,
                    billingCycle = dto.billingCycle,
                    features     = dto.features.joinToString("|"),
                )
            }
            dao.clearAll()
            dao.upsertAll(entities)
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                result.data.plans.map { dto ->
                    Plan(dto.id, dto.name, dto.priceInr, dto.billingCycle, dto.features)
                }
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    private fun PlanEntity.toDomain() = Plan(
        id           = id,
        name         = name,
        priceInr     = priceInr,
        billingCycle = billingCycle,
        features     = features.split("|").filter { it.isNotBlank() },
    )
}
