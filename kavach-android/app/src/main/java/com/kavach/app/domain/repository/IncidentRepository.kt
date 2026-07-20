package com.kavach.app.domain.repository

import com.kavach.app.domain.model.EvidencePackage
import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.model.ResolvedIncident
import com.kavach.app.utils.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Incident repository interface.
 * Combines local Room cache with remote API calls.
 */
interface IncidentRepository {

    /**
     * Observe all cached incidents as a live [Flow].
     * Auto-updates when the cache is refreshed.
     */
    fun observeIncidents(): Flow<List<Incident>>

    /** Observe only active (unresolved) incidents. */
    fun observeActiveIncidents(): Flow<List<Incident>>

    /** Get a single cached incident by ID (null if not cached). */
    suspend fun getIncidentById(id: String): Incident?

    /**
     * Resolve an incident as guardian.
     * resolution: "resolved" | "false_positive"
     * Updates remote + invalidates local cache entry.
     */
    suspend fun resolveIncident(
        incidentId: String,
        resolution: String,
        note: String? = null,
    ): NetworkResult<ResolvedIncident>

    /**
     * Generate an evidence PDF for an incident.
     * Returns a signed 1-hour MinIO download URL.
     */
    suspend fun generateEvidence(incidentId: String): NetworkResult<EvidencePackage>

    /**
     * Refresh the incident cache from the remote backend.
     * Call this on pull-to-refresh or app foreground.
     * NOTE: The backend has no list-incidents endpoint in the current phase —
     * incidents arrive via WebSocket [AlertEvent]. This method is a hook for
     * when that endpoint is added in a future phase.
     */
    suspend fun refreshCache(): NetworkResult<Unit>
}
