package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.domain.model.EvidencePackage
import com.kavach.app.domain.model.Incident
import com.kavach.app.domain.model.ResolvedIncident
import com.kavach.app.domain.usecases.incident.GenerateEvidenceUseCase
import com.kavach.app.domain.usecases.incident.ObserveIncidentsUseCase
import com.kavach.app.domain.usecases.incident.ResolveIncidentUseCase
import com.kavach.app.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class IncidentListState {
    data object Loading                          : IncidentListState()
    data class  Success(val items: List<Incident>) : IncidentListState()
    data class  Error(val message: String)       : IncidentListState()
}

sealed class IncidentActionState {
    data object Idle    : IncidentActionState()
    data object Loading : IncidentActionState()
    data class  Resolved(val result: ResolvedIncident)    : IncidentActionState()
    data class  Evidence(val result: EvidencePackage)     : IncidentActionState()
    data class  Error(val message: String)                : IncidentActionState()
}

@HiltViewModel
class IncidentViewModel @Inject constructor(
    private val observeIncidentsUseCase: ObserveIncidentsUseCase,
    private val resolveIncidentUseCase: ResolveIncidentUseCase,
    private val generateEvidenceUseCase: GenerateEvidenceUseCase,
) : ViewModel() {

    /** Live list from Room — auto-updates on WebSocket-pushed cache writes. */
    val incidents: StateFlow<IncidentListState> = observeIncidentsUseCase()
        .map<List<Incident>, IncidentListState> { IncidentListState.Success(it) }
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.WhileSubscribed(5_000),
            initialValue  = IncidentListState.Loading,
        )

    private val _actionState = MutableStateFlow<IncidentActionState>(IncidentActionState.Idle)
    val actionState: StateFlow<IncidentActionState> = _actionState.asStateFlow()

    fun resolve(incidentId: String, resolution: String, note: String? = null) {
        viewModelScope.launch {
            _actionState.value = IncidentActionState.Loading
            _actionState.value = when (val r = resolveIncidentUseCase(incidentId, resolution, note)) {
                is NetworkResult.Success -> IncidentActionState.Resolved(r.data)
                is NetworkResult.Error   -> IncidentActionState.Error(r.message)
                NetworkResult.Loading    -> IncidentActionState.Loading
            }
        }
    }

    fun generateEvidence(incidentId: String) {
        viewModelScope.launch {
            _actionState.value = IncidentActionState.Loading
            _actionState.value = when (val r = generateEvidenceUseCase(incidentId)) {
                is NetworkResult.Success -> IncidentActionState.Evidence(r.data)
                is NetworkResult.Error   -> IncidentActionState.Error(r.message)
                NetworkResult.Loading    -> IncidentActionState.Loading
            }
        }
    }

    fun resetActionState() { _actionState.value = IncidentActionState.Idle }
}
