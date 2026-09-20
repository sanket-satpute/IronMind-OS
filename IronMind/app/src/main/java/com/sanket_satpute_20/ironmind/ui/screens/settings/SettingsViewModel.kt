package com.sanket_satpute_20.ironmind.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetAppUsageObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetAppUsageObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isGlobalPauseActive: Boolean = false,
    val isAppUsageObservationEnabled: Boolean = false,
    val isAppUsagePermissionGranted: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val getAutonomySettingsUseCase: GetAutonomySettingsUseCase,
    private val toggleGlobalPauseUseCase: ToggleGlobalPauseUseCase,
    private val getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase,
    private val setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase,
    private val appUsageObservationProvider: AppUsageObservationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // V0.5 hardcoded user ID, consistent with the rest of the project
    private val currentUserId = "user-1"

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val autonomyResult = getAutonomySettingsUseCase(currentUserId)
            val appUsageResult = getAppUsageObservationSettingsUseCase(currentUserId)
            val isGlobalPaused = if (autonomyResult is Result.Success) autonomyResult.data.isGlobalPauseActive else false
            val isAppUsageEnabled = if (appUsageResult is Result.Success) appUsageResult.data.isEnabled else false
            val isPermissionGranted = appUsageObservationProvider.isPermissionGranted()
            println("IronMindLifecycle [Settings] [LOADED] userId=$currentUserId isGlobalPauseActive=$isGlobalPaused isAppUsageEnabled=$isAppUsageEnabled permissionGranted=$isPermissionGranted")
            _uiState.value = SettingsUiState(
                isGlobalPauseActive = isGlobalPaused,
                isAppUsageObservationEnabled = isAppUsageEnabled,
                isAppUsagePermissionGranted = isPermissionGranted,
                isLoading = false
            )
        }
    }

    fun setGlobalPause(isPaused: Boolean) {
        viewModelScope.launch {
            println("IronMindLifecycle [Autonomy] [GLOBAL_PAUSE_TOGGLED] userId=$currentUserId isPaused=$isPaused")
            when (val result = toggleGlobalPauseUseCase(currentUserId, isPaused)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isGlobalPauseActive = isPaused, errorMessage = null)
                }
                is Result.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.error.message ?: "Failed to update setting"
                    )
                }
            }
        }
    }

    fun setAppUsageObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            when (val result = setAppUsageObservationEnabledUseCase(currentUserId, isEnabled)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isAppUsageObservationEnabled = isEnabled, errorMessage = null)
                }
                is Result.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.error.message ?: "Failed to update app usage setting"
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                SettingsViewModel(
                    getAutonomySettingsUseCase = container.getAutonomySettingsUseCase,
                    toggleGlobalPauseUseCase = container.toggleGlobalPauseUseCase,
                    getAppUsageObservationSettingsUseCase = container.getAppUsageObservationSettingsUseCase,
                    setAppUsageObservationEnabledUseCase = container.setAppUsageObservationEnabledUseCase,
                    appUsageObservationProvider = container.appUsageObservationProvider
                )
            }
        }
    }
}
