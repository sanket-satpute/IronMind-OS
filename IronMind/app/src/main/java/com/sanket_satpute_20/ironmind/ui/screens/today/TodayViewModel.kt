package com.sanket_satpute_20.ironmind.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Success(val activeCommitments: List<Commitment>) : TodayUiState
    data class Error(val message: String) : TodayUiState
}

class TodayViewModel(
    private val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase,
    private val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // Using a hardcoded user ID for V0.5 as session management doesn't exist yet
    private val currentUserId = "user-1"

    init {
        loadCommitments()
    }

    fun loadCommitments() {
        viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            when (val result = getActiveCommitmentsUseCase(currentUserId)) {
                is Result.Success -> {
                    _uiState.value = TodayUiState.Success(result.data)
                }
                is Result.Failure -> {
                    _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to load commitments")
                }
            }
        }
    }

    fun updateCommitmentStatus(
        commitmentId: String, 
        newStatus: CommitmentStatus, 
        resultStatus: ResultStatus? = null,
        actualDurationMinutes: Int? = null
    ) {
        viewModelScope.launch {
            when (val result = updateCommitmentStatusUseCase(commitmentId, newStatus, resultStatus, actualDurationMinutes)) {
                is Result.Success -> {
                    loadCommitments() // Refresh the list
                }
                is Result.Failure -> {
                    loadCommitments()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                TodayViewModel(
                    getActiveCommitmentsUseCase = container.getActiveCommitmentsUseCase,
                    updateCommitmentStatusUseCase = container.updateCommitmentStatusUseCase
                )
            }
        }
    }
}
