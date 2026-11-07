package com.sanket_satpute_20.ironmind.ui.screens.protection.restriction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetAppRestrictionConfigsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetInstalledApplicationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.SaveAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.ToggleAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.data.provider.AndroidInstalledAppDiscoveryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppRestrictionViewModel(
    private val getInstalledApplicationsUseCase: GetInstalledApplicationsUseCase,
    private val getAppRestrictionConfigsUseCase: GetAppRestrictionConfigsUseCase,
    private val saveAppRestrictionConfigUseCase: SaveAppRestrictionConfigUseCase,
    private val toggleAppRestrictionConfigUseCase: ToggleAppRestrictionConfigUseCase,
    private val installedAppDiscoveryProvider: InstalledAppDiscoveryProvider,
    private val appProtectionProvider: AppProtectionProvider,
    private val protectionRepository: ProtectionRepository,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppRestrictionUiState(isLoading = true))
    val uiState: StateFlow<AppRestrictionUiState> = _uiState.asStateFlow()

    private val defaultUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private var loadJob: Job? = null
    private var loadGeneration = 0L

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
                        loadJob?.cancel()
                        _uiState.value = AppRestrictionUiState(isLoading = true)
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadJob?.cancel()
                        _uiState.value = AppRestrictionUiState(
                            isLoading = false,
                            errorMessage = "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        loadData(state.currentUserId)
                    }
                }
            }
        }
    }

    fun loadData(userId: String = defaultUserId) {
        loadJob?.cancel()
        val generation = ++loadGeneration

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession

                    val isAccessibilityGranted = appProtectionProvider.hasRequiredPermissions()
                    val isProtectionActive = appProtectionProvider.isProtectionActive()

                    // Check active protection sessions
                    val activeSessionsResult = protectionRepository.getActiveProtectionSessionsForUser(activeUid)
                    val activeSessions = when (activeSessionsResult) {
                        is Result.Success -> activeSessionsResult.data.filter { it.status == ProtectionSessionStatus.ACTIVE }
                        is Result.Failure -> emptyList()
                    }

                    // Query configured restrictions
                    val configsResult = getAppRestrictionConfigsUseCase(activeUid)
                    val configs = when (configsResult) {
                        is Result.Success -> configsResult.data
                        is Result.Failure -> emptyList()
                    }

                    // Check which configured packages are uninstalled or missing
                    val uninstalledPackages = configs
                        .filter { !installedAppDiscoveryProvider.isAppInstalled(it.packageName) }
                        .map { it.packageName }
                        .toSet()

                    // Determine actively enforced packages:
                    // An app is actively enforced ONLY when protection is running for the active user
                    // and the app restriction is configured and enabled.
                    val activelyEnforced = if (isProtectionActive && activeSessions.isNotEmpty()) {
                        configs.filter { it.isEnabled }.map { it.packageName }.toSet()
                    } else {
                        emptySet()
                    }

                    // Query installed launchable applications
                    val currentQuery = _uiState.value.searchQuery
                    val installed = getInstalledApplicationsUseCase(currentQuery)

                    if (isCurrentLoad(userId, generation)) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                installedApps = installed,
                                configuredRestrictions = configs,
                                activeEnforcedPackageNames = activelyEnforced,
                                uninstalledConfiguredPackages = uninstalledPackages,
                                isAccessibilityPermissionGranted = isAccessibilityGranted,
                                errorMessage = if (configsResult is Result.Failure) {
                                    configsResult.error.message ?: "Failed to load configurations"
                                } else null
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load restrictions"
                        )
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                installedApps = getInstalledApplicationsUseCase(query)
            )
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { current ->
            val updatedSelection = if (current.selectedPackageNames.contains(packageName)) {
                current.selectedPackageNames - packageName
            } else {
                current.selectedPackageNames + packageName
            }
            current.copy(selectedPackageNames = updatedSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackageNames = emptySet()) }
    }

    fun saveSelectedRestrictions() {
        val selected = _uiState.value.selectedPackageNames
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    for (pkg in selected) {
                        val appLabel = installedAppDiscoveryProvider.getAppDisplayName(pkg) ?: pkg
                        saveAppRestrictionConfigUseCase(
                            userId = activeUid,
                            packageName = pkg,
                            appDisplayName = appLabel,
                            isEnabled = true
                        )
                    }
                    _uiState.update { it.copy(selectedPackageNames = emptySet()) }
                    loadData(activeUid)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save restrictions") }
            }
        }
    }

    fun toggleRestriction(configId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    val result = toggleAppRestrictionConfigUseCase(
                        userId = activeUid,
                        configId = configId,
                        isEnabled = isEnabled
                    )
                    when (result) {
                        is Result.Success -> loadData(activeUid)
                        is Result.Failure -> _uiState.update {
                            it.copy(errorMessage = result.error.message ?: "Failed to update restriction")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to update restriction") }
            }
        }
    }

    fun refresh() {
        loadData(defaultUserId)
    }

    fun acknowledgeError() {
        _uiState.update { it.copy(errorMessage = null) }
        refresh()
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                val discoveryProvider = AndroidInstalledAppDiscoveryProvider(application)

                AppRestrictionViewModel(
                    getInstalledApplicationsUseCase = GetInstalledApplicationsUseCase(discoveryProvider),
                    getAppRestrictionConfigsUseCase = GetAppRestrictionConfigsUseCase(container.protectionRepository),
                    saveAppRestrictionConfigUseCase = SaveAppRestrictionConfigUseCase(container.protectionRepository, container.clock),
                    toggleAppRestrictionConfigUseCase = ToggleAppRestrictionConfigUseCase(container.protectionRepository, container.clock),
                    installedAppDiscoveryProvider = discoveryProvider,
                    appProtectionProvider = container.appProtectionProvider,
                    protectionRepository = container.protectionRepository,
                    userSessionProvider = container.userSessionProvider
                )
            }
        }
    }
}
