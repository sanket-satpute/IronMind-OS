package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.memory.ConfirmMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.CorrectMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.DeleteMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetMemoriesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.RejectMemoryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface MemoryManagementUiState {
    data object Loading : MemoryManagementUiState
    data class Success(
        val memories: List<Memory>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null
    ) : MemoryManagementUiState
    data class Error(val message: String) : MemoryManagementUiState
}

class MemoryManagementViewModel(
    private val getMemoriesUseCase: GetMemoriesUseCase,
    private val confirmMemoryUseCase: ConfirmMemoryUseCase,
    private val correctMemoryUseCase: CorrectMemoryUseCase,
    private val rejectMemoryUseCase: RejectMemoryUseCase,
    private val deleteMemoryUseCase: DeleteMemoryUseCase,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemoryManagementUiState>(MemoryManagementUiState.Loading)
    val uiState: StateFlow<MemoryManagementUiState> = _uiState.asStateFlow()

    private val isActionInProgress = AtomicBoolean(false)
    private var observeJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            observeMemories(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        observeJob?.cancel()
                        _uiState.value = MemoryManagementUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        observeJob?.cancel()
                        _uiState.value = MemoryManagementUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        observeMemories(state.currentUserId)
                    }
                }
            }
        }
    }

    fun observeMemories(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = MemoryManagementUiState.Loading
            try {
                getMemoriesUseCase.observe(userId).collect { memories ->
                    val currentState = _uiState.value
                    val isSubmitting = if (currentState is MemoryManagementUiState.Success) currentState.isSubmitting else false
                    _uiState.value = MemoryManagementUiState.Success(
                        memories = memories,
                        isSubmitting = isSubmitting,
                        submitError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MemoryManagementUiState.Error(e.message ?: "Failed to load memories")
            }
        }
    }

    fun confirm(memoryId: String) {
        executeMemoryAction { activeUserId ->
            val result = confirmMemoryUseCase(activeUserId, memoryId)
            when (result) {
                is Result.Success -> Result.Success(Unit)
                is Result.Failure -> Result.Failure(result.error)
            }
        }
    }

    fun correct(memoryId: String, newContent: String) {
        executeMemoryAction { activeUserId ->
            val result = correctMemoryUseCase(activeUserId, memoryId, newContent)
            when (result) {
                is Result.Success -> Result.Success(Unit)
                is Result.Failure -> Result.Failure(result.error)
            }
        }
    }

    fun reject(memoryId: String) {
        executeMemoryAction { activeUserId ->
            rejectMemoryUseCase(activeUserId, memoryId)
        }
    }

    fun delete(memoryId: String) {
        executeMemoryAction { activeUserId ->
            deleteMemoryUseCase(activeUserId, memoryId)
        }
    }

    private fun executeMemoryAction(
        action: suspend (activeUserId: String) -> Result<Unit, Exception>
    ) {
        if (!isActionInProgress.compareAndSet(false, true)) {
            return
        }

        val currentState = _uiState.value
        if (currentState is MemoryManagementUiState.Success) {
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
                        if (state is MemoryManagementUiState.Success) {
                            _uiState.value = state.copy(isSubmitting = false, submitError = null)
                        }
                    }
                    is Result.Failure -> {
                        val state = _uiState.value
                        if (state is MemoryManagementUiState.Success) {
                            _uiState.value = state.copy(
                                isSubmitting = false,
                                submitError = actionResult.error.message ?: "Action failed"
                            )
                        } else {
                            _uiState.value = MemoryManagementUiState.Error(
                                actionResult.error.message ?: "Action failed"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val state = _uiState.value
                if (state is MemoryManagementUiState.Success) {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "Operation failed"
                    )
                } else {
                    _uiState.value = MemoryManagementUiState.Error(e.message ?: "Operation failed")
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
                MemoryManagementViewModel(
                    getMemoriesUseCase = container.getMemoriesUseCase,
                    confirmMemoryUseCase = container.confirmMemoryUseCase,
                    correctMemoryUseCase = container.correctMemoryUseCase,
                    rejectMemoryUseCase = container.rejectMemoryUseCase,
                    deleteMemoryUseCase = container.deleteMemoryUseCase,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
