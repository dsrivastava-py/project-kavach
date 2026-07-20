package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.domain.model.Plan
import com.kavach.app.domain.usecases.billing.GetPlansUseCase
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

sealed class PlansUiState {
    data object Loading                           : PlansUiState()
    data class  Success(val plans: List<Plan>)    : PlansUiState()
    data class  Error(val message: String)        : PlansUiState()
}

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val getPlansUseCase: GetPlansUseCase,
) : ViewModel() {

    val plansState: StateFlow<PlansUiState> = getPlansUseCase()
        .map<List<Plan>, PlansUiState> { plans ->
            if (plans.isEmpty()) PlansUiState.Loading else PlansUiState.Success(plans)
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlansUiState.Loading,
        )

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            when (val result = getPlansUseCase.refresh()) {
                is NetworkResult.Error -> _refreshError.value = result.message
                else                   -> _refreshError.value = null
            }
        }
    }
}
