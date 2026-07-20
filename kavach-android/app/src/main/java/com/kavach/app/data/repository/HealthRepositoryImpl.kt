package com.kavach.app.data.repository

import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.domain.model.HealthStatus
import com.kavach.app.domain.repository.HealthRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
) : HealthRepository {

    override suspend fun getHealth(): NetworkResult<HealthStatus> {
        val result = safeApiCall { api.getHealth() }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                HealthStatus(result.data.status, result.data.db, result.data.redis, result.data.neo4j)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }
}
