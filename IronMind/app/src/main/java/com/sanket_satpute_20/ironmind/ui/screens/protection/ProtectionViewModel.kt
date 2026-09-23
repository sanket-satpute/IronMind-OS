package com.sanket_satpute_20.ironmind.ui.screens.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProtectionUiState {
    data object Loading : ProtectionUiState
    data object NoActiveSession : ProtectionUiState
    data class ActiveSession(val session: ProtectionSession) : ProtectionUiState
    data class Error(val message: String) : ProtectionUiState
}

class ProtectionViewModel(
    private val getActiveProtectionSessionUseCase: GetActiveProtectionSessionUseCase,
    private val startProtectionSessionUseCase: StartProtectionSessionUseCase,
    private val stopProtectionSessionUseCase: StopProtectionSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProtectionUiState>(ProtectionUiState.Loading)
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    // Using a hardcoded user ID for V0
    private val currentUserId = "user-1"

    init {
        loadCurrentSession()
    }

    fun loadCurrentSession() {
        viewModelScope.launch {
            _uiState.value = ProtectionUiState.Loading
            println("IronMindLifecycle [Protection] [LOAD_START]")
            when (val result = getActiveProtectionSessionUseCase(currentUserId)) {
                is Result.Success -> {
                    println("IronMindLifecycle [Protection] [LOAD_SUCCESS]")
                    if (result.data != null) {
                        _uiState.value = ProtectionUiState.ActiveSession(result.data)
                    } else {
                        _uiState.value = ProtectionUiState.NoActiveSession
                    }
                }
                is Result.Failure -> {
                    println("IronMindLifecycle [Protection] [LOAD_FAILURE]")
                    _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to load session")
                }
            }
        }
    }

    fun startProtection(targetPackages: List<String>) {
        viewModelScope.launch {
            println("IronMindLifecycle [Protection] [START_REQUEST] targetPackages=${targetPackages.joinToString()}")
            when (val result = startProtectionSessionUseCase(
                userId = currentUserId,
                targetPackages = targetPackages
            )) {
                is Result.Success -> {
                    println("IronMindLifecycle [Protection] [START_SUCCESS] sessionId=${result.data.id}")
                    loadCurrentSession()
                }
                is Result.Failure -> {
                    println("IronMindLifecycle [Protection] [START_FAILURE] error=${result.error.message}")
                    _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to start protection. Ensure accessibility permissions are granted.")
                }
            }
        }
    }

    fun stopProtection() {
        val currentState = _uiState.value
        if (currentState is ProtectionUiState.ActiveSession) {
            viewModelScope.launch {
                println("IronMindLifecycle [Protection] [STOP_REQUEST] sessionId=${currentState.session.id}")
                when (val result = stopProtectionSessionUseCase(currentState.session.id)) {
                    is Result.Success -> {
                        println("IronMindLifecycle [Protection] [STOP_SUCCESS]")
                        loadCurrentSession()
                    }
                    is Result.Failure -> {
                        println("IronMindLifecycle [Protection] [STOP_FAILURE] error=${result.error.message}")
                        _uiState.value = ProtectionUiState.Error(result.error.message ?: "Failed to stop protection")
                    }
                }
            }
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
                    stopProtectionSessionUseCase = container.stopProtectionSessionUseCase
                )
            }
        }
    }
}
