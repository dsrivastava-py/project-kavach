package com.kavach.app.domain.usecases.incident

import com.kavach.app.domain.model.ResolvedIncident
import com.kavach.app.domain.repository.IncidentRepository
import com.kavach.app.utils.NetworkResult
import javax.inject.Inject

/**
 * Resolve or mark an incident as a false positive.
 *
 * Validates the resolution value before hitting the network.
 */
class ResolveIncidentUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository,
) {
    suspend operator fun invoke(
        incidentId: String,
        resolution: String,
        note: String? = null,
    ): NetworkResult<ResolvedIncident> {
        if (resolution !in listOf("resolved", "false_positive")) {
            return NetworkResult.Error("Resolution must be 'resolved' or 'false_positive'")
        }
        if (incidentId.isBlank()) {
            return NetworkResult.Error("Incident ID is required")
        }
        return incidentRepository.resolveIncident(incidentId, resolution, note)
    }
}
