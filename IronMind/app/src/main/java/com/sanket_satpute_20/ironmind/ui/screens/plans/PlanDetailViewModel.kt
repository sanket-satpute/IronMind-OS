package com.sanket_satpute_20.ironmind.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlanDetailUiState {
    object Loading : PlanDetailUiState()
    data class Success(val plan: Plan, val tasks: List<Task>) : PlanDetailUiState()
    data class Error(val message: String) : PlanDetailUiState()
}

class PlanDetailViewModel(
    private val getPlanUseCase: GetPlanUseCase,
    private val getTasksUseCase: GetTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val logger: IronLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanDetailUiState>(PlanDetailUiState.Loading)
    val uiState: StateFlow<PlanDetailUiState> = _uiState.asStateFlow()

    private val _showCreateForm = MutableStateFlow(false)
    val showCreateForm: StateFlow<Boolean> = _showCreateForm.asStateFlow()

    // Form states for Task
    private val _newTaskTitle = MutableStateFlow("")
    val newTaskTitle: StateFlow<String> = _newTaskTitle.asStateFlow()
    
    private val _newTaskDescription = MutableStateFlow("")
    val newTaskDescription: StateFlow<String> = _newTaskDescription.asStateFlow()

    private val _newTaskPriority = MutableStateFlow(1f)
    val newTaskPriority: StateFlow<Float> = _newTaskPriority.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val defaultUserId = "user-1"
    private var currentPlanId: String? = null
    private var currentGoalId: String? = null

    fun loadData(planId: String) {
        currentPlanId = planId
        logger.logLifecycle("PlanDetail", "LOAD_START", mapOf("planId" to planId))
        viewModelScope.launch {
            _uiState.value = PlanDetailUiState.Loading
            
            val planResult = getPlanUseCase(planId)
            if (planResult is Result.Failure) {
                _uiState.value = PlanDetailUiState.Error(planResult.error.message ?: "Failed to load plan")
                return@launch
            }
            
            val plan = (planResult as Result.Success).data
            if (plan == null) {
                _uiState.value = PlanDetailUiState.Error("Plan not found")
                return@launch
            }
            currentGoalId = plan.goalId

            val tasksResult = getTasksUseCase(planId)
            when (tasksResult) {
                is Result.Success -> {
                    logger.logLifecycle("PlanDetail", "LOAD_SUCCESS", mapOf("tasksCount" to tasksResult.data.size))
                    _uiState.value = PlanDetailUiState.Success(plan, tasksResult.data)
                }
                is Result.Failure -> {
                    logger.logLifecycle("PlanDetail", "LOAD_FAILURE", mapOf("reason" to (tasksResult.error.message ?: "Unknown")))
                    _uiState.value = PlanDetailUiState.Error(tasksResult.error.message ?: "Failed to load tasks")
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

    fun updateNewTaskTitle(title: String) { _newTaskTitle.value = title }
    fun updateNewTaskDescription(desc: String) { _newTaskDescription.value = desc }
    fun updateNewTaskPriority(priority: Float) { _newTaskPriority.value = priority }

    fun createTask() {
        val planId = currentPlanId ?: return

        if (_newTaskTitle.value.isBlank()) {
            _saveError.value = "Title cannot be empty"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Task", "CREATE_START")
            _isSaving.value = true
            _saveError.value = null
            
            val result = createTaskUseCase(
                userId = defaultUserId,
                goalId = currentGoalId,
                planId = planId,
                title = _newTaskTitle.value.trim(),
                description = _newTaskDescription.value.trim(),
                priority = _newTaskPriority.value.toInt()
            )

            _isSaving.value = false

            when (result) {
                is Result.Success -> {
                    logger.logLifecycle("Task", "CREATE_SUCCESS", mapOf("taskId" to result.data.id))
                    closeCreateForm()
                    loadData(planId) // Refresh list
                }
                is Result.Failure -> {
                    logger.logLifecycle("Task", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                    _saveError.value = result.error.message ?: "Failed to create task"
                }
            }
        }
    }

    private fun clearForm() {
        _newTaskTitle.value = ""
        _newTaskDescription.value = ""
        _newTaskPriority.value = 1f
        _saveError.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                PlanDetailViewModel(
                    getPlanUseCase = application.container.getPlanUseCase,
                    getTasksUseCase = application.container.getTasksUseCase,
                    createTaskUseCase = application.container.createTaskUseCase,
                    logger = application.container.logger
                )
            }
        }
    }
}
