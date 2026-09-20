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
import com.sanket_satpute_20.ironmind.domain.usecase.ai.RecommendInterventionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Success(
        val activeCommitments: List<Commitment>,
        val activeSuggestion: AIOutput.InterventionRecommendation? = null,
        val isGlobalPauseActive: Boolean = false
    ) : TodayUiState
    data class Error(val message: String) : TodayUiState
}

class TodayViewModel(
    private val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase,
    private val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase,
    private val recommendInterventionUseCase: RecommendInterventionUseCase,
    private val handleInterventionResultUseCase: HandleInterventionResultUseCase,
    private val getAutonomySettingsUseCase: GetAutonomySettingsUseCase
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
                    val commitments = result.data
                    // Trigger suggestion check in background
                    fetchSuggestion(commitments)
                    val autonomyResult = getAutonomySettingsUseCase(currentUserId)
                    val isPaused = if (autonomyResult is Result.Success) autonomyResult.data.isGlobalPauseActive else false
                    _uiState.value = TodayUiState.Success(
                        activeCommitments = commitments,
                        isGlobalPauseActive = isPaused
                    )
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

    private fun fetchSuggestion(commitments: List<Commitment>) {
        // Only fetch a suggestion if we don't have one and we have active commitments
        if (commitments.isEmpty()) return
        
        val currentState = _uiState.value
        if (currentState is TodayUiState.Success && currentState.activeSuggestion != null) return

        viewModelScope.launch {
            val contextText = "User has ${commitments.size} active commitments today."
            val result = recommendInterventionUseCase(contextText, currentUserId)
            
            if (result is Result.Success) {
                val recommendation = result.data
                if (recommendation.interventionType != InterventionType.STAY_SILENT) {
                    val currentSuccessState = _uiState.value as? TodayUiState.Success
                    if (currentSuccessState != null) {
                        _uiState.value = currentSuccessState.copy(activeSuggestion = recommendation)
                    }
                }
            }
        }
    }

    fun handleSuggestionAction(
        recommendation: AIOutput.InterventionRecommendation,
        action: HandleInterventionResultUseCase.Action,
        correctedText: String? = null
    ) {
        viewModelScope.launch {
            // Dismiss suggestion immediately from UI
            val currentSuccessState = _uiState.value as? TodayUiState.Success
            if (currentSuccessState != null) {
                _uiState.value = currentSuccessState.copy(activeSuggestion = null)
            }
            
            // Record result
            handleInterventionResultUseCase(
                recommendation = recommendation,
                action = action,
                userId = currentUserId,
                correctedText = correctedText
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                TodayViewModel(
                    getActiveCommitmentsUseCase = container.getActiveCommitmentsUseCase,
                    updateCommitmentStatusUseCase = container.updateCommitmentStatusUseCase,
                    recommendInterventionUseCase = container.recommendInterventionUseCase,
                    handleInterventionResultUseCase = container.handleInterventionResultUseCase,
                    getAutonomySettingsUseCase = container.getAutonomySettingsUseCase
                )
            }
        }
    }
}
