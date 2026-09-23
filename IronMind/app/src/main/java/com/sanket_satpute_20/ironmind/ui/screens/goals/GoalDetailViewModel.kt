package com.sanket_satpute_20.ironmind.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GoalDetailUiState {
    object Loading : GoalDetailUiState()
    data class Success(val goal: Goal, val plans: List<Plan>) : GoalDetailUiState()
    data class Error(val message: String) : GoalDetailUiState()
}

class GoalDetailViewModel(
    private val getGoalUseCase: GetGoalUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPlanUseCase: CreatePlanUseCase,
    private val logger: IronLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<GoalDetailUiState>(GoalDetailUiState.Loading)
    val uiState: StateFlow<GoalDetailUiState> = _uiState.asStateFlow()

    private val _showCreateForm = MutableStateFlow(false)
    val showCreateForm: StateFlow<Boolean> = _showCreateForm.asStateFlow()

    // Form states for Plan
    private val _newPlanTitle = MutableStateFlow("")
    val newPlanTitle: StateFlow<String> = _newPlanTitle.asStateFlow()
    
    private val _newPlanDescription = MutableStateFlow("")
    val newPlanDescription: StateFlow<String> = _newPlanDescription.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val defaultUserId = "user-1"
    private var currentGoalId: String? = null

    fun loadData(goalId: String) {
        currentGoalId = goalId
        logger.logLifecycle("GoalDetail", "LOAD_START", mapOf("goalId" to goalId))
        viewModelScope.launch {
            _uiState.value = GoalDetailUiState.Loading
            
            val goalResult = getGoalUseCase(goalId)
            if (goalResult is Result.Failure) {
                _uiState.value = GoalDetailUiState.Error(goalResult.error.message ?: "Failed to load goal")
                return@launch
            }
            
            val goal = (goalResult as Result.Success).data
            if (goal == null) {
                _uiState.value = GoalDetailUiState.Error("Goal not found")
                return@launch
            }

            val plansResult = getPlansUseCase(goalId)
            when (plansResult) {
                is Result.Success -> {
                    logger.logLifecycle("GoalDetail", "LOAD_SUCCESS", mapOf("plansCount" to plansResult.data.size))
                    _uiState.value = GoalDetailUiState.Success(goal, plansResult.data)
                }
                is Result.Failure -> {
                    logger.logLifecycle("GoalDetail", "LOAD_FAILURE", mapOf("reason" to (plansResult.error.message ?: "Unknown")))
                    _uiState.value = GoalDetailUiState.Error(plansResult.error.message ?: "Failed to load plans")
                }
            }
        }
    }

    fun openCreateForm() {
        _showCreateForm.value = true
        _saveError.value = null
    }

    fun closeCreateForm() {
        _showCreateForm.value = false
        clearForm()
    }

    fun updateNewPlanTitle(title: String) { _newPlanTitle.value = title }
    fun updateNewPlanDescription(desc: String) { _newPlanDescription.value = desc }

    fun createPlan() {
        val goalId = currentGoalId ?: return

        if (_newPlanTitle.value.isBlank()) {
            _saveError.value = "Title cannot be empty"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Plan", "CREATE_START")
            _isSaving.value = true
            _saveError.value = null
            
            val result = createPlanUseCase(
                userId = defaultUserId,
                goalId = goalId,
                title = _newPlanTitle.value.trim(),
                description = _newPlanDescription.value.trim()
            )

            _isSaving.value = false

            when (result) {
                is Result.Success -> {
                    logger.logLifecycle("Plan", "CREATE_SUCCESS", mapOf("planId" to result.data.id))
                    closeCreateForm()
                    loadData(goalId) // Refresh list
                }
                is Result.Failure -> {
                    logger.logLifecycle("Plan", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                    _saveError.value = result.error.message ?: "Failed to create plan"
                }
            }
        }
    }

    private fun clearForm() {
        _newPlanTitle.value = ""
        _newPlanDescription.value = ""
        _saveError.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                GoalDetailViewModel(
                    getGoalUseCase = application.container.getGoalUseCase,
                    getPlansUseCase = application.container.getPlansUseCase,
                    createPlanUseCase = application.container.createPlanUseCase,
                    logger = application.container.logger
                )
            }
        }
    }
}
