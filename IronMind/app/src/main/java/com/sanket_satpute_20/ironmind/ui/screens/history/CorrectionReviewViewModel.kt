package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetPendingCorrectionReviewsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CorrectionReviewUiState {
    data object Loading : CorrectionReviewUiState
    data class Success(
        val reviews: List<CorrectionReviewItem>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : CorrectionReviewUiState
    data class Error(val message: String) : CorrectionReviewUiState
}

class CorrectionReviewViewModel(
    private val getPendingCorrectionReviewsUseCase: GetPendingCorrectionReviewsUseCase,
    private val approveCorrectionReviewUseCase: ApproveCorrectionReviewUseCase,
    private val refuteCorrectionReviewUseCase: RefuteCorrectionReviewUseCase,
    private val dismissCorrectionReviewUseCase: DismissCorrectionReviewUseCase,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<CorrectionReviewUiState>(CorrectionReviewUiState.Loading)
    val uiState: StateFlow<CorrectionReviewUiState> = _uiState.asStateFlow()

    private val isActionInProgress = AtomicBoolean(false)
    private var observeJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            observeReviews(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        observeJob?.cancel()
                        _uiState.value = CorrectionReviewUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observeJob?.cancel()
                        _uiState.value = CorrectionReviewUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        observeReviews(state.currentUserId)
                    }
                }
            }
        }
    }

    fun observeReviews(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = CorrectionReviewUiState.Loading
            try {
                getPendingCorrectionReviewsUseCase.observe(userId).collect { reviews ->
                    val currentState = _uiState.value
                    val isSubmitting = if (currentState is CorrectionReviewUiState.Success) currentState.isSubmitting else false
                    _uiState.value = CorrectionReviewUiState.Success(
                        reviews = reviews,
                        isSubmitting = isSubmitting,
                        submitError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CorrectionReviewUiState.Error(e.message ?: "Failed to load correction reviews")
            }
        }
    }

    fun approve(reviewId: String, feedback: String? = null) {
        executeReviewAction { activeUserId ->
            approveCorrectionReviewUseCase(activeUserId, reviewId, feedback)
        }
    }

    fun refute(reviewId: String, feedback: String? = null) {
        executeReviewAction { activeUserId ->
            refuteCorrectionReviewUseCase(activeUserId, reviewId, feedback)
        }
    }

    fun dismiss(reviewId: String, feedback: String? = null) {
        executeReviewAction { activeUserId ->
            dismissCorrectionReviewUseCase(activeUserId, reviewId, feedback)
        }
    }

    private fun executeReviewAction(action: suspend (String) -> Result<Unit, Exception>) {
        if (!isActionInProgress.compareAndSet(false, true)) {
            return // Double submission prevention
        }

        val currentState = _uiState.value
        if (currentState is CorrectionReviewUiState.Success) {
            _uiState.value = currentState.copy(isSubmitting = true, submitError = null)
        }

        viewModelScope.launch {
            try {
                val generation = userSessionProvider?.getSessionGeneration() ?: 1L
                val result = userSessionProvider.runWithSession(expectedGeneration = generation) { activeUserId ->
                    action(activeUserId)
                } ?: Result.Failure(IllegalStateException("No active user session"))

                when (result) {
                    is Result.Success -> {
                        val state = _uiState.value
                        if (state is CorrectionReviewUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = null)
                        }
                    }
                    is Result.Failure -> {
                        val state = _uiState.value
                        if (state is CorrectionReviewUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = result.error.message ?: "Action failed")
                        } else {
                            _uiState.value = CorrectionReviewUiState.Error(result.error.message ?: "Action failed")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val state = _uiState.value
                if (state is CorrectionReviewUiState.Success) {
                    _uiState.value = state.copy(isSubmitting = false, submitError = e.message ?: "Unexpected error")
                } else {
                    _uiState.value = CorrectionReviewUiState.Error(e.message ?: "Unexpected error")
                }
            } finally {
                isActionInProgress.set(false)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication
                val container = app.container
                CorrectionReviewViewModel(
                    getPendingCorrectionReviewsUseCase = container.getPendingCorrectionReviewsUseCase,
                    approveCorrectionReviewUseCase = container.approveCorrectionReviewUseCase,
                    refuteCorrectionReviewUseCase = container.refuteCorrectionReviewUseCase,
                    dismissCorrectionReviewUseCase = container.dismissCorrectionReviewUseCase,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
