package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.ConfirmPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.DismissPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.GetPatternsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.RefutePatternUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface PatternReviewUiState {
    data object Loading : PatternReviewUiState
    data class Success(
        val patterns: List<Pattern>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : PatternReviewUiState
    data class Error(val message: String) : PatternReviewUiState
}

class PatternReviewViewModel(
    private val getPatternsUseCase: GetPatternsUseCase,
    private val confirmPatternUseCase: ConfirmPatternUseCase,
    private val refutePatternUseCase: RefutePatternUseCase,
    private val dismissPatternUseCase: DismissPatternUseCase,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<PatternReviewUiState>(PatternReviewUiState.Loading)
    val uiState: StateFlow<PatternReviewUiState> = _uiState.asStateFlow()

    private val isActionInProgress = AtomicBoolean(false)
    private var observeJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            observePatterns(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        observeJob?.cancel()
                        _uiState.value = PatternReviewUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observeJob?.cancel()
                        _uiState.value = PatternReviewUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        observePatterns(state.currentUserId)
                    }
                }
            }
        }
    }

    fun observePatterns(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = PatternReviewUiState.Loading
            try {
                getPatternsUseCase.observe(userId).collect { patterns ->
                    val currentState = _uiState.value
                    val isSubmitting = if (currentState is PatternReviewUiState.Success) currentState.isSubmitting else false
                    _uiState.value = PatternReviewUiState.Success(
                        patterns = patterns,
                        isSubmitting = isSubmitting,
                        submitError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PatternReviewUiState.Error(e.message ?: "Failed to load patterns")
            }
        }
    }

    fun confirm(patternId: String) {
        executePatternAction { activeUserId ->
            confirmPatternUseCase(activeUserId, patternId)
        }
    }

    fun refute(patternId: String) {
        executePatternAction { activeUserId ->
            refutePatternUseCase(activeUserId, patternId)
        }
    }

    fun dismiss(patternId: String) {
        executePatternAction { activeUserId ->
            dismissPatternUseCase(activeUserId, patternId)
        }
    }

    private fun executePatternAction(
        action: suspend (activeUserId: String) -> Result<Unit, Exception>
    ) {
        if (!isActionInProgress.compareAndSet(false, true)) {
            return
        }

        val currentState = _uiState.value
        if (currentState is PatternReviewUiState.Success) {
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
                        if (state is PatternReviewUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = null)
                        }
                    }
                    is Result.Failure -> {
                        val state = _uiState.value
                        if (state is PatternReviewUiState.Success) {
                            _uiState.value = state.copy(
                                isSubmitting = false,
                                submitError = actionResult.error.message ?: "Action failed"
                            )
                        } else {
                            _uiState.value = PatternReviewUiState.Error(
                                actionResult.error.message ?: "Action failed"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val state = _uiState.value
                if (state is PatternReviewUiState.Success) {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "Operation failed"
                    )
                } else {
                    _uiState.value = PatternReviewUiState.Error(e.message ?: "Operation failed")
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
                PatternReviewViewModel(
                    getPatternsUseCase = container.getPatternsUseCase,
                    confirmPatternUseCase = container.confirmPatternUseCase,
                    refutePatternUseCase = container.refutePatternUseCase,
                    dismissPatternUseCase = container.dismissPatternUseCase,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
