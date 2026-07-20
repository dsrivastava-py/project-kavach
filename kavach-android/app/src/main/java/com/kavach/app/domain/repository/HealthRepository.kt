package com.kavach.app.domain.repository

import com.kavach.app.domain.model.HealthStatus
import com.kavach.app.utils.NetworkResult

/** Health-check repository interface. */
interface HealthRepository {
    suspend fun getHealth(): NetworkResult<HealthStatus>
}
