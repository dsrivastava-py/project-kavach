package com.kavach.app.domain.usecases.incident

import com.kavach.app.domain.model.EvidencePackage
import com.kavach.app.domain.repository.IncidentRepository
import com.kavach.app.utils.NetworkResult
import javax.inject.Inject

/** Generate an evidence PDF for an incident and return a signed download URL. */
class GenerateEvidenceUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository,
) {
    suspend operator fun invoke(incidentId: String): NetworkResult<EvidencePackage> =
        incidentRepository.generateEvidence(incidentId)
}
