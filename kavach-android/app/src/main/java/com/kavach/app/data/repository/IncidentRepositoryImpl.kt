package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.IncidentCacheDao
import com.kavach.app.data.local.entity.IncidentEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.ResolveIncidentRequest
import com.kavach.app.domain.model.*
import com.kavach.app.domain.repository.IncidentRepository
import com.kavach.app.utils.NetworkResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Combines Room cache (offline-first) with remote API calls.
 *
 * Read path  → Room [Flow] (always fresh via reactive queries)
 * Write path → remote call first, then update cache on success
 */
class IncidentRepositoryImpl @Inject constructor(
    private val api: KavachApiService,
    private val dao: IncidentCacheDao,
) : IncidentRepository {

    override fun observeIncidents(): Flow<List<Incident>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveIncidents(): Flow<List<Incident>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getIncidentById(id: String): Incident? =
        dao.getById(id)?.toDomain()

    override suspend fun resolveIncident(
        incidentId: String,
        resolution: String,
        note: String?,
    ): NetworkResult<ResolvedIncident> {
        val result = safeApiCall {
            api.resolveIncident(incidentId, ResolveIncidentRequest(resolution, note))
        }

        if (result is NetworkResult.Success) {
            // Sync resolved state to local cache
            dao.getById(incidentId)?.let { cached ->
                dao.upsert(
                    cached.copy(
                        status      = result.data.status,
                        resolvedAt  = result.data.resolvedAt,
                        resolutionNote = note,
                    )
                )
            }
        }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                ResolvedIncident(result.data.incidentId, result.data.status, result.data.resolvedAt)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun generateEvidence(incidentId: String): NetworkResult<EvidencePackage> {
        val result = safeApiCall { api.generateEvidence(incidentId) }

        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(
                EvidencePackage(result.data.incidentId, result.data.pdfRef, result.data.downloadUrl)
            )
            is NetworkResult.Error   -> result
            NetworkResult.Loading    -> NetworkResult.Loading
        }
    }

    override suspend fun refreshCache(): NetworkResult<Unit> {
        // No list-incidents endpoint exists in the current backend phase.
        // Incidents are pushed via WebSocket. This is a no-op placeholder.
        return NetworkResult.Success(Unit)
    }

    // ── Extension: Entity → Domain ────────────────────────────

    /** Cache an incident received from a WebSocket alert event. */
    suspend fun cacheIncident(incident: Incident) {
        dao.upsert(incident.toEntity())
    }

    private fun IncidentEntity.toDomain() = Incident(
        id             = id,
        elderId        = elderId,
        status         = IncidentStatus.fromString(status),
        riskScore      = riskScore,
        startedAt      = startedAt,
        resolvedAt     = resolvedAt,
        resolutionNote = resolutionNote,
    )

    private fun Incident.toEntity() = IncidentEntity(
        id             = id,
        elderId        = elderId,
        status         = status.value,
        riskScore      = riskScore,
        startedAt      = startedAt,
        resolvedAt     = resolvedAt,
        resolutionNote = resolutionNote,
    )
}
