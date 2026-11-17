package com.sanket_satpute_20.ironmind.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.NotificationObservationProvider
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetAppUsageObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetNotificationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetAppUsageObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetNotificationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isGlobalPauseActive: Boolean = false,
    val isAppUsageObservationEnabled: Boolean = false,
    val isAppUsagePermissionGranted: Boolean = false,
    val isNotificationObservationEnabled: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val isCalendarObservationEnabled: Boolean = false,
    val isCalendarPermissionGranted: Boolean = false,
    val isLocationObservationEnabled: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val isActivityObservationEnabled: Boolean = false,
    val isActivityPermissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isErasureConfirmationVisible: Boolean = false,
    val isErasingData: Boolean = false,
    val erasureSuccessMessage: String? = null,
    val erasureErrorMessage: String? = null,
    val isExporting: Boolean = false,
    val exportSuccessMetadata: ExportFileMetadata? = null,
    val exportErrorMessage: String? = null
)

class SettingsViewModel(
    private val getAutonomySettingsUseCase: GetAutonomySettingsUseCase,
    private val toggleGlobalPauseUseCase: ToggleGlobalPauseUseCase,
    private val getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase,
    private val setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase,
    private val appUsageObservationProvider: AppUsageObservationProvider,
    private val getNotificationObservationSettingsUseCase: GetNotificationObservationSettingsUseCase,
    private val setNotificationObservationEnabledUseCase: SetNotificationObservationEnabledUseCase,
    private val notificationObservationProvider: NotificationObservationProvider,
    private val getCalendarObservationSettingsUseCase: GetCalendarObservationSettingsUseCase,
    private val setCalendarObservationEnabledUseCase: SetCalendarObservationEnabledUseCase,
    private val calendarObservationProvider: CalendarObservationProvider,
    private val getLocationObservationSettingsUseCase: GetLocationObservationSettingsUseCase,
    private val setLocationObservationEnabledUseCase: SetLocationObservationEnabledUseCase,
    private val locationObservationProvider: LocationObservationProvider,
    private val getActivityObservationSettingsUseCase: GetActivityObservationSettingsUseCase,
    private val setActivityObservationEnabledUseCase: SetActivityObservationEnabledUseCase,
    private val activityObservationProvider: ActivityObservationProvider,
    private val userSessionProvider: UserSessionProvider? = null,
    private val deleteUserDataUseCase: DeleteUserDataUseCase? = null,
    private val exportUserDataToFileUseCase: ExportUserDataToFileUseCase? = null,
    private val shareManager: UserDataExportShareManager = AndroidUserDataExportShareManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var loadedUserId: String? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            loadSettings(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadedUserId = null
                        loadJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            isExporting = false,
                            exportSuccessMetadata = null,
                            exportErrorMessage = null
                        )
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadedUserId = null
                        loadJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Session reconciliation failed: ${state.error.message ?: "Unknown error"}",
                            isExporting = false,
                            exportSuccessMetadata = null,
                            exportErrorMessage = null
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        loadSettings(state.currentUserId)
                    }
                }
            }
        }
    }

    private fun loadSettings(userId: String = currentUserId) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadedUserId = null
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                isExporting = false,
                exportSuccessMetadata = null,
                exportErrorMessage = null
            )
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession
                    val autonomyResult = getAutonomySettingsUseCase(activeUid)
                    val appUsageResult = getAppUsageObservationSettingsUseCase(activeUid)
                    val notificationResult = getNotificationObservationSettingsUseCase(activeUid)
                    val calendarResult = getCalendarObservationSettingsUseCase(activeUid)
                    val locationResult = getLocationObservationSettingsUseCase(activeUid)
                    val activityResult = getActivityObservationSettingsUseCase(activeUid)

                    val failure = listOfNotNull(
                        (autonomyResult as? Result.Failure)?.error,
                        (appUsageResult as? Result.Failure)?.error,
                        (notificationResult as? Result.Failure)?.error,
                        (calendarResult as? Result.Failure)?.error,
                        (locationResult as? Result.Failure)?.error,
                        (activityResult as? Result.Failure)?.error
                    ).firstOrNull()
                    if (failure != null) {
                        if (isCurrentLoad(userId, generation)) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = failure.message ?: "Failed to load settings"
                            )
                        }
                        return@runWithSession
                    }
                    if (!isCurrentLoad(userId, generation)) return@runWithSession

                    val isGlobalPaused = (autonomyResult as Result.Success).data.isGlobalPauseActive
                    val isAppUsageEnabled = (appUsageResult as Result.Success).data.isEnabled
                    val isAppUsagePermissionGranted = appUsageObservationProvider.isPermissionGranted()
                    val isNotificationEnabled = (notificationResult as Result.Success).data.isEnabled
                    val isNotificationPermissionGranted = notificationObservationProvider.isPermissionGranted()
                    val isCalendarEnabled = (calendarResult as Result.Success).data.isEnabled
                    val isCalendarPermissionGranted = calendarObservationProvider.isPermissionGranted()
                    val isLocationEnabled = (locationResult as Result.Success).data.isEnabled
                    val isLocationPermissionGranted = locationObservationProvider.isPermissionGranted()
                    val isActivityEnabled = (activityResult as Result.Success).data.isEnabled
                    val isActivityPermissionGranted = activityObservationProvider.isPermissionGranted()

                    if (!isCurrentLoad(userId, generation)) return@runWithSession
                    loadedUserId = activeUid
                    println("IronMindLifecycle [Settings] [LOADED] userId=$userId isGlobalPauseActive=$isGlobalPaused isAppUsageEnabled=$isAppUsageEnabled isNotificationEnabled=$isNotificationEnabled isCalendarEnabled=$isCalendarEnabled isLocationEnabled=$isLocationEnabled isActivityEnabled=$isActivityEnabled")
                    _uiState.value = _uiState.value.copy(
                        isGlobalPauseActive = isGlobalPaused,
                        isAppUsageObservationEnabled = isAppUsageEnabled,
                        isAppUsagePermissionGranted = isAppUsagePermissionGranted,
                        isNotificationObservationEnabled = isNotificationEnabled,
                        isNotificationPermissionGranted = isNotificationPermissionGranted,
                        isCalendarObservationEnabled = isCalendarEnabled,
                        isCalendarPermissionGranted = isCalendarPermissionGranted,
                        isLocationObservationEnabled = isLocationEnabled,
                        isLocationPermissionGranted = isLocationPermissionGranted,
                        isActivityObservationEnabled = isActivityEnabled,
                        isActivityPermissionGranted = isActivityPermissionGranted,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load settings"
                    )
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

    private fun isCurrentSession(userId: String): Boolean {
        val state = userSessionProvider?.sessionState?.value
            ?: return userId == SessionState.DEFAULT_LEGACY_USER_ID
        return when (state) {
            is SessionState.Authenticated, is SessionState.OfflineFallback -> state.currentUserId == userId
            is SessionState.Initializing, is SessionState.ReconciliationFailed -> false
        }
    }

    fun setGlobalPause(isPaused: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                println("IronMindLifecycle [Autonomy] [GLOBAL_PAUSE_TOGGLED] userId=$activeUid isPaused=$isPaused")
                when (val result = toggleGlobalPauseUseCase(activeUid, isPaused)) {
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
    }

    fun setAppUsageObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                when (val result = setAppUsageObservationEnabledUseCase(activeUid, isEnabled)) {
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
    }

    fun setNotificationObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                when (val result = setNotificationObservationEnabledUseCase(activeUid, isEnabled)) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(isNotificationObservationEnabled = isEnabled, errorMessage = null)
                    }
                    is Result.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = result.error.message ?: "Failed to update notification observation setting"
                        )
                    }
                }
            }
        }
    }

    fun setCalendarObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                when (val result = setCalendarObservationEnabledUseCase(activeUid, isEnabled)) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(isCalendarObservationEnabled = isEnabled, errorMessage = null)
                    }
                    is Result.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = result.error.message ?: "Failed to update calendar observation setting"
                        )
                    }
                }
            }
        }
    }

    fun setLocationObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                when (val result = setLocationObservationEnabledUseCase(activeUid, isEnabled)) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(isLocationObservationEnabled = isEnabled, errorMessage = null)
                    }
                    is Result.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = result.error.message ?: "Failed to update location observation setting"
                        )
                    }
                }
            }
        }
    }

    fun setActivityObservationEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userSessionProvider.runWithSession { activeUid ->
                if (activeUid != loadedUserId || !isCurrentSession(activeUid)) return@runWithSession
                val result = setActivityObservationEnabledUseCase(activeUid, isEnabled)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(isActivityObservationEnabled = isEnabled, errorMessage = null)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.errorOrNull()?.message ?: "Failed to update activity observation setting"
                    )
                }
            }
        }
    }

    fun requestErasureConfirmation() {
        if (_uiState.value.isErasingData) return
        _uiState.value = _uiState.value.copy(
            isErasureConfirmationVisible = true,
            erasureErrorMessage = null
        )
    }

    fun dismissErasureConfirmation() {
        _uiState.value = _uiState.value.copy(
            isErasureConfirmationVisible = false
        )
    }

    fun dismissErasureMessages() {
        _uiState.value = _uiState.value.copy(
            erasureSuccessMessage = null,
            erasureErrorMessage = null
        )
    }

    fun eraseLocalData() {
        if (_uiState.value.isErasingData) return
        val useCase = deleteUserDataUseCase ?: run {
            _uiState.value = _uiState.value.copy(
                erasureErrorMessage = "Data erasure use case is not configured"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isErasureConfirmationVisible = false,
            isErasingData = true,
            erasureSuccessMessage = null,
            erasureErrorMessage = null
        )

        viewModelScope.launch {
            val targetUserId = currentUserId
            println("IronMindLifecycle [Settings] [ERASURE_REQUESTED] userId=$targetUserId")
            try {
                when (val result = useCase(targetUserId)) {
                    is Result.Success -> {
                        println("IronMindLifecycle [Settings] [ERASURE_SUCCESS] userId=$targetUserId")
                        _uiState.value = _uiState.value.copy(
                            isErasingData = false,
                            erasureSuccessMessage = "Local IronMind data has been safely erased.",
                            exportSuccessMetadata = null,
                            exportErrorMessage = null
                        )
                    }
                    is Result.Failure -> {
                        println("IronMindLifecycle [Settings] [ERASURE_FAILED] userId=$targetUserId error=${result.error.message}")
                        _uiState.value = _uiState.value.copy(
                            isErasingData = false,
                            erasureErrorMessage = result.error.message ?: "Failed to erase local data"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Settings] [ERASURE_EXCEPTION] error=${e.message}")
                _uiState.value = _uiState.value.copy(
                    isErasingData = false,
                    erasureErrorMessage = e.message ?: "Failed to erase local data"
                )
            }
        }
    }

    fun exportUserData() {
        if (_uiState.value.isExporting || _uiState.value.isErasingData) return
        val useCase = exportUserDataToFileUseCase ?: run {
            _uiState.value = _uiState.value.copy(
                exportErrorMessage = "Export use case is not configured"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isExporting = true,
            exportSuccessMetadata = null,
            exportErrorMessage = null
        )

        viewModelScope.launch {
            val targetUserId = currentUserId
            println("IronMindLifecycle [Settings] [EXPORT_REQUESTED] userId=$targetUserId")
            try {
                when (val result = useCase(targetUserId)) {
                    is Result.Success -> {
                        if (!isCurrentSession(targetUserId)) {
                            println("IronMindLifecycle [Settings] [EXPORT_IGNORED] session changed during export")
                            _uiState.value = _uiState.value.copy(
                                isExporting = false,
                                exportSuccessMetadata = null,
                                exportErrorMessage = null
                            )
                            return@launch
                        }

                        println("IronMindLifecycle [Settings] [EXPORT_SUCCESS] userId=$targetUserId file=${result.data.fileName} records=${result.data.recordCount}")
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            exportSuccessMetadata = result.data,
                            exportErrorMessage = null
                        )
                    }
                    is Result.Failure -> {
                        println("IronMindLifecycle [Settings] [EXPORT_FAILED] userId=$targetUserId error=${result.error.message}")
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            exportErrorMessage = result.error.message ?: "Failed to export data"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Settings] [EXPORT_EXCEPTION] error=${e.message}")
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportErrorMessage = e.message ?: "Failed to export data"
                )
            }
        }
    }

    fun dismissExportMessages() {
        _uiState.value = _uiState.value.copy(
            exportSuccessMetadata = null,
            exportErrorMessage = null
        )
    }

    fun getShareIntent(context: Context): Intent? {
        val metadata = _uiState.value.exportSuccessMetadata ?: return null
        if (metadata.userId != currentUserId || !isCurrentSession(currentUserId)) {
            return null
        }
        if (!metadata.file.exists() || metadata.file.length() <= 0L) {
            _uiState.value = _uiState.value.copy(
                exportErrorMessage = "Export file is no longer available"
            )
            return null
        }
        return shareManager.createShareChooserIntent(context, metadata)
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
                    appUsageObservationProvider = container.appUsageObservationProvider,
                    getNotificationObservationSettingsUseCase = container.getNotificationObservationSettingsUseCase,
                    setNotificationObservationEnabledUseCase = container.setNotificationObservationEnabledUseCase,
                    notificationObservationProvider = container.notificationObservationProvider,
                    getCalendarObservationSettingsUseCase = container.getCalendarObservationSettingsUseCase,
                    setCalendarObservationEnabledUseCase = container.setCalendarObservationEnabledUseCase,
                    calendarObservationProvider = container.calendarObservationProvider,
                    getLocationObservationSettingsUseCase = container.getLocationObservationSettingsUseCase,
                    setLocationObservationEnabledUseCase = container.setLocationObservationEnabledUseCase,
                    locationObservationProvider = container.locationObservationProvider,
                    getActivityObservationSettingsUseCase = container.getActivityObservationSettingsUseCase,
                    setActivityObservationEnabledUseCase = container.setActivityObservationEnabledUseCase,
                    activityObservationProvider = container.activityObservationProvider,
                    userSessionProvider = container.userSessionProvider,
                    deleteUserDataUseCase = container.deleteUserDataUseCase,
                    exportUserDataToFileUseCase = container.exportUserDataToFileUseCase,
                    shareManager = container.userDataExportShareManager
                )
            }
        }
    }
}
