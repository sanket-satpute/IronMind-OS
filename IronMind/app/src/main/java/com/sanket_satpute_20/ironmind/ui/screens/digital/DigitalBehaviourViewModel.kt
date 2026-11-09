package com.sanket_satpute_20.ironmind.ui.screens.digital

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationExecutionError
import com.sanket_satpute_20.ironmind.infrastructure.worker.IntelligenceOrchestrationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DigitalBehaviourViewModel(
    private val getDailyAppUsageIntelligenceUseCase: GetDailyAppUsageIntelligenceUseCase,
    private val appUsageObservationProvider: AppUsageObservationProvider,
    private val appUsageSettingsRepository: AppUsageObservationSettingsRepository,
    private val protectionRepository: ProtectionRepository,
    private val userSessionProvider: UserSessionProvider? = null,
    private val appNameResolver: ((String) -> String?)? = null,
    private val clock: Clock = object : Clock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val executeObservationCollectionUseCase: ExecuteObservationCollectionUseCase? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val intelligenceOrchestrationScheduler: IntelligenceOrchestrationScheduler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DigitalBehaviourUiState(
            selectedDate = Instant.ofEpochMilli(clock.currentTimeMillis()).atZone(zoneId).toLocalDate(),
            isLoading = true
        )
    )
    val uiState: StateFlow<DigitalBehaviourUiState> = _uiState.asStateFlow()

    private val defaultUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var catchupJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            loadData(targetDate = _uiState.value.selectedDate, userId = SessionState.DEFAULT_LEGACY_USER_ID)
            triggerCatchup(userId = SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadJob?.cancel()
                        catchupJob?.cancel()
                        _uiState.value = DigitalBehaviourUiState(
                            selectedDate = _uiState.value.selectedDate,
                            isLoading = true
                        )
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadJob?.cancel()
                        catchupJob?.cancel()
                        _uiState.value = DigitalBehaviourUiState(
                            selectedDate = _uiState.value.selectedDate,
                            isLoading = false,
                            errorMessage = "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        loadData(targetDate = _uiState.value.selectedDate, userId = state.currentUserId)
                        triggerCatchup(userId = state.currentUserId)
                    }
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (_uiState.value.selectedDate == date && !_uiState.value.isLoading) return
        loadData(targetDate = date, userId = defaultUserId)
    }

    fun refresh() {
        loadData(targetDate = _uiState.value.selectedDate, userId = defaultUserId)
        triggerCatchup(userId = defaultUserId)
    }

    fun triggerCatchup(userId: String = defaultUserId) {
        if (executeObservationCollectionUseCase == null) return
        if (catchupJob?.isActive == true) return

        catchupJob = viewModelScope.launch(ioDispatcher) {
            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId) return@runWithSession

                    // 1. Verify Usage Access permission
                    val isPermissionGranted = appUsageObservationProvider.isPermissionGranted()
                    if (!isPermissionGranted) {
                        println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_SKIPPED] reason=PERMISSION_DENIED userId=$activeUid")
                        return@runWithSession
                    }

                    // 2. Verify observation consent
                    val settingsResult = appUsageSettingsRepository.getSettings(activeUid)
                    val isObservationEnabled = when (settingsResult) {
                        is Result.Success -> settingsResult.data.isEnabled
                        is Result.Failure -> true
                    }
                    if (!isObservationEnabled) {
                        println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_SKIPPED] reason=CONSENT_DISABLED userId=$activeUid")
                        return@runWithSession
                    }

                    println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_STARTED] userId=$activeUid")
                    val result = executeObservationCollectionUseCase(activeUid)
                    when (result) {
                        is Result.Success -> {
                            println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_COMPLETED] userId=$activeUid collected=${result.data.totalCollected}")
                            if (result.data.totalCollected > 0) {
                                intelligenceOrchestrationScheduler?.scheduleOrchestration(userId = activeUid)
                            }
                            loadData(targetDate = _uiState.value.selectedDate, userId = activeUid)
                        }
                        is Result.Failure -> {
                            when (result.error) {
                                is ObservationExecutionError.AlreadyRunning -> {
                                    println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_ALREADY_RUNNING] userId=$activeUid")
                                }
                                is ObservationExecutionError.CollectionFailed -> {
                                    println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_FAILED] userId=$activeUid error=${result.error.exception.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [DigitalBehaviourViewModel] [CATCHUP_EXCEPTION] error=${e.message}")
            }
        }
    }

    fun acknowledgeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        refresh()
    }

    private fun loadData(targetDate: LocalDate, userId: String) {
        loadJob?.cancel()
        val generation = ++loadGeneration

        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedDate = targetDate,
                isLoading = true,
                errorMessage = null
            )

            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession

                    val isPermissionGranted = appUsageObservationProvider.isPermissionGranted()
                    val settingsResult = appUsageSettingsRepository.getSettings(activeUid)
                    val isObservationEnabled = when (settingsResult) {
                        is Result.Success -> settingsResult.data.isEnabled
                        is Result.Failure -> true
                    }

                    val targetDayMillis = targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

                    val usageResult = getDailyAppUsageIntelligenceUseCase(
                        userId = activeUid,
                        targetDayMillis = targetDayMillis,
                        zoneId = zoneId
                    )

                    val dailyUsage = when (usageResult) {
                        is Result.Success -> usageResult.data
                        is Result.Failure -> null
                    }

                    val enrichedUsage = if (appNameResolver != null && dailyUsage != null) {
                        val enrichedMostUsed = dailyUsage.mostUsedApps.map { item ->
                            if (item.appName == null) {
                                val resolved = appNameResolver.invoke(item.packageName)
                                if (resolved != null) item.copy(appName = resolved) else item
                            } else {
                                item
                            }
                        }
                        val enrichedAppUsages = dailyUsage.appUsages.map { item ->
                            if (item.appName == null) {
                                val resolved = appNameResolver.invoke(item.packageName)
                                if (resolved != null) item.copy(appName = resolved) else item
                            } else {
                                item
                            }
                        }
                        dailyUsage.copy(mostUsedApps = enrichedMostUsed, appUsages = enrichedAppUsages)
                    } else {
                        dailyUsage
                    }

                    val protectionResult = protectionRepository.getActiveProtectionSessionsForUser(activeUid)
                    val activeProtectionSessions = when (protectionResult) {
                        is Result.Success -> protectionResult.data
                        is Result.Failure -> emptyList()
                    }

                    if (isCurrentLoad(userId, generation)) {
                        _uiState.value = DigitalBehaviourUiState(
                            selectedDate = targetDate,
                            isLoading = false,
                            dailyUsage = enrichedUsage,
                            isUsageAccessPermissionGranted = isPermissionGranted,
                            isUsageObservationEnabled = isObservationEnabled,
                            activeProtectionSessions = activeProtectionSessions,
                            errorMessage = if (usageResult is Result.Failure) {
                                usageResult.error.message ?: "Failed to calculate daily usage"
                            } else null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load digital behaviour data"
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                val useCase = GetDailyAppUsageIntelligenceUseCase(
                    appUsageObservationProvider = container.appUsageObservationProvider,
                    appUsageSettingsRepository = container.appUsageObservationSettingsRepository,
                    observationRepository = container.observationRepository,
                    clock = container.clock
                )
                val appNameResolver: (String) -> String? = { pkg ->
                    try {
                        val pm = application.packageManager
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        null
                    }
                }
                DigitalBehaviourViewModel(
                    getDailyAppUsageIntelligenceUseCase = useCase,
                    appUsageObservationProvider = container.appUsageObservationProvider,
                    appUsageSettingsRepository = container.appUsageObservationSettingsRepository,
                    protectionRepository = container.protectionRepository,
                    userSessionProvider = container.userSessionProvider,
                    appNameResolver = appNameResolver,
                    clock = container.clock,
                    executeObservationCollectionUseCase = container.executeObservationCollectionUseCase,
                    intelligenceOrchestrationScheduler = container.intelligenceOrchestrationScheduler
                )
            }
        }
    }
}
