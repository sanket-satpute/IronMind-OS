package com.sanket_satpute_20.ironmind.ui.screens.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EnforcementAvailability
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.CancellationException
import com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProtectionUiState {
    data object Loading : ProtectionUiState
    data object NoActiveSession : ProtectionUiState
    data class ActiveSession(
        val session: ProtectionSession,
        val enforcementAvailability: EnforcementAvailability = EnforcementAvailability.AVAILABLE
    ) : ProtectionUiState
    data class Error(val message: String) : ProtectionUiState
}

class ProtectionViewModel(
    private val getActiveProtectionSessionUseCase: GetActiveProtectionSessionUseCase,
    private val startProtectionSessionUseCase: StartProtectionSessionUseCase,
    private val stopProtectionSessionUseCase: StopProtectionSessionUseCase,
    private val userSessionProvider: UserSessionProvider? = null,
    private val appProtectionProvider: AppProtectionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProtectionUiState>(ProtectionUiState.Loading)
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    private val defaultUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            loadCurrentSession(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = ProtectionUiState.Loading
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = ProtectionUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        loadCurrentSession(state.currentUserId)
                    }
                }
            }
        }
    }

    fun loadCurrentSession(userId: String = defaultUserId) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _uiState.value = ProtectionUiState.Loading
            println("IronMindLifecycle [Protection] [LOAD_START]")
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession
                    when (val result = getActiveProtectionSessionUseCase(activeUid)) {
                        is Result.Success -> if (isCurrentLoad(userId, generation)) {
                            println("IronMindLifecycle [Protection] [LOAD_SUCCESS]")
                            if (result.data != null && result.data.userId == activeUid) {
                                val availability = appProtectionProvider?.getEnforcementAvailability()
                                    ?: EnforcementAvailability.AVAILABLE
                                _uiState.value = ProtectionUiState.ActiveSession(
                                    session = result.data,
                                    enforcementAvailability = availability
                                )
                            } else if (result.data == null) {
                                _uiState.value = ProtectionUiState.NoActiveSession
                            } else {
                                _uiState.value = ProtectionUiState.Error("Protection session does not belong to this user")
                            }
                        }
                        is Result.Failure -> if (isCurrentLoad(userId, generation)) {
                            println("IronMindLifecycle [Protection] [LOAD_FAILURE]")
                            _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to load session")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = ProtectionUiState.Error(e.message ?: "Failed to load session")
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

    fun startProtection(targetPackages: List<String>) {
        if (targetPackages.isEmpty()) {
            println("IronMindLifecycle [Protection] [VALIDATION_FAILURE] reason=empty_targets")
            _uiState.value = ProtectionUiState.Error("Please select at least one application to protect.")
            return
        }
        viewModelScope.launch {
            println("IronMindLifecycle [Protection] [START_REQUEST] targetCount=${targetPackages.size}")
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (!isCurrentSession(activeUid)) return@runWithSession
                    when (val result = startProtectionSessionUseCase(
                        userId = activeUid,
                        targetPackages = targetPackages
                    )) {
                        is Result.Success -> if (isCurrentSession(activeUid) && result.data.userId == activeUid) {
                            println("IronMindLifecycle [Protection] [START_SUCCESS] sessionId=${result.data.id}")
                            loadCurrentSession(activeUid)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            println("IronMindLifecycle [Protection] [START_FAILURE] error=${result.error.message}")
                            _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to start protection. Ensure accessibility permissions are granted.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun stopProtection() {
        val currentState = _uiState.value
        if (currentState is ProtectionUiState.ActiveSession) {
            viewModelScope.launch {
                println("IronMindLifecycle [Protection] [STOP_REQUEST] sessionId=${currentState.session.id}")
                try {
                    userSessionProvider.runWithSession { activeUid ->
                    if (currentState.session.userId != activeUid || !isCurrentSession(activeUid)) return@runWithSession
                    when (val result = stopProtectionSessionUseCase(currentState.session.id)) {
                        is Result.Success -> if (isCurrentSession(activeUid)) {
                            println("IronMindLifecycle [Protection] [STOP_SUCCESS]")
                            loadCurrentSession(activeUid)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid)) {
                            println("IronMindLifecycle [Protection] [STOP_FAILURE] error=${result.error.message}")
                            _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to stop protection")
                        }
                    }
                }
                } catch (e: CancellationException) {
                    throw e
                }
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

    fun acknowledgeError() {
        loadCurrentSession()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                ProtectionViewModel(
                    getActiveProtectionSessionUseCase = container.getActiveProtectionSessionUseCase,
                    startProtectionSessionUseCase = container.startProtectionSessionUseCase,
                    stopProtectionSessionUseCase = container.stopProtectionSessionUseCase,
                    userSessionProvider = container.userSessionProvider,
                    appProtectionProvider = container.appProtectionProvider
                )
            }
        }
    }
}
