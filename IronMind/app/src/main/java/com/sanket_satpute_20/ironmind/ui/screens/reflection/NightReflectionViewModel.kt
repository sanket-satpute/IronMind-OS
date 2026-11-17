package com.sanket_satpute_20.ironmind.ui.screens.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.SaveReflectionUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class DaySummary(
    val completed: List<Commitment> = emptyList(),
    val missed: List<Commitment> = emptyList(),
    val postponed: List<Commitment> = emptyList()
)

sealed class NightReflectionUiState {
    object Loading : NightReflectionUiState()
    data class Success(
        val summary: DaySummary,
        val existingReflection: String? = null,
        val isSaved: Boolean = false
    ) : NightReflectionUiState()
    data class Error(val message: String) : NightReflectionUiState()
}

class NightReflectionViewModel(
    private val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase,
    private val saveReflectionUseCase: SaveReflectionUseCase,
    private val reflectionRepository: ReflectionRepository,
    private val clock: Clock,
    private val logger: IronLogger,
    private val userSessionProvider: UserSessionProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<NightReflectionUiState>(NightReflectionUiState.Loading)
    val uiState: StateFlow<NightReflectionUiState> = _uiState.asStateFlow()

    private val _reflectionText = MutableStateFlow("")
    val reflectionText: StateFlow<String> = _reflectionText.asStateFlow()

    private val currentUserId: String
        get() = userSessionProvider?.getActiveUserId() ?: SessionState.DEFAULT_LEGACY_USER_ID

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var observedSessionUserId: String? = null
    private var loadedUserId: String? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        if (userSessionProvider == null) {
            loadSummary(SessionState.DEFAULT_LEGACY_USER_ID)
            return
        }

        viewModelScope.launch {
            userSessionProvider.sessionState.collect { state ->
                when (state) {
                    is SessionState.Initializing -> {
                        loadGeneration++
                        loadedUserId = null
                        loadJob?.cancel()
                        _uiState.value = NightReflectionUiState.Loading
                        _reflectionText.value = ""
                    }
                    is SessionState.ReconciliationFailed -> {
                        loadGeneration++
                        loadedUserId = null
                        loadJob?.cancel()
                        _uiState.value = NightReflectionUiState.Error(
                            "Session reconciliation failed: ${state.error.message ?: "Unknown error"}"
                        )
                        _reflectionText.value = ""
                    }
                    is SessionState.OfflineFallback,
                    is SessionState.Authenticated -> {
                        if (observedSessionUserId != state.currentUserId) {
                            observedSessionUserId = state.currentUserId
                            _reflectionText.value = ""
                            _uiState.value = NightReflectionUiState.Loading
                        }
                        loadSummary(state.currentUserId)
                    }
                }
            }
        }
    }

    fun updateReflectionText(text: String) {
        _reflectionText.value = text
    }

    fun loadSummary(userId: String = currentUserId) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadedUserId = null
        loadJob = viewModelScope.launch {
            logger.logLifecycle("Reflection", "LOAD_START")
            _uiState.value = NightReflectionUiState.Loading

            // Calculate start and end of today
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = clock.currentTimeMillis()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || !isCurrentLoad(userId, generation)) return@runWithSession
                    val result = getCommitmentsForDateRangeUseCase(activeUid, startTime, endTime)
                    if (result is Result.Failure) {
                        if (isCurrentLoad(userId, generation)) {
                            _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to load summary")
                        }
                        return@runWithSession
                    }
                    val commitments = (result as Result.Success).data
                    if (commitments.any { it.userId != activeUid }) {
                        _uiState.value = NightReflectionUiState.Error("Commitments do not belong to this session")
                        return@runWithSession
                    }

                    val reflectionResult = reflectionRepository.getReflectionsForDateRange(activeUid, startTime, endTime)
                    if (reflectionResult is Result.Failure) {
                        if (isCurrentLoad(userId, generation)) {
                            _uiState.value = NightReflectionUiState.Error(reflectionResult.error.message ?: "Failed to load reflection")
                        }
                        return@runWithSession
                    }
                    val reflections = (reflectionResult as Result.Success).data
                    if (reflections.any { it.userId != activeUid }) {
                        _uiState.value = NightReflectionUiState.Error("Reflections do not belong to this session")
                        return@runWithSession
                    }
                    if (!isCurrentLoad(userId, generation)) return@runWithSession

                    val existingReflection = reflections.firstOrNull()?.content
                    val summary = DaySummary(
                        completed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.COMPLETED },
                        missed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.MISSED },
                        postponed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.POSTPONED }
                    )
                    loadedUserId = activeUid
                    _uiState.value = NightReflectionUiState.Success(
                        summary = summary,
                        existingReflection = existingReflection,
                        isSaved = existingReflection != null
                    )
                    logger.logLifecycle("Reflection", "LOAD_SUCCESS", mapOf("hasExisting" to (existingReflection != null).toString()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLoad(userId, generation)) {
                    _uiState.value = NightReflectionUiState.Error(e.message ?: "Failed to load summary")
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

    fun saveReflection(userId: String = currentUserId, content: String) {
        viewModelScope.launch {
            if (content.isBlank()) {
                logger.logLifecycle("Reflection", "VALIDATION_FAILURE", mapOf("reason" to "Blank reflection"))
                return@launch
            }
            logger.logLifecycle("Reflection", "SAVE_START")
            val currentState = _uiState.value
            if (currentState is NightReflectionUiState.Success) {
                try {
                userSessionProvider.runWithSession { activeUid ->
                    if (activeUid != userId || loadedUserId != activeUid || !isCurrentSession(activeUid)) return@runWithSession
                    val result = saveReflectionUseCase(
                        userId = activeUid,
                        content = content
                    )
                    when (result) {
                        is Result.Success -> if (isCurrentSession(activeUid) && loadedUserId == activeUid) {
                            logger.logLifecycle("Reflection", "SAVE_SUCCESS")
                            _uiState.value = currentState.copy(isSaved = true)
                        }
                        is Result.Failure -> if (isCurrentSession(activeUid) && loadedUserId == activeUid) {
                            logger.logLifecycle("Reflection", "SAVE_FAILURE", mapOf("error" to (result.error.message ?: "Unknown")))
                            _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to save reflection")
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as com.sanket_satpute_20.ironmind.IronMindApplication)
                val getCommitmentsForDateRangeUseCase = application.container.getCommitmentsForDateRangeUseCase
                val saveReflectionUseCase = application.container.saveReflectionUseCase
                val reflectionRepository = application.container.reflectionRepository
                val clock = application.container.clock
                val logger = application.container.logger

                NightReflectionViewModel(
                    getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
                    saveReflectionUseCase = saveReflectionUseCase,
                    reflectionRepository = reflectionRepository,
                    clock = clock,
                    logger = logger,
                    userSessionProvider = application.container.userSessionProvider
                )
            }
        }
    }
}
