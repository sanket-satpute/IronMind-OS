package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.DismissBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.GetBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.RefuteBarrierUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface BarrierReviewUiState {
    data object Loading : BarrierReviewUiState
    data class Success(
        val barriers: List<BarrierHypothesis>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : BarrierReviewUiState
    data class Error(val message: String) : BarrierReviewUiState
}

class BarrierReviewViewModel(
    private val getBarriersUseCase: GetBarriersUseCase,
    private val confirmBarrierUseCase: ConfirmBarrierUseCase,
    private val refuteBarrierUseCase: RefuteBarrierUseCase,
    private val dismissBarrierUseCase: DismissBarrierUseCase,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<BarrierReviewUiState>(BarrierReviewUiState.Loading)
    val uiState: StateFlow<BarrierReviewUiState> = _uiState.asStateFlow()

    private val isActionInProgress = AtomicBoolean(false)
    private var observeJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            observeBarriers(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        observeJob?.cancel()
                        _uiState.value = BarrierReviewUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observeJob?.cancel()
                        _uiState.value = BarrierReviewUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        observeBarriers(state.currentUserId)
                    }
                }
            }
        }
    }

    fun observeBarriers(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = BarrierReviewUiState.Loading
            try {
                getBarriersUseCase.observe(userId).collect { barriers ->
                    val currentState = _uiState.value
                    val isSubmitting = if (currentState is BarrierReviewUiState.Success) currentState.isSubmitting else false
                    _uiState.value = BarrierReviewUiState.Success(
                        barriers = barriers,
                        isSubmitting = isSubmitting,
                        submitError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = BarrierReviewUiState.Error(e.message ?: "Failed to load barriers")
            }
        }
    }

    fun confirm(barrierId: String) {
        executeBarrierAction { activeUserId ->
            confirmBarrierUseCase(activeUserId, barrierId)
        }
    }

    fun refute(barrierId: String) {
        executeBarrierAction { activeUserId ->
            refuteBarrierUseCase(activeUserId, barrierId)
        }
    }

    fun dismiss(barrierId: String) {
        executeBarrierAction { activeUserId ->
            dismissBarrierUseCase(activeUserId, barrierId)
        }
    }

    private fun executeBarrierAction(
        action: suspend (activeUserId: String) -> Result<Unit, Exception>
    ) {
        if (!isActionInProgress.compareAndSet(false, true)) {
            return
        }

        val currentState = _uiState.value
        if (currentState is BarrierReviewUiState.Success) {
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
                        if (state is BarrierReviewUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = null)
                        }
                    }
                    is Result.Failure -> {
                        val state = _uiState.value
                        if (state is BarrierReviewUiState.Success) {
                            _uiState.value = state.copy(
                                isSubmitting = false,
                                submitError = actionResult.error.message ?: "Action failed"
                            )
                        } else {
                            _uiState.value = BarrierReviewUiState.Error(
                                actionResult.error.message ?: "Action failed"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val state = _uiState.value
                if (state is BarrierReviewUiState.Success) {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "Operation failed"
                    )
                } else {
                    _uiState.value = BarrierReviewUiState.Error(e.message ?: "Operation failed")
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
                BarrierReviewViewModel(
                    getBarriersUseCase = container.getBarriersUseCase,
                    confirmBarrierUseCase = container.confirmBarrierUseCase,
                    refuteBarrierUseCase = container.refuteBarrierUseCase,
                    dismissBarrierUseCase = container.dismissBarrierUseCase,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
