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
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.UpdateGoalStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GoalDetailUiState {
    object Loading : GoalDetailUiState()
    data class Success(
        val goal: Goal,
        val plans: List<Plan>,
        val goalProgress: ProgressInfo = ProgressInfo.ZERO,
        val planProgressMap: Map<String, ProgressInfo> = emptyMap()
    ) : GoalDetailUiState()
    data class Error(val message: String) : GoalDetailUiState()
}

class GoalDetailViewModel(
    private val getGoalUseCase: GetGoalUseCase,
    private val getPlansUseCase: GetPlansUseCase,
    private val createPlanUseCase: CreatePlanUseCase,
    private val logger: IronLogger,
    private val userSessionProvider: UserSessionProvider? = null,
    private val editGoalUseCase: EditGoalUseCase? = null,
    private val updateGoalStatusUseCase: UpdateGoalStatusUseCase? = null,
    private val getGoalProgressUseCase: GetGoalProgressUseCase? = null,
    private val getPlanProgressUseCase: GetPlanProgressUseCase? = null
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

    // Form states for Goal Edit
    private val _showEditForm = MutableStateFlow(false)
    val showEditForm: StateFlow<Boolean> = _showEditForm.asStateFlow()

    private val _editGoalTitle = MutableStateFlow("")
    val editGoalTitle: StateFlow<String> = _editGoalTitle.asStateFlow()

    private val _editGoalDescription = MutableStateFlow("")
    val editGoalDescription: StateFlow<String> = _editGoalDescription.asStateFlow()

    private val _editGoalWhy = MutableStateFlow("")
    val editGoalWhy: StateFlow<String> = _editGoalWhy.asStateFlow()

    private val _editGoalImportance = MutableStateFlow(5f)
    val editGoalImportance: StateFlow<Float> = _editGoalImportance.asStateFlow()

    private val _editGoalStatus = MutableStateFlow(GoalStatus.ACTIVE)
    val editGoalStatus: StateFlow<GoalStatus> = _editGoalStatus.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val defaultUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID
    private var currentGoalId: String? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) return

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = GoalDetailUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = GoalDetailUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        val goalId = currentGoalId
                        if (goalId != null) {
                            loadData(goalId, state.currentUserId)
                        }
                    }
                }
            }
        }
    }

    fun loadData(goalId: String, userId: String = defaultUserId) {
        currentGoalId = goalId
        logger.logLifecycle("Plan", "LOAD_START", mapOf("goalId" to goalId))
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _uiState.value = GoalDetailUiState.Loading
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession
                    val goalResult = getGoalUseCase(goalId)
                    if (!isCurrentLoad(userId, generation)) return@runWithSession
                    if (goalResult is Result.Failure) {
                        _uiState.value = GoalDetailUiState.Error(goalResult.error.message ?: "Failed to load goal")
                        return@runWithSession
                    }

                    val goal = (goalResult as Result.Success).data
                    if (goal == null || goal.userId != activeUid) {
                        _uiState.value = GoalDetailUiState.Error("Goal not found for this session")
                        return@runWithSession
                    }

                    val plansResult = getPlansUseCase(goalId)
                    if (!isCurrentLoad(userId, generation)) return@runWithSession
                    when (plansResult) {
                        is Result.Success -> if (plansResult.data.any { it.userId != activeUid }) {
                            _uiState.value = GoalDetailUiState.Error("Goal plans do not belong to this session")
                        } else {
                            val plans = plansResult.data
                            val goalProgress = getGoalProgressUseCase?.invoke(goalId, activeUid)?.let {
                                if (it is Result.Success) it.data else ProgressInfo.ZERO
                            } ?: ProgressInfo.ZERO

                            val planProgressMap = plans.associate { plan ->
                                val progress = getPlanProgressUseCase?.invoke(plan.id, activeUid)?.let {
                                    if (it is Result.Success) it.data else ProgressInfo.ZERO
                                } ?: ProgressInfo.ZERO
                                plan.id to progress
                            }

                            logger.logLifecycle("Plan", "LOAD_SUCCESS", mapOf("plansCount" to plans.size))
                            _uiState.value = GoalDetailUiState.Success(
                                goal = goal,
                                plans = plans,
                                goalProgress = goalProgress,
                                planProgressMap = planProgressMap
                            )
                        }
                        is Result.Failure -> {
                            logger.logLifecycle("Plan", "LOAD_FAILURE", mapOf("reason" to (plansResult.error.message ?: "Unknown")))
                            _uiState.value = GoalDetailUiState.Error(plansResult.error.message ?: "Failed to load plans")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = GoalDetailUiState.Error(e.message ?: "Failed to load goal")
                }
            }
        }
    }

    private fun isCurrentLoad(userId: String, generation: Long): Boolean {
        if (generation != loadGeneration) return false
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

    fun updateNewPlanTitle(title: String) { _newPlanTitle.value = title }
    fun updateNewPlanDescription(desc: String) { _newPlanDescription.value = desc }

    fun createPlan() {
        val goalId = currentGoalId ?: return

        if (_newPlanTitle.value.isBlank()) {
            logger.logLifecycle("Plan", "VALIDATION_FAILURE", mapOf("reason" to "blank_title"))
            _saveError.value = "Title cannot be empty"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Plan", "CREATE_START")
            _isSaving.value = true
            _saveError.value = null

            try {
                userSessionProvider.runWithSession { activeUid ->
                    val loadedGoal = (_uiState.value as? GoalDetailUiState.Success)?.goal
                    if (loadedGoal?.userId != activeUid || currentGoalId != goalId) return@runWithSession
                    when (val result = createPlanUseCase(
                        userId = activeUid,
                        goalId = goalId,
                        title = _newPlanTitle.value.trim(),
                        description = _newPlanDescription.value.trim()
                    )) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            _isSaving.value = false
                            logger.logLifecycle("Plan", "CREATE_SUCCESS", mapOf("planId" to result.data.id))
                            closeCreateForm()
                            loadData(goalId, activeUid)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            _isSaving.value = false
                            logger.logLifecycle("Plan", "CREATE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _saveError.value = result.error.message ?: "Failed to create plan"
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

    private fun isCurrentSession(userId: String): Boolean {
        val state = userSessionProvider?.sessionState?.value
            ?: return userId == SessionState.DEFAULT_LEGACY_USER_ID
        return when (state) {
            is SessionState.Authenticated, is SessionState.OfflineFallback -> state.currentUserId == userId
            is SessionState.Initializing, is SessionState.ReconciliationFailed -> false
        }
    }

    private fun clearForm() {
        _newPlanTitle.value = ""
        _newPlanDescription.value = ""
        _saveError.value = null
    }

    fun openEditForm() {
        val currentGoal = (_uiState.value as? GoalDetailUiState.Success)?.goal ?: return
        _editGoalTitle.value = currentGoal.title
        _editGoalDescription.value = currentGoal.description
        _editGoalWhy.value = currentGoal.why
        _editGoalImportance.value = currentGoal.importance.toFloat().coerceIn(1f, 10f)
        _editGoalStatus.value = currentGoal.status
        _editError.value = null
        _showEditForm.value = true
    }

    fun closeEditForm() {
        _showEditForm.value = false
        _editError.value = null
    }

    fun updateEditGoalTitle(title: String) { _editGoalTitle.value = title }
    fun updateEditGoalDescription(desc: String) { _editGoalDescription.value = desc }
    fun updateEditGoalWhy(why: String) { _editGoalWhy.value = why }
    fun updateEditGoalImportance(importance: Float) { _editGoalImportance.value = importance }
    fun updateEditGoalStatus(status: GoalStatus) { _editGoalStatus.value = status }

    fun clearUserMessage() { _userMessage.value = null }

    fun saveGoalEdit() {
        val goalId = currentGoalId ?: return
        if (_isEditing.value) return

        val title = _editGoalTitle.value.trim()
        if (title.isBlank()) {
            logger.logLifecycle("Goal", "VALIDATION_FAILURE", mapOf("reason" to "blank_title"))
            _editError.value = "Title cannot be empty"
            return
        }

        val editUseCase = editGoalUseCase
        if (editUseCase == null) {
            _editError.value = "Goal editing is not supported"
            return
        }

        viewModelScope.launch {
            logger.logLifecycle("Goal", "EDIT_START", mapOf("goalId" to goalId))
            _isEditing.value = true
            _editError.value = null

            try {
                userSessionProvider.runWithSession { activeUid ->
                    val currentSuccess = _uiState.value as? GoalDetailUiState.Success
                    val loadedGoal = currentSuccess?.goal
                    if (loadedGoal?.userId != activeUid || currentGoalId != goalId) return@runWithSession

                    val result = editUseCase(
                        userId = activeUid,
                        goalId = goalId,
                        title = title,
                        description = _editGoalDescription.value.trim(),
                        why = _editGoalWhy.value.trim(),
                        importance = _editGoalImportance.value.toInt(),
                        targetAt = null,
                        status = _editGoalStatus.value
                    )

                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            _isEditing.value = false
                            logger.logLifecycle("Goal", "EDIT_SUCCESS", mapOf("goalId" to goalId))
                            _uiState.value = GoalDetailUiState.Success(result.data, currentSuccess.plans)
                            _userMessage.value = "Goal updated successfully"
                            closeEditForm()
                            loadData(goalId, activeUid)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            _isEditing.value = false
                            logger.logLifecycle("Goal", "EDIT_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _editError.value = result.error.message ?: "Failed to update goal"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _editError.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isEditing.value = false
            }
        }
    }

    fun updateStatus(newStatus: GoalStatus) {
        val goalId = currentGoalId ?: return
        if (_isEditing.value) return

        viewModelScope.launch {
            logger.logLifecycle("Goal", "STATUS_CHANGE_START", mapOf("goalId" to goalId, "newStatus" to newStatus.name))
            _isEditing.value = true
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val currentSuccess = _uiState.value as? GoalDetailUiState.Success
                    val loadedGoal = currentSuccess?.goal
                    if (loadedGoal?.userId != activeUid || currentGoalId != goalId) return@runWithSession
                    if (loadedGoal.status == newStatus) return@runWithSession

                    val result = if (editGoalUseCase != null) {
                        editGoalUseCase.invoke(
                            userId = activeUid,
                            goalId = goalId,
                            title = null,
                            description = null,
                            why = null,
                            importance = null,
                            targetAt = null,
                            status = newStatus
                        )
                    } else if (updateGoalStatusUseCase != null) {
                        updateGoalStatusUseCase.invoke(goalId, newStatus)
                    } else {
                        Result.Failure(IllegalStateException("Status update is not supported"))
                    }

                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            _isEditing.value = false
                            logger.logLifecycle("Goal", "STATUS_CHANGE_SUCCESS", mapOf("goalId" to goalId, "newStatus" to newStatus.name))
                            _uiState.value = GoalDetailUiState.Success(result.data, currentSuccess.plans)
                            _userMessage.value = "Goal status updated to ${newStatus.name}"
                            loadData(goalId, activeUid)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            _isEditing.value = false
                            logger.logLifecycle("Goal", "STATUS_CHANGE_FAILURE", mapOf("reason" to (result.error.message ?: "Unknown error")))
                            _userMessage.value = "Failed to update status: ${result.error.message}"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _userMessage.value = "Failed to update status: ${e.message}"
            } finally {
                _isEditing.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                GoalDetailViewModel(
                    getGoalUseCase = application.container.getGoalUseCase,
                    getPlansUseCase = application.container.getPlansUseCase,
                    createPlanUseCase = application.container.createPlanUseCase,
                    logger = application.container.logger,
                    userSessionProvider = application.container.userSessionProvider,
                    editGoalUseCase = application.container.editGoalUseCase,
                    updateGoalStatusUseCase = application.container.updateGoalStatusUseCase,
                    getGoalProgressUseCase = application.container.getGoalProgressUseCase,
                    getPlanProgressUseCase = application.container.getPlanProgressUseCase
                )
            }
        }
    }
}
