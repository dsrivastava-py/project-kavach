package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.domain.model.DeepCheckStatus
import com.kavach.app.domain.usecases.deepcheck.StartDeepCheckUseCase
import com.kavach.app.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class DeepCheckUiState {
    data object Idle                                    : DeepCheckUiState()
    data object Uploading                               : DeepCheckUiState()
    data class  Polling(val session: DeepCheckSession)  : DeepCheckUiState()
    data class  Done(val session: DeepCheckSession)     : DeepCheckUiState()
    data class  Error(val message: String)              : DeepCheckUiState()
}

@HiltViewModel
class DeepCheckViewModel @Inject constructor(
    private val startDeepCheckUseCase: StartDeepCheckUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow<DeepCheckUiState>(DeepCheckUiState.Idle)
    val state: StateFlow<DeepCheckUiState> = _state.asStateFlow()

    fun startDeepCheck(audioFile: File, incidentId: String? = null) {
        viewModelScope.launch {
            val elderId = sessionManager.elderId.first() ?: run {
                _state.value = DeepCheckUiState.Error("Elder ID not found. Are you logged in as elder?")
                return@launch
            }

            _state.value = DeepCheckUiState.Uploading

            val result = startDeepCheckUseCase(
                audioFile  = audioFile,
                elderId    = elderId,
                incidentId = incidentId,
                onPoll     = { session ->
                    if (!session.status.isTerminal) {
                        _state.value = DeepCheckUiState.Polling(session)
                    }
                }
            )

            _state.value = when (result) {
                is NetworkResult.Success -> DeepCheckUiState.Done(result.data)
                is NetworkResult.Error   -> DeepCheckUiState.Error(result.message)
                NetworkResult.Loading    -> DeepCheckUiState.Uploading
            }
        }
    }

    fun reset() { _state.value = DeepCheckUiState.Idle }
}
