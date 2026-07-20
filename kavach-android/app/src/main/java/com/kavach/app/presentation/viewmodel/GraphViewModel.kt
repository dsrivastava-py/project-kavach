package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.domain.usecases.graph.GetMuleRingUseCase
import com.kavach.app.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GraphUiState {
    data object Idle                               : GraphUiState()
    data object Loading                            : GraphUiState()
    data class  Success(val graph: RingSubgraph)   : GraphUiState()
    data class  Error(val message: String)         : GraphUiState()
}

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val getMuleRingUseCase: GetMuleRingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<GraphUiState>(GraphUiState.Idle)
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    var lastPhone: String = ""
        private set

    fun search(phone: String, depth: Int = 3) {
        lastPhone = phone
        viewModelScope.launch {
            _state.value = GraphUiState.Loading
            _state.value = when (val result = getMuleRingUseCase(phone, depth)) {
                is NetworkResult.Success -> GraphUiState.Success(result.data)
                is NetworkResult.Error   -> GraphUiState.Error(result.message)
                NetworkResult.Loading    -> GraphUiState.Loading
            }
        }
    }

    fun reset() { _state.value = GraphUiState.Idle }
}
