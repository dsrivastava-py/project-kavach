package com.kavach.app.domain.usecases.incident

import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observe all cached incidents as a reactive [Flow]. */
class ObserveIncidentsUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository,
) {
    operator fun invoke(): Flow<List<Incident>> =
        incidentRepository.observeIncidents()
}
