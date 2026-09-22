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
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GoalsUiState {
    object Loading : GoalsUiState()
    data class Success(val goals: List<Goal>) : GoalsUiState()
    data class Error(val message: String) : GoalsUiState()
}

class GoalsViewModel(
    private val getGoalsUseCase: GetGoalsUseCase,
    private val createGoalUseCase: CreateGoalUseCase,
    private val logger: IronLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<GoalsUiState>(GoalsUiState.Loading)
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private val _showCreateForm = MutableStateFlow(false)
    val showCreateForm: StateFlow<Boolean> = _showCreateForm.asStateFlow()

    // Form states
    private val _newGoalTitle = MutableStateFlow("")
    val newGoalTitle: StateFlow<String> = _newGoalTitle.asStateFlow()
    
    private val _newGoalDescription = MutableStateFlow("")
    val newGoalDescription: StateFlow<String> = _newGoalDescription.asStateFlow()
    
    private val _newGoalWhy = MutableStateFlow("")
    val newGoalWhy: StateFlow<String> = _newGoalWhy.asStateFlow()
    
    private val _newGoalImportance = MutableStateFlow(5f)
    val newGoalImportance: StateFlow<Float> = _newGoalImportance.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val defaultUserId = "user-1" // V0 Local User

    init {
        loadGoals()
    }

    fun loadGoals() {
        logger.logLifecycle("Goal", "LOAD_START")
        viewModelScope.launch {
            _uiState.value = GoalsUiState.Loading
            val result = getGoalsUseCase(defaultUserId)
            when (result) {
                is Result.Success -> {
                    logger.logLifecycle("Goal", "LOAD_SUCCESS", mapOf("count" to result.data.size))
                    _uiState.value = GoalsUiState.Success(result.data)
                }
                is Result.Failure -> {
                    logger.logLifecycle("Goal", "LOAD_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown")))
                    _uiState.value = GoalsUiState.Error(result.error.message ?: "Failed to load goals")
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

    fun updateNewGoalTitle(title: String) { _newGoalTitle.value = title }
    fun updateNewGoalDescription(desc: String) { _newGoalDescription.value = desc }
    fun updateNewGoalWhy(why: String) { _newGoalWhy.value = why }
    fun updateNewGoalImportance(imp: Float) { _newGoalImportance.value = imp }

    fun createGoal() {
        if (_newGoalTitle.value.isBlank()) {
            _saveError.value = "Title cannot be empty"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Goal", "CREATE_START")
            _isSaving.value = true
            _saveError.value = null
            
            val result = createGoalUseCase(
                userId = defaultUserId,
                title = _newGoalTitle.value.trim(),
                description = _newGoalDescription.value.trim(),
                why = _newGoalWhy.value.trim(),
                importance = _newGoalImportance.value.toInt(),
                targetAt = null // Optional in V0
            )

            _isSaving.value = false

            when (result) {
                is Result.Success -> {
                    logger.logLifecycle("Goal", "CREATE_SUCCESS", mapOf("goalId" to result.data.id))
                    closeCreateForm()
                    loadGoals()
                }
                is Result.Failure -> {
                    logger.logLifecycle("Goal", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                    _saveError.value = result.error.message ?: "Failed to create goal"
                }
            }
        }
    }

    private fun clearForm() {
        _newGoalTitle.value = ""
        _newGoalDescription.value = ""
        _newGoalWhy.value = ""
        _newGoalImportance.value = 5f
        _saveError.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                GoalsViewModel(
                    getGoalsUseCase = application.container.getGoalsUseCase,
                    createGoalUseCase = application.container.createGoalUseCase,
                    logger = application.container.logger
                )
            }
        }
    }
}
