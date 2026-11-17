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
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.EditPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.UpdatePlanStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.UpdateTaskStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.DeleteTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

sealed class PlanDetailUiState {
    object Loading : PlanDetailUiState()
    data class Success(
        val plan: Plan,
        val tasks: List<Task>,
        val committedTaskIds: Set<String>,
        val progress: ProgressInfo = ProgressInfo.fromTasks(tasks, plan.userId)
    ) : PlanDetailUiState()
    data class Error(val message: String) : PlanDetailUiState()
}

class PlanDetailViewModel(
    private val getPlanUseCase: GetPlanUseCase,
    private val getTasksUseCase: GetTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase,
    private val createCommitmentUseCase: CreateCommitmentUseCase,
    private val logger: IronLogger,
    private val userSessionProvider: UserSessionProvider? = null,
    private val editTaskUseCase: EditTaskUseCase? = null,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase? = null,
    private val deleteTaskUseCase: DeleteTaskUseCase? = null,
    private val editPlanUseCase: EditPlanUseCase? = null,
    private val updatePlanStatusUseCase: UpdatePlanStatusUseCase? = null,
    private val getPlanProgressUseCase: GetPlanProgressUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanDetailUiState>(PlanDetailUiState.Loading)
    val uiState: StateFlow<PlanDetailUiState> = _uiState.asStateFlow()

    private val _showCreateForm = MutableStateFlow(false)
    val showCreateForm: StateFlow<Boolean> = _showCreateForm.asStateFlow()

    // Form states for Create Task
    private val _newTaskTitle = MutableStateFlow("")
    val newTaskTitle: StateFlow<String> = _newTaskTitle.asStateFlow()

    private val _newTaskDescription = MutableStateFlow("")
    val newTaskDescription: StateFlow<String> = _newTaskDescription.asStateFlow()

    private val _newTaskPriority = MutableStateFlow(1f)
    val newTaskPriority: StateFlow<Float> = _newTaskPriority.asStateFlow()

    private val _newTaskDueAt = MutableStateFlow<Long?>(null)
    val newTaskDueAt: StateFlow<Long?> = _newTaskDueAt.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    // Edit Task states
    private val _editingTask = MutableStateFlow<Task?>(null)
    val editingTask: StateFlow<Task?> = _editingTask.asStateFlow()

    private val _editTaskTitle = MutableStateFlow("")
    val editTaskTitle: StateFlow<String> = _editTaskTitle.asStateFlow()

    private val _editTaskDescription = MutableStateFlow("")
    val editTaskDescription: StateFlow<String> = _editTaskDescription.asStateFlow()

    private val _editTaskPriority = MutableStateFlow(1f)
    val editTaskPriority: StateFlow<Float> = _editTaskPriority.asStateFlow()

    private val _editTaskDueAt = MutableStateFlow<Long?>(null)
    val editTaskDueAt: StateFlow<Long?> = _editTaskDueAt.asStateFlow()

    private val _isPerformingTaskAction = MutableStateFlow(false)
    val isPerformingTaskAction: StateFlow<Boolean> = _isPerformingTaskAction.asStateFlow()

    private val _taskActionError = MutableStateFlow<String?>(null)
    val taskActionError: StateFlow<String?> = _taskActionError.asStateFlow()

    private val _taskPendingDeletion = MutableStateFlow<Task?>(null)
    val taskPendingDeletion: StateFlow<Task?> = _taskPendingDeletion.asStateFlow()

    private val _deletionSuccessMessage = MutableStateFlow<String?>(null)
    val deletionSuccessMessage: StateFlow<String?> = _deletionSuccessMessage.asStateFlow()

    // Plan Edit & Lifecycle states
    private val _showEditPlanForm = MutableStateFlow(false)
    val showEditPlanForm: StateFlow<Boolean> = _showEditPlanForm.asStateFlow()

    private val _editPlanTitle = MutableStateFlow("")
    val editPlanTitle: StateFlow<String> = _editPlanTitle.asStateFlow()

    private val _editPlanDescription = MutableStateFlow("")
    val editPlanDescription: StateFlow<String> = _editPlanDescription.asStateFlow()

    private val _editPlanStatus = MutableStateFlow(PlanStatus.ACTIVE)
    val editPlanStatus: StateFlow<PlanStatus> = _editPlanStatus.asStateFlow()

    private val _isPerformingPlanAction = MutableStateFlow(false)
    val isPerformingPlanAction: StateFlow<Boolean> = _isPerformingPlanAction.asStateFlow()

    private val _planActionError = MutableStateFlow<String?>(null)
    val planActionError: StateFlow<String?> = _planActionError.asStateFlow()

    private val _planUserMessage = MutableStateFlow<String?>(null)
    val planUserMessage: StateFlow<String?> = _planUserMessage.asStateFlow()

    private val _showCompletePlanConfirmation = MutableStateFlow(false)
    val showCompletePlanConfirmation: StateFlow<Boolean> = _showCompletePlanConfirmation.asStateFlow()

    private val defaultUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var observedSessionUserId: String? = null
    private var currentPlanId: String? = null
    private var currentGoalId: String? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        val provider = userSessionProvider ?: return
        viewModelScope.launch {
            provider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        observedSessionUserId = null
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = PlanDetailUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observedSessionUserId = null
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = PlanDetailUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        val userId = state.currentUserId
                        if (observedSessionUserId != userId) {
                            observedSessionUserId = userId
                            loadJob?.cancel()
                            _uiState.value = PlanDetailUiState.Loading
                            currentPlanId?.let { loadDataForUser(it, userId) }
                        }
                    }
                }
            }
        }
    }

    fun loadData(planId: String) {
        currentPlanId = planId
        val state = userSessionProvider?.sessionState?.value
        when (state) {
            is SessionState.Initializing -> {
                loadGeneration++
                loadJob?.cancel()
                _uiState.value = PlanDetailUiState.Loading
                return
            }
            is SessionState.ReconciliationFailed -> {
                loadGeneration++
                loadJob?.cancel()
                _uiState.value = PlanDetailUiState.Error(
                    "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                )
                return
            }
            else -> Unit
        }
        val userId = state?.currentUserId ?: defaultUserId
        observedSessionUserId = userId
        loadDataForUser(planId, userId)
    }

    private fun loadDataForUser(planId: String, userId: String) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        logger.logLifecycle("Task", "LOAD_START", mapOf("planId" to planId))
        loadJob = viewModelScope.launch {
            _uiState.value = PlanDetailUiState.Loading
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession
                    val planResult = getPlanUseCase(planId)
                    if (!isCurrentLoad(userId, generation)) return@runWithSession
                    if (planResult is Result.Failure) {
                        _uiState.value = PlanDetailUiState.Error(planResult.error.message ?: "Failed to load plan")
                        return@runWithSession
                    }

                    val plan = (planResult as Result.Success).data
                    if (plan == null || plan.userId != activeUid) {
                        _uiState.value = PlanDetailUiState.Error("Plan not found for this session")
                        return@runWithSession
                    }
                    currentGoalId = plan.goalId

                    val tasksResult = getTasksUseCase(planId)
                    val commitmentsResult = getActiveCommitmentsUseCase(activeUid)
                    if (!isCurrentLoad(userId, generation)) return@runWithSession

                    if (tasksResult is Result.Failure) {
                        logger.logLifecycle("Task", "LOAD_FAILURE", mapOf("reason" to (tasksResult.error.message ?: "Unknown")))
                        _uiState.value = PlanDetailUiState.Error(tasksResult.error.message ?: "Failed to load tasks")
                        return@runWithSession
                    }
                    val tasks = (tasksResult as Result.Success).data
                    if (tasks.any { it.userId != activeUid }) {
                        _uiState.value = PlanDetailUiState.Error("Plan tasks do not belong to this session")
                        return@runWithSession
                    }
                    if (commitmentsResult is Result.Failure) {
                        _uiState.value = PlanDetailUiState.Error(
                            commitmentsResult.error.message ?: "Failed to load commitments"
                        )
                        return@runWithSession
                    }
                    val commitments = (commitmentsResult as Result.Success).data
                    if (commitments.any { it.userId != activeUid }) {
                        _uiState.value = PlanDetailUiState.Error("Commitments do not belong to this session")
                        return@runWithSession
                    }
                    val committedTaskIds = commitments.mapNotNull { it.taskId }.toSet()
                    val progress = getPlanProgressUseCase?.calculate(tasks, activeUid) ?: ProgressInfo.fromTasks(tasks, activeUid)
                    if (isCurrentLoad(userId, generation)) {
                        logger.logLifecycle("Task", "LOAD_SUCCESS", mapOf("tasksCount" to tasks.size))
                        _uiState.value = PlanDetailUiState.Success(plan, tasks, committedTaskIds, progress)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = PlanDetailUiState.Error(e.message ?: "Failed to load plan")
                }
            }
        }
    }

    private fun isCurrentLoad(userId: String, generation: Long): Boolean {
        if (generation != loadGeneration) return false
        val state = userSessionProvider?.sessionState?.value ?: return userId == SessionState.DEFAULT_LEGACY_USER_ID
        return when (state) {
            is SessionState.Authenticated, is SessionState.OfflineFallback -> state.currentUserId == userId
            is SessionState.Initializing, is SessionState.ReconciliationFailed -> false
        }
    }

    private fun isCurrentSession(userId: String): Boolean {
        val state = userSessionProvider?.sessionState?.value
            ?: return userId == SessionState.DEFAULT_LEGACY_USER_ID
        return when (state) {
            is SessionState.Authenticated, is SessionState.OfflineFallback -> state.currentUserId == userId
            is SessionState.Initializing, is SessionState.ReconciliationFailed -> false
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
    fun updateNewTaskDueAt(dueAt: Long?) { _newTaskDueAt.value = dueAt }

    fun createTask() {
        val planId = currentPlanId ?: return

        if (_newTaskTitle.value.isBlank()) {
            logger.logLifecycle("Task", "VALIDATION_FAILURE", mapOf("reason" to "blank_title"))
            _saveError.value = "Title cannot be empty"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Task", "CREATE_START")
            _isSaving.value = true
            _saveError.value = null
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val loadedPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan
                    if (loadedPlan?.userId != activeUid || loadedPlan.id != planId || currentPlanId != planId) {
                        return@runWithSession
                    }
                    when (val result = createTaskUseCase(
                        userId = activeUid,
                        goalId = loadedPlan.goalId,
                        planId = planId,
                        title = _newTaskTitle.value.trim(),
                        description = _newTaskDescription.value.trim(),
                        priority = _newTaskPriority.value.toInt(),
                        dueAt = _newTaskDueAt.value
                    )) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            if (result.data.userId != activeUid) {
                                _saveError.value = "Created task does not belong to this session"
                                return@runWithSession
                            }
                            logger.logLifecycle("Task", "CREATE_SUCCESS", mapOf("taskId" to result.data.id))
                            closeCreateForm()
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Task", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _saveError.value = result.error.message ?: "Failed to create task"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun commitTaskForToday(task: Task) {
        val planId = currentPlanId ?: return
        val currentState = _uiState.value
        if (currentState is PlanDetailUiState.Success && currentState.committedTaskIds.contains(task.id)) {
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Commitment", "CREATE_START")
            userSessionProvider.runWithSession { activeUid ->
                val loadedPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan
                if (loadedPlan?.userId != activeUid || loadedPlan.id != planId ||
                    task.userId != activeUid || task.planId != planId
                ) return@runWithSession
                val result = createCommitmentUseCase(
                    userId = activeUid,
                    goalId = task.goalId,
                    planId = task.planId,
                    taskId = task.id,
                    parentCommitmentId = null,
                    title = task.title,
                    description = task.description,
                    priority = task.priority,
                    initialStatus = CommitmentStatus.COMMITTED
                )

                when (result) {
                    is Result.Success -> {
                        if (!isCurrentSession(activeUid) || result.data.userId != activeUid) return@runWithSession
                        logger.logLifecycle("Commitment", "CREATE_SUCCESS", mapOf("commitmentId" to result.data.id, "taskId" to task.id))
                        loadData(planId) // Refresh to update committedTaskIds
                    }
                    is Result.Failure -> {
                        if (!isCurrentSession(activeUid)) return@runWithSession
                        logger.logLifecycle("Commitment", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                        _saveError.value = result.error.message ?: "Failed to create commitment"
                    }
                }
            }
        }
    }

    private fun clearForm() {
        _newTaskTitle.value = ""
        _newTaskDescription.value = ""
        _newTaskPriority.value = 1f
        _newTaskDueAt.value = null
        _saveError.value = null
    }

    fun openEditForm(task: Task) {
        _editingTask.value = task
        _editTaskTitle.value = task.title
        _editTaskDescription.value = task.description
        _editTaskPriority.value = task.priority.toFloat()
        _editTaskDueAt.value = task.dueAt
        _taskActionError.value = null
    }

    fun closeEditForm() {
        _editingTask.value = null
        _editTaskTitle.value = ""
        _editTaskDescription.value = ""
        _editTaskPriority.value = 1f
        _editTaskDueAt.value = null
        _taskActionError.value = null
    }

    fun updateEditTaskTitle(title: String) { _editTaskTitle.value = title }
    fun updateEditTaskDescription(desc: String) { _editTaskDescription.value = desc }
    fun updateEditTaskPriority(priority: Float) { _editTaskPriority.value = priority }
    fun updateEditTaskDueAt(dueAt: Long?) { _editTaskDueAt.value = dueAt }
    fun clearTaskActionError() { _taskActionError.value = null }

    fun saveEditedTask() {
        val task = _editingTask.value ?: return
        val planId = currentPlanId ?: return
        val useCase = editTaskUseCase ?: return

        if (_editTaskTitle.value.isBlank()) {
            logger.logLifecycle("Task", "VALIDATION_FAILURE", mapOf("reason" to "blank_title"))
            _taskActionError.value = "Title cannot be empty"
            return
        }

        if (_isPerformingTaskAction.value) return
        _isPerformingTaskAction.value = true
        _taskActionError.value = null

        viewModelScope.launch {
            logger.logLifecycle("Task", "EDIT_START", mapOf("taskId" to task.id))
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val loadedPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan
                    if (loadedPlan?.userId != activeUid || loadedPlan.id != planId || currentPlanId != planId || task.userId != activeUid) {
                        _taskActionError.value = "Task does not belong to this session"
                        return@runWithSession
                    }

                    val targetDueAt = _editTaskDueAt.value
                    val clearDue = (targetDueAt == null && task.dueAt != null)
                    val result = useCase(
                        taskId = task.id,
                        title = _editTaskTitle.value.trim(),
                        description = _editTaskDescription.value.trim(),
                        priority = _editTaskPriority.value.toInt(),
                        estimatedDurationMinutes = task.estimatedDurationMinutes,
                        scheduledAt = task.scheduledAt,
                        dueAt = targetDueAt,
                        clearDueAt = clearDue
                    )

                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            if (result.data.userId != activeUid) {
                                _taskActionError.value = "Edited task does not belong to this session"
                                return@runWithSession
                            }
                            logger.logLifecycle("Task", "EDIT_SUCCESS", mapOf("taskId" to result.data.id))
                            closeEditForm()
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Task", "EDIT_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _taskActionError.value = result.error.message ?: "Failed to edit task"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isPerformingTaskAction.value = false
            }
        }
    }

    fun updateTaskStatus(task: Task, newStatus: TaskStatus) {
        val planId = currentPlanId ?: return
        val useCase = updateTaskStatusUseCase ?: return

        if (task.status == newStatus) return
        if (_isPerformingTaskAction.value) return

        _isPerformingTaskAction.value = true
        _taskActionError.value = null

        viewModelScope.launch {
            logger.logLifecycle("Task", "STATUS_CHANGE_START", mapOf("taskId" to task.id, "from" to task.status.name, "to" to newStatus.name))
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val loadedPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan
                    if (loadedPlan?.userId != activeUid || loadedPlan.id != planId || currentPlanId != planId || task.userId != activeUid) {
                        _taskActionError.value = "Task does not belong to this session"
                        return@runWithSession
                    }

                    val result = useCase(task.id, newStatus)
                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            if (result.data.userId != activeUid) {
                                _taskActionError.value = "Updated task does not belong to this session"
                                return@runWithSession
                            }
                            logger.logLifecycle("Task", "STATUS_CHANGE_SUCCESS", mapOf("taskId" to result.data.id, "status" to result.data.status.name))
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Task", "STATUS_CHANGE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _taskActionError.value = result.error.message ?: "Failed to update task status"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isPerformingTaskAction.value = false
            }
        }
    }

    fun requestDeleteTask(task: Task) {
        _taskPendingDeletion.value = task
    }

    fun cancelDeleteTask() {
        _taskPendingDeletion.value = null
    }

    fun clearDeletionSuccessMessage() {
        _deletionSuccessMessage.value = null
    }

    fun confirmDeleteTask() {
        val task = _taskPendingDeletion.value ?: return
        val planId = currentPlanId ?: return
        val useCase = deleteTaskUseCase ?: return

        if (_isPerformingTaskAction.value) return

        _isPerformingTaskAction.value = true
        _taskActionError.value = null

        viewModelScope.launch {
            logger.logLifecycle("Task", "DELETE_START", mapOf("taskId" to task.id, "planId" to planId))
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val loadedPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan
                    if (loadedPlan?.userId != activeUid || loadedPlan.id != planId || currentPlanId != planId || task.userId != activeUid) {
                        _taskActionError.value = "Task does not belong to this session"
                        _taskPendingDeletion.value = null
                        return@runWithSession
                    }

                    val result = useCase(userId = activeUid, taskId = task.id)
                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Task", "DELETE_SUCCESS", mapOf("taskId" to task.id))
                            _taskPendingDeletion.value = null
                            _deletionSuccessMessage.value = "Task deleted successfully"
                            closeEditForm()
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Task", "DELETE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _taskPendingDeletion.value = null
                            _taskActionError.value = result.error.message ?: "Failed to delete task"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isPerformingTaskAction.value = false
            }
        }
    }

    fun openEditPlanForm() {
        val currentPlan = (_uiState.value as? PlanDetailUiState.Success)?.plan ?: return
        _editPlanTitle.value = currentPlan.title
        _editPlanDescription.value = currentPlan.description ?: ""
        _editPlanStatus.value = currentPlan.status
        _planActionError.value = null
        _showEditPlanForm.value = true
    }

    fun closeEditPlanForm() {
        _showEditPlanForm.value = false
        _planActionError.value = null
    }

    fun updateEditPlanTitle(title: String) {
        _editPlanTitle.value = title
    }

    fun updateEditPlanDescription(description: String) {
        _editPlanDescription.value = description
    }

    fun updateEditPlanStatus(status: PlanStatus) {
        _editPlanStatus.value = status
    }

    fun clearPlanActionError() {
        _planActionError.value = null
    }

    fun clearPlanUserMessage() {
        _planUserMessage.value = null
    }

    fun saveEditedPlan() {
        val planId = currentPlanId ?: return
        if (_isPerformingPlanAction.value) return

        val title = _editPlanTitle.value.trim()
        if (title.isBlank()) {
            logger.logLifecycle("Plan", "VALIDATION_FAILURE", mapOf("reason" to "blank_title"))
            _planActionError.value = "Plan title cannot be empty"
            return
        }

        val useCase = editPlanUseCase
        if (useCase == null) {
            _planActionError.value = "Plan editing is not supported"
            return
        }

        _isPerformingPlanAction.value = true
        _planActionError.value = null

        viewModelScope.launch {
            logger.logLifecycle("Plan", "EDIT_START", mapOf("planId" to planId))
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val currentSuccess = _uiState.value as? PlanDetailUiState.Success
                    val loadedPlan = currentSuccess?.plan
                    if (loadedPlan?.userId != activeUid || currentPlanId != planId) return@runWithSession

                    val result = useCase(
                        userId = activeUid,
                        planId = planId,
                        title = title,
                        description = _editPlanDescription.value.trim(),
                        status = _editPlanStatus.value
                    )

                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Plan", "EDIT_SUCCESS", mapOf("planId" to planId))
                            _uiState.value = PlanDetailUiState.Success(
                                result.data,
                                currentSuccess.tasks,
                                currentSuccess.committedTaskIds
                            )
                            _planUserMessage.value = "Plan updated successfully"
                            closeEditPlanForm()
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Plan", "EDIT_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _planActionError.value = result.error.message ?: "Failed to update plan"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _planActionError.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isPerformingPlanAction.value = false
            }
        }
    }

    fun requestCompletePlan() {
        _showCompletePlanConfirmation.value = true
    }

    fun cancelCompletePlan() {
        _showCompletePlanConfirmation.value = false
    }

    fun confirmCompletePlan() {
        _showCompletePlanConfirmation.value = false
        updatePlanStatus(PlanStatus.COMPLETED)
    }

    fun updatePlanStatus(newStatus: PlanStatus) {
        val planId = currentPlanId ?: return
        if (_isPerformingPlanAction.value) return

        _isPerformingPlanAction.value = true
        _planActionError.value = null

        viewModelScope.launch {
            logger.logLifecycle("Plan", "STATUS_CHANGE_START", mapOf("planId" to planId, "newStatus" to newStatus.name))
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val currentSuccess = _uiState.value as? PlanDetailUiState.Success
                    val loadedPlan = currentSuccess?.plan
                    if (loadedPlan?.userId != activeUid || currentPlanId != planId) return@runWithSession
                    if (loadedPlan.status == newStatus) return@runWithSession

                    val result = if (updatePlanStatusUseCase != null) {
                        updatePlanStatusUseCase.invoke(
                            userId = activeUid,
                            planId = planId,
                            newStatus = newStatus
                        )
                    } else if (editPlanUseCase != null) {
                        editPlanUseCase.invoke(
                            userId = activeUid,
                            planId = planId,
                            status = newStatus
                        )
                    } else {
                        Result.Failure(IllegalStateException("Plan status update is not supported"))
                    }

                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Plan", "STATUS_CHANGE_SUCCESS", mapOf("planId" to planId, "newStatus" to newStatus.name))
                            _uiState.value = PlanDetailUiState.Success(
                                result.data,
                                currentSuccess.tasks,
                                currentSuccess.committedTaskIds
                            )
                            val statusLabel = when (newStatus) {
                                PlanStatus.ACTIVE -> if (loadedPlan.status == PlanStatus.COMPLETED) "reactivated" else "resumed"
                                PlanStatus.PAUSED -> "paused"
                                PlanStatus.COMPLETED -> "completed"
                                else -> newStatus.name.lowercase()
                            }
                            _planUserMessage.value = "Plan $statusLabel successfully"
                            loadData(planId)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            logger.logLifecycle("Plan", "STATUS_CHANGE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _planActionError.value = result.error.message ?: "Failed to update plan status"
                            _planUserMessage.value = "Failed to update status: ${result.error.message}"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _planActionError.value = e.message ?: "An unexpected error occurred"
                _planUserMessage.value = "Failed to update status: ${e.message}"
            } finally {
                _isPerformingPlanAction.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                PlanDetailViewModel(
                    getPlanUseCase = application.container.getPlanUseCase,
                    getTasksUseCase = application.container.getTasksUseCase,
                    createTaskUseCase = application.container.createTaskUseCase,
                    getActiveCommitmentsUseCase = application.container.getActiveCommitmentsUseCase,
                    createCommitmentUseCase = application.container.createCommitmentUseCase,
                    logger = application.container.logger,
                    userSessionProvider = application.container.userSessionProvider,
                    editTaskUseCase = application.container.editTaskUseCase,
                    updateTaskStatusUseCase = application.container.updateTaskStatusUseCase,
                    deleteTaskUseCase = application.container.deleteTaskUseCase,
                    editPlanUseCase = application.container.editPlanUseCase,
                    updatePlanStatusUseCase = application.container.updatePlanStatusUseCase,
                    getPlanProgressUseCase = application.container.getPlanProgressUseCase
                )
            }
        }
    }
}
