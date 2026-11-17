package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ObservePersonalInsightsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.OrchestratePersonalIntelligenceUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

sealed interface InsightReviewUiState {
    data object Loading : InsightReviewUiState
    data class Success(
        val insights: List<PersonalInsight>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
        val isOrchestrating: Boolean = false
    ) : InsightReviewUiState
    data class Error(val message: String) : InsightReviewUiState
}

class InsightReviewViewModel(
    private val observePersonalInsightsUseCase: ObservePersonalInsightsUseCase,
    private val reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase,
    private val orchestratePersonalIntelligenceUseCase: OrchestratePersonalIntelligenceUseCase? = null,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<InsightReviewUiState>(InsightReviewUiState.Loading)
    val uiState: StateFlow<InsightReviewUiState> = _uiState.asStateFlow()

    private val isActionInProgress = AtomicBoolean(false)
    private var observeJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            observeInsights(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { sessionState ->
                when (sessionState) {
                    is SessionState.Initializing -> {
                        observeJob?.cancel()
                        _uiState.value = InsightReviewUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observeJob?.cancel()
                        _uiState.value = InsightReviewUiState.Error(
                            "Session reconciliation failed: ${sessionState.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        observeInsights(sessionState.currentUserId)
                    }
                }
            }
        }
    }

    fun observeInsights(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = InsightReviewUiState.Loading
            try {
                observePersonalInsightsUseCase(userId).collect { insights ->
                    val currentState = _uiState.value
                    val isSubmitting = if (currentState is InsightReviewUiState.Success) currentState.isSubmitting else false
                    val isOrchestrating = if (currentState is InsightReviewUiState.Success) currentState.isOrchestrating else false
                    _uiState.value = InsightReviewUiState.Success(
                        insights = insights,
                        isSubmitting = isSubmitting,
                        submitError = null,
                        isOrchestrating = isOrchestrating
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = InsightReviewUiState.Error(e.message ?: "Failed to load insights")
            }
        }
    }

    fun markUseful(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.markUseful(insightId, activeUserId)
        }
    }

    fun dismiss(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.dismiss(insightId, activeUserId)
        }
    }

    fun reviewLater(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.reviewLater(insightId, activeUserId)
        }
    }

    fun provideFeedback(insightId: String, feedback: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.provideFeedback(insightId, activeUserId, feedback)
        }
    }

    fun acceptRecommendation(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.acceptLinkedRecommendation(insightId, activeUserId)
        }
    }

    fun rejectRecommendation(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.rejectLinkedRecommendation(insightId, activeUserId)
        }
    }

    fun confirmFactualClaim(insightId: String) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.confirmFactualClaim(insightId, activeUserId)
        }
    }

    fun refuteFactualClaim(insightId: String, correction: String? = null) {
        executeInsightAction { activeUserId ->
            reviewPersonalInsightUseCase.refuteFactualClaim(insightId, activeUserId, correction)
        }
    }

    fun runIntelligenceOrchestration(zoneId: ZoneId = ZoneId.systemDefault()) {
        if (orchestratePersonalIntelligenceUseCase == null) return
        val currentState = _uiState.value
        if (currentState is InsightReviewUiState.Success) {
            _uiState.value = currentState.copy(isOrchestrating = true)
        }

        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUserId ->
                    val now = System.currentTimeMillis()
                    val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
                    orchestratePersonalIntelligenceUseCase(
                        userId = activeUserId,
                        windowStartTimeMs = sevenDaysAgo,
                        windowEndTimeMs = now,
                        zoneId = zoneId
                    )
                }
            } catch (e: Exception) {
                // Ignore or log error
            } finally {
                val state = _uiState.value
                if (state is InsightReviewUiState.Success) {
                    _uiState.value = state.copy(isOrchestrating = false)
                }
            }
        }
    }

    private fun executeInsightAction(
        action: suspend (activeUserId: String) -> Result<Unit, Exception>
    ) {
        if (!isActionInProgress.compareAndSet(false, true)) {
            return
        }

        val currentState = _uiState.value
        if (currentState is InsightReviewUiState.Success) {
            _uiState.value = currentState.copy(isSubmitting = true, submitError = null)
        }

        viewModelScope.launch {
            try {
                val actionResult = userSessionProvider.runWithSession { activeUserId ->
                    action(activeUserId)
                }
                when (actionResult) {
                    is Result.Success -> {
                        val state = _uiState.value
                        if (state is InsightReviewUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = null)
                        }
                    }
                    is Result.Failure -> {
                        val state = _uiState.value
                        if (state is InsightReviewUiState.Success) {
                            _uiState.value = state.copy(
                                isSubmitting = false,
                                submitError = actionResult.error.message ?: "Action failed"
                            )
                        } else {
                            _uiState.value = InsightReviewUiState.Error(
                                actionResult.error.message ?: "Action failed"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val state = _uiState.value
                if (state is InsightReviewUiState.Success) {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "Operation failed"
                    )
                } else {
                    _uiState.value = InsightReviewUiState.Error(e.message ?: "Operation failed")
                }
            } finally {
                isActionInProgress.set(false)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                InsightReviewViewModel(
                    observePersonalInsightsUseCase = container.observePersonalInsightsUseCase,
                    reviewPersonalInsightUseCase = container.reviewPersonalInsightUseCase,
                    orchestratePersonalIntelligenceUseCase = container.orchestratePersonalIntelligenceUseCase,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
