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
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Success(
        val activeCommitments: List<Commitment>,
        val pendingRecommendations: List<InterventionRecommendation> = emptyList(),
        val isGeneratingRecommendation: Boolean = false,
        val userMessage: String? = null
    ) : TodayUiState
    data class Error(val message: String) : TodayUiState
}

class TodayViewModel(
    private val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase,
    private val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase,
    private val scheduleCommitmentUseCase: ScheduleCommitmentUseCase,
    private val cancelCommitmentScheduleUseCase: CancelCommitmentScheduleUseCase,
    private val getPendingInterventionRecommendationsUseCase: GetPendingInterventionRecommendationsUseCase,
    private val handleInterventionResultUseCase: HandleInterventionResultUseCase,
    private val orchestrateInterventionGenerationUseCase: OrchestrateInterventionGenerationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // Using a hardcoded user ID for V0.5 as session management doesn't exist yet
    private val currentUserId = "user-1"

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            when (val result = getActiveCommitmentsUseCase(currentUserId)) {
                is Result.Success -> {
                    val commitments = result.data
                    
                    // Observe pending recommendations and update state
                    getPendingInterventionRecommendationsUseCase(currentUserId, System.currentTimeMillis())
                        .catch { e ->
                            _uiState.value = TodayUiState.Error(e.message ?: "Failed to load recommendations")
                        }
                        .collect { recommendations ->
                            _uiState.value = TodayUiState.Success(
                                activeCommitments = commitments,
                                pendingRecommendations = recommendations
                            )
                        }
                }
                is Result.Failure -> {
                    _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to load commitments")
                }
            }
        }
    }

    fun loadCommitments() {
        loadData() // Alias loadCommitments to loadData since we combined them
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
                    loadData() // Refresh the list
                }
                is Result.Failure -> {
                    loadData()
                }
            }
        }
    }



    fun scheduleCommitment(commitmentId: String, timeInMillis: Long) {
        viewModelScope.launch {
            when (val result = scheduleCommitmentUseCase(currentUserId, commitmentId, timeInMillis)) {
                is Result.Success -> loadData()
                is Result.Failure -> {
                    // Show error state
                    _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to schedule reminder")
                }
            }
        }
    }

    fun cancelSchedule(commitmentId: String) {
        viewModelScope.launch {
            when (val result = cancelCommitmentScheduleUseCase(currentUserId, commitmentId)) {
                is Result.Success -> loadData()
                is Result.Failure -> {
                    _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to cancel schedule")
                }
            }
        }
    }

    fun handleInterventionResponse(
        recommendation: InterventionRecommendation,
        action: HandleInterventionResultUseCase.Action,
        correctedText: String?
    ) {
        viewModelScope.launch {
            handleInterventionResultUseCase(
                recommendation = recommendation,
                action = action,
                userId = currentUserId,
                correctedText = correctedText
            )
            // No need to reload data explicitly here because GetPendingInterventionRecommendationsUseCase 
            // returns a Flow. When the status is updated, the Flow will emit a new list 
            // (if the database implementation supports Flow updates, Room does). 
            // If Room isn't reacting properly due to how it's queried, we might need a manual refresh,
            // but standard Room Flow implementation handles this automatically.
        }
    }

    fun clearUserMessage() {
        val currentState = _uiState.value
        if (currentState is TodayUiState.Success) {
            _uiState.value = currentState.copy(userMessage = null)
        }
    }

    fun askIronMind() {
        val currentState = _uiState.value
        if (currentState !is TodayUiState.Success) return
        if (currentState.isGeneratingRecommendation) return
        
        _uiState.value = currentState.copy(isGeneratingRecommendation = true)

        viewModelScope.launch {
            val result = orchestrateInterventionGenerationUseCase(currentUserId)
            
            // Re-fetch current state in case it changed (e.g. commitments loaded)
            val updatedState = _uiState.value
            if (updatedState is TodayUiState.Success) {
                when (result) {
                    is Result.Success -> {
                        val recommendationResult = result.data
                        if (recommendationResult == InterventionRecommendationResult.NoRecommendation) {
                            _uiState.value = updatedState.copy(
                                isGeneratingRecommendation = false,
                                userMessage = "No new suggestions at this time."
                            )
                        } else {
                            // Recommended state - Flow will update recommendations automatically
                            _uiState.value = updatedState.copy(
                                isGeneratingRecommendation = false
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.value = updatedState.copy(
                            isGeneratingRecommendation = false,
                            userMessage = "Could not reach IronMind. Try again later."
                        )
                    }
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
                    updateCommitmentStatusUseCase = container.updateCommitmentStatusUseCase,
                    scheduleCommitmentUseCase = container.scheduleCommitmentUseCase,
                    cancelCommitmentScheduleUseCase = container.cancelCommitmentScheduleUseCase,
                    getPendingInterventionRecommendationsUseCase = container.getPendingInterventionRecommendationsUseCase,
                    handleInterventionResultUseCase = container.handleInterventionResultUseCase,
                    orchestrateInterventionGenerationUseCase = container.orchestrateInterventionGenerationUseCase
                )
            }
        }
    }
}
