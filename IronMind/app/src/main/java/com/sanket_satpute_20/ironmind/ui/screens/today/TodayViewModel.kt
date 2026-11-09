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
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult

import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.briefing.DailyBriefing
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.briefing.AssembleDailyBriefingUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import java.util.concurrent.ConcurrentHashMap

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Success(
        val activeCommitments: List<Commitment>,
        val pendingRecommendations: List<InterventionRecommendation> = emptyList(),
        val dueTodayTasks: List<DueTaskItem> = emptyList(),
        val overdueTasks: List<DueTaskItem> = emptyList(),
        val isGeneratingRecommendation: Boolean = false,
        val userMessage: String? = null,
        val dailyBriefing: DailyBriefing? = null,
        val proposedActions: List<ProposedAction> = emptyList(),
        val isBriefingLoading: Boolean = false
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
    private val orchestrateInterventionGenerationUseCase: OrchestrateInterventionGenerationUseCase,
    private val userSessionProvider: UserSessionProvider? = null,
    private val createCommitmentUseCase: CreateCommitmentUseCase? = null,
    private val getDueTasksUseCase: GetDueTasksUseCase? = null,
    private val assembleDailyBriefingUseCase: AssembleDailyBriefingUseCase? = null,
    private val actionLifecycleUseCase: ActionLifecycleUseCase? = null,
    private val actionRepository: ActionRepository? = null,
    private val reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // Quick Add Commitment states
    private val _showQuickAddDialog = MutableStateFlow(false)
    val showQuickAddDialog: StateFlow<Boolean> = _showQuickAddDialog.asStateFlow()

    private val _quickAddTitle = MutableStateFlow("")
    val quickAddTitle: StateFlow<String> = _quickAddTitle.asStateFlow()

    private val _quickAddDescription = MutableStateFlow("")
    val quickAddDescription: StateFlow<String> = _quickAddDescription.asStateFlow()

    private val _quickAddPriority = MutableStateFlow(1)
    val quickAddPriority: StateFlow<Int> = _quickAddPriority.asStateFlow()

    private val _quickAddScheduledStartAt = MutableStateFlow<Long?>(null)
    val quickAddScheduledStartAt: StateFlow<Long?> = _quickAddScheduledStartAt.asStateFlow()

    private val _isQuickAdding = MutableStateFlow(false)
    val isQuickAdding: StateFlow<Boolean> = _isQuickAdding.asStateFlow()

    private val _quickAddError = MutableStateFlow<String?>(null)
    val quickAddError: StateFlow<String?> = _quickAddError.asStateFlow()

    private val currentUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private val handledRecommendationIds = mutableSetOf<String>()
    private var dataLoadJob: Job? = null
    private var actionObserveJob: Job? = null
    private var loadGeneration = 0L
    private var observedSessionUserId: String? = null
    private var loadedUserId: String? = null
    private var latestObservedActions: List<ProposedAction>? = null
    private val pendingInsightReviews = ConcurrentHashMap.newKeySet<String>()

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            loadData(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadedUserId = null
                        latestObservedActions = null
                        dataLoadJob?.cancel()
                        actionObserveJob?.cancel()
                        _uiState.value = TodayUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadedUserId = null
                        latestObservedActions = null
                        dataLoadJob?.cancel()
                        actionObserveJob?.cancel()
                        _uiState.value = TodayUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                        handledRecommendationIds.clear()
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        if (observedSessionUserId != state.currentUserId) {
                            observedSessionUserId = state.currentUserId
                            handledRecommendationIds.clear()
                            loadedUserId = null
                            latestObservedActions = null
                            actionObserveJob?.cancel()
                            _uiState.value = TodayUiState.Loading
                        }
                        loadData(state.currentUserId)
                    }
                }
            }
        }
    }

    private fun loadData(userId: String, message: String? = null) {
        dataLoadJob?.cancel()
        actionObserveJob?.cancel()
        val generation = ++loadGeneration
        loadedUserId = null
        dataLoadJob = viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            try {
                val (activeUid, commitmentsResult) = userSessionProvider.runWithSession { activeUid ->
                    activeUid to getActiveCommitmentsUseCase(activeUid)
                }
                if (activeUid != userId || !isCurrentLoad(userId, generation)) return@launch
                when (commitmentsResult) {
                    is Result.Failure -> _uiState.value = TodayUiState.Error(
                        commitmentsResult.error.message ?: "Failed to load commitments"
                    )
                    is Result.Success -> {
                        val commitments = commitmentsResult.data
                        if (commitments.any { it.userId != activeUid }) {
                            _uiState.value = TodayUiState.Error("Commitments do not belong to this session")
                            return@launch
                        }
                        loadedUserId = activeUid

                        if (actionRepository != null) {
                            actionObserveJob?.cancel()
                            actionObserveJob = viewModelScope.launch {
                                actionRepository.observeActionsForUser(activeUid)
                                    .catch { e ->
                                        if (e is CancellationException) throw e
                                    }
                                    .collect { actions ->
                                        if (!isCurrentLoad(userId, generation) || !isCurrentSession(userId)) return@collect
                                        if (actions.any { it.userId != activeUid }) return@collect

                                        val now = System.currentTimeMillis()
                                        val visibleActions = actions.filter { action ->
                                            val isNotExpired = action.expiresAt == null || action.expiresAt > now
                                            val isNotDeferred = action.deferredUntilMs == null || action.deferredUntilMs <= now
                                            (action.reviewStatus == ActionReviewStatus.PROPOSED && isNotDeferred && isNotExpired) ||
                                            action.reviewStatus == ActionReviewStatus.ACCEPTED ||
                                            action.reviewStatus == ActionReviewStatus.EXECUTING ||
                                            (action.reviewStatus == ActionReviewStatus.EXECUTED && action.outcomeId == null) ||
                                            action.reviewStatus == ActionReviewStatus.EXECUTION_FAILED
                                        }

                                        latestObservedActions = visibleActions
                                        val currentState = _uiState.value
                                        if (currentState is TodayUiState.Success) {
                                            _uiState.value = currentState.copy(proposedActions = visibleActions)
                                        }
                                    }
                            }
                        }

                        val dueTasksResult = getDueTasksUseCase?.invoke(activeUid)
                        val dueToday = if (dueTasksResult is Result.Success) dueTasksResult.data.dueToday else emptyList()
                        val overdue = if (dueTasksResult is Result.Success) dueTasksResult.data.overdue else emptyList()

                        // Collect outside the barrier. The session observer cancels this job on UID
                        // changes, and the generation check rejects any late emission.
                        getPendingInterventionRecommendationsUseCase(activeUid, System.currentTimeMillis())
                            .catch { e ->
                                if (e is CancellationException) throw e
                                if (isCurrentLoad(userId, generation)) {
                                    _uiState.value = TodayUiState.Error(e.message ?: "Failed to load recommendations")
                                }
                            }
                            .collect { recommendations ->
                                if (!isCurrentLoad(userId, generation)) return@collect
                                if (recommendations.any { it.userId != activeUid }) {
                                    _uiState.value = TodayUiState.Error("Recommendations do not belong to this session")
                                    return@collect
                                }
                                val visibleRecommendations = recommendations.filter { it.id !in handledRecommendationIds }
                                val currentState = _uiState.value
                                if (currentState is TodayUiState.Success) {
                                    _uiState.value = currentState.copy(
                                        activeCommitments = commitments,
                                        pendingRecommendations = visibleRecommendations,
                                        dueTodayTasks = dueToday,
                                        overdueTasks = overdue,
                                        proposedActions = latestObservedActions ?: currentState.proposedActions,
                                        userMessage = message ?: currentState.userMessage
                                    )
                                } else {
                                    _uiState.value = TodayUiState.Success(
                                        activeCommitments = commitments,
                                        pendingRecommendations = visibleRecommendations,
                                        dueTodayTasks = dueToday,
                                        overdueTasks = overdue,
                                        proposedActions = latestObservedActions ?: emptyList(),
                                        userMessage = message
                                    )
                                }
                                // Auto-assemble briefing when data loads
                                refreshDailyBriefing()
                            }
                }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = TodayUiState.Error(e.message ?: "Failed to load commitments")
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

    private fun hasLoadedUser(userId: String): Boolean = loadedUserId == userId && isCurrentSession(userId)

    private fun isCurrentSession(userId: String): Boolean {
        val state = userSessionProvider?.sessionState?.value
            ?: return userId == SessionState.DEFAULT_LEGACY_USER_ID
        return when (state) {
            is SessionState.Authenticated, is SessionState.OfflineFallback -> state.currentUserId == userId
            is SessionState.Initializing, is SessionState.ReconciliationFailed -> false
        }
    }

    fun loadCommitments() {
        loadData(currentUserId)
    }

    fun updateCommitmentStatus(
        commitmentId: String, 
        newStatus: CommitmentStatus, 
        resultStatus: ResultStatus? = null,
        actualDurationMinutes: Int? = null
    ) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                val currentState = _uiState.value as? TodayUiState.Success
                if (!hasLoadedUser(activeUid) || currentState == null || currentState.activeCommitments.none {
                        it.id == commitmentId && it.userId == activeUid
                    }
                ) return@runWithSession
                when (val result = updateCommitmentStatusUseCase(commitmentId, newStatus, resultStatus, actualDurationMinutes)) {
                    is Result.Success -> loadData(activeUid)
                    is Result.Failure -> _uiState.value = TodayUiState.Error(
                        result.error.message ?: "Failed to update commitment"
                    )
                }
            }
        }
    }

    fun scheduleCommitment(commitmentId: String, timeInMillis: Long) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                val currentState = _uiState.value as? TodayUiState.Success
                if (!hasLoadedUser(activeUid) || currentState == null || currentState.activeCommitments.none {
                        it.id == commitmentId && it.userId == activeUid
                    }
                ) return@runWithSession
                when (val result = scheduleCommitmentUseCase(activeUid, commitmentId, timeInMillis)) {
                    is Result.Success -> loadData(activeUid)
                    is Result.Failure -> {
                        _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to schedule reminder")
                    }
                }
            }
        }
    }

    fun cancelSchedule(commitmentId: String) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                val currentState = _uiState.value as? TodayUiState.Success
                if (!hasLoadedUser(activeUid) || currentState == null || currentState.activeCommitments.none {
                        it.id == commitmentId && it.userId == activeUid
                    }
                ) return@runWithSession
                when (val result = cancelCommitmentScheduleUseCase(activeUid, commitmentId)) {
                    is Result.Success -> loadData(activeUid)
                    is Result.Failure -> {
                        _uiState.value = TodayUiState.Error(result.error.message ?: "Failed to cancel schedule")
                    }
                }
            }
        }
    }

    fun handleInterventionResponse(
        recommendation: InterventionRecommendation,
        action: HandleInterventionResultUseCase.Action,
        correctedText: String?,
        scope: CorrectionScope = CorrectionScope.INTERVENTION_CORRECTION
    ) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || recommendation.userId != activeUid) return@runWithSession
                val result = handleInterventionResultUseCase(
                    recommendation = recommendation,
                    action = action,
                    userId = activeUid,
                    correctedText = correctedText,
                    scope = scope
                )
                val currentState = _uiState.value
                if (currentState is TodayUiState.Success) {
                    when (result) {
                        is Result.Success -> {
                            handledRecommendationIds.add(recommendation.id)
                            val updatedPending = currentState.pendingRecommendations.filter { it.id !in handledRecommendationIds }
                            val feedbackMsg = when (action) {
                                HandleInterventionResultUseCase.Action.CORRECT -> "Correction recorded"
                                HandleInterventionResultUseCase.Action.ACCEPT -> "Suggestion accepted"
                                HandleInterventionResultUseCase.Action.REJECT -> "Suggestion dismissed"
                                HandleInterventionResultUseCase.Action.IGNORE -> "Suggestion ignored"
                            }
                            _uiState.value = currentState.copy(
                                pendingRecommendations = updatedPending,
                                userMessage = feedbackMsg
                            )
                        }
                        is Result.Failure -> {
                            _uiState.value = currentState.copy(
                                userMessage = "Failed to record response: ${result.error.message ?: "Unknown error"}"
                            )
                        }
                    }
                }
            }
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
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid)) {
                    val state = _uiState.value as? TodayUiState.Success
                    if (state != null) _uiState.value = state.copy(isGeneratingRecommendation = false)
                    return@runWithSession
                }
                val result = orchestrateInterventionGenerationUseCase(activeUid)
                
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
    }

    fun refreshDailyBriefing() {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid)) return@runWithSession
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                _uiState.value = currentState.copy(isBriefingLoading = true)

                val safeExisting = currentState.proposedActions.filter { it.userId == activeUid }
                val briefingResult = assembleDailyBriefingUseCase?.invoke(
                    userId = activeUid,
                    existingActions = safeExisting
                )

                val updatedState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (briefingResult is Result.Success) {
                    val actions = if (actionRepository == null) {
                        briefingResult.data.proposedNextActions
                    } else {
                        // Room-backed repository emissions are the authoritative source for persisted actions
                        if (updatedState.proposedActions.isEmpty()) {
                            briefingResult.data.proposedNextActions
                        } else {
                            updatedState.proposedActions
                        }
                    }
                    _uiState.value = updatedState.copy(
                        dailyBriefing = briefingResult.data,
                        proposedActions = actions,
                        isBriefingLoading = false
                    )
                } else {
                    _uiState.value = updatedState.copy(isBriefingLoading = false)
                }
            }
        }
    }

    fun acceptAction(action: ProposedAction) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                val result = actionLifecycleUseCase?.acceptAction(action, activeUid)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.map {
                            if (it.id == action.id) result.data else it
                        }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = "Action approved: ready for execution"
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = "Action approved: ready for execution"
                        )
                    }
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Failed to accept action"
                    )
                }
            }
        }
    }

    fun rejectAction(action: ProposedAction, reason: String? = null) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                val result = actionLifecycleUseCase?.rejectAction(action, activeUid, reason)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.filter { it.id != action.id }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = "Action rejected"
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = "Action rejected"
                        )
                    }
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Failed to reject action"
                    )
                }
            }
        }
    }

    fun dismissAction(action: ProposedAction) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                val result = actionLifecycleUseCase?.dismissAction(action, activeUid)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.filter { it.id != action.id }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = "Action dismissed"
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = "Action dismissed"
                        )
                    }
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Failed to dismiss action"
                    )
                }
            }
        }
    }

    fun deferAction(action: ProposedAction, deferUntilMs: Long) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                val result = actionLifecycleUseCase?.deferAction(action, activeUid, deferUntilMs)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.map {
                            if (it.id == action.id) result.data else it
                        }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = "Action deferred"
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = "Action deferred"
                        )
                    }
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Failed to defer action"
                    )
                }
            }
        }
    }

    fun executeAction(action: ProposedAction, params: Map<String, Any?> = emptyMap()) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                if (action.reviewStatus != ActionReviewStatus.ACCEPTED) {
                    val currentState = _uiState.value as? TodayUiState.Success
                    if (currentState != null) {
                        _uiState.value = currentState.copy(userMessage = "Action must be in ACCEPTED state before execution")
                    }
                    return@runWithSession
                }
                val result = actionLifecycleUseCase?.executeAction(action, activeUid, params)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    val executedAction = result.data
                    val execMessage = executedAction.executionResult?.message ?: "Action executed"
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.map {
                            if (it.id == action.id) executedAction else it
                        }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = execMessage
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = execMessage
                        )
                    }
                    loadData(activeUid, message = execMessage)
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Execution failed"
                    )
                }
            }
        }
    }

    fun recordActionOutcome(
        action: ProposedAction,
        resultStatus: ResultStatus,
        actualDurationMinutes: Int? = null,
        feedback: String? = null
    ) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (!hasLoadedUser(activeUid) || action.userId != activeUid) return@runWithSession
                val result = actionLifecycleUseCase?.recordActionOutcome(action, activeUid, resultStatus, actualDurationMinutes, feedback)
                val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                if (result is Result.Success) {
                    if (actionRepository == null) {
                        val updatedActions = currentState.proposedActions.filter { it.id != action.id }
                        _uiState.value = currentState.copy(
                            proposedActions = updatedActions,
                            userMessage = "Outcome recorded"
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            userMessage = "Outcome recorded"
                        )
                    }
                } else if (result is Result.Failure) {
                    _uiState.value = currentState.copy(
                        userMessage = result.error.message ?: "Failed to record outcome"
                    )
                }
            }
        }
    }

    fun markInsightUseful(insightId: String) {
        if (reviewPersonalInsightUseCase == null) return
        if (!pendingInsightReviews.add(insightId)) return
        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (!hasLoadedUser(activeUid)) return@runWithSession
                    val result = reviewPersonalInsightUseCase.markUseful(insightId, activeUid)
                    val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                    when (result) {
                        is Result.Success -> {
                            _uiState.value = currentState.copy(userMessage = "Insight marked as useful")
                            refreshDailyBriefing()
                        }
                        is Result.Failure -> {
                            _uiState.value = currentState.copy(
                                userMessage = result.error.message ?: "Failed to mark insight as useful"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentState = _uiState.value as? TodayUiState.Success
                if (currentState != null) {
                    _uiState.value = currentState.copy(userMessage = e.message ?: "Error updating insight")
                }
            } finally {
                pendingInsightReviews.remove(insightId)
            }
        }
    }

    fun dismissInsight(insightId: String) {
        if (reviewPersonalInsightUseCase == null) return
        if (!pendingInsightReviews.add(insightId)) return
        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (!hasLoadedUser(activeUid)) return@runWithSession
                    val result = reviewPersonalInsightUseCase.dismiss(insightId, activeUid)
                    val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                    when (result) {
                        is Result.Success -> {
                            _uiState.value = currentState.copy(userMessage = "Insight dismissed")
                            refreshDailyBriefing()
                        }
                        is Result.Failure -> {
                            _uiState.value = currentState.copy(
                                userMessage = result.error.message ?: "Failed to dismiss insight"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentState = _uiState.value as? TodayUiState.Success
                if (currentState != null) {
                    _uiState.value = currentState.copy(userMessage = e.message ?: "Error updating insight")
                }
            } finally {
                pendingInsightReviews.remove(insightId)
            }
        }
    }

    fun reviewInsightLater(insightId: String) {
        if (reviewPersonalInsightUseCase == null) return
        if (!pendingInsightReviews.add(insightId)) return
        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (!hasLoadedUser(activeUid)) return@runWithSession
                    val result = reviewPersonalInsightUseCase.reviewLater(insightId, activeUid)
                    val currentState = _uiState.value as? TodayUiState.Success ?: return@runWithSession
                    when (result) {
                        is Result.Success -> {
                            _uiState.value = currentState.copy(userMessage = "Insight postponed for later review")
                            refreshDailyBriefing()
                        }
                        is Result.Failure -> {
                            _uiState.value = currentState.copy(
                                userMessage = result.error.message ?: "Failed to postpone insight"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentState = _uiState.value as? TodayUiState.Success
                if (currentState != null) {
                    _uiState.value = currentState.copy(userMessage = e.message ?: "Error updating insight")
                }
            } finally {
                pendingInsightReviews.remove(insightId)
            }
        }
    }

    fun openQuickAdd() {
        _quickAddTitle.value = ""
        _quickAddDescription.value = ""
        _quickAddPriority.value = 1
        _quickAddScheduledStartAt.value = null
        _quickAddError.value = null
        _showQuickAddDialog.value = true
    }

    fun closeQuickAdd() {
        _showQuickAddDialog.value = false
        _quickAddError.value = null
    }

    fun updateQuickAddTitle(title: String) {
        _quickAddTitle.value = title
    }

    fun updateQuickAddDescription(description: String) {
        _quickAddDescription.value = description
    }

    fun updateQuickAddPriority(priority: Int) {
        _quickAddPriority.value = priority
    }

    fun updateQuickAddScheduledStartAt(time: Long?) {
        _quickAddScheduledStartAt.value = time
    }

    fun clearQuickAddError() {
        _quickAddError.value = null
    }

    fun saveQuickAddCommitment() {
        if (_isQuickAdding.value) return

        val title = _quickAddTitle.value.trim()
        if (title.isBlank()) {
            _quickAddError.value = "Title cannot be empty"
            return
        }

        val useCase = createCommitmentUseCase
        if (useCase == null) {
            _quickAddError.value = "Commitment creation not supported"
            return
        }

        _isQuickAdding.value = true
        _quickAddError.value = null

        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val result = useCase(
                        userId = activeUid,
                        goalId = null,
                        planId = null,
                        taskId = null,
                        parentCommitmentId = null,
                        title = title,
                        description = _quickAddDescription.value.trim(),
                        priority = _quickAddPriority.value,
                        scheduledStartAt = _quickAddScheduledStartAt.value,
                        scheduledEndAt = null,
                        initialStatus = CommitmentStatus.COMMITTED
                    )
                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            _showQuickAddDialog.value = false
                            loadData(activeUid, message = "Commitment added for today")
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            _quickAddError.value = result.error.message ?: "Failed to add commitment"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _quickAddError.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isQuickAdding.value = false
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
                    orchestrateInterventionGenerationUseCase = container.orchestrateInterventionGenerationUseCase,
                    userSessionProvider = container.userSessionProvider,
                    createCommitmentUseCase = container.createCommitmentUseCase,
                    getDueTasksUseCase = container.getDueTasksUseCase,
                    assembleDailyBriefingUseCase = container.assembleDailyBriefingUseCase,
                    actionLifecycleUseCase = container.actionLifecycleUseCase,
                    actionRepository = container.actionRepository,
                    reviewPersonalInsightUseCase = container.reviewPersonalInsightUseCase
                )
            }
        }
    }
}
