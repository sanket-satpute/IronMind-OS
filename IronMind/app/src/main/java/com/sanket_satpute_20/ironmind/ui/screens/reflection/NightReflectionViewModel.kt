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
    private val logger: IronLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<NightReflectionUiState>(NightReflectionUiState.Loading)
    val uiState: StateFlow<NightReflectionUiState> = _uiState.asStateFlow()

    private val _reflectionText = MutableStateFlow("")
    val reflectionText: StateFlow<String> = _reflectionText.asStateFlow()

    init {
        loadSummary()
    }

    fun updateReflectionText(text: String) {
        _reflectionText.value = text
    }

    fun loadSummary(userId: String = "user-1") { // Default user for V0
        viewModelScope.launch {
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

            val result = getCommitmentsForDateRangeUseCase(userId, startTime, endTime)
            val reflectionResult = reflectionRepository.getReflectionsForDateRange(userId, startTime, endTime)
            
            val existingReflection = if (reflectionResult is Result.Success && reflectionResult.data.isNotEmpty()) {
                reflectionResult.data.first().content
            } else {
                null
            }
            
            when (result) {
                is Result.Success -> {
                    val commitments = result.data
                    val summary = DaySummary(
                        completed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.COMPLETED },
                        missed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.MISSED },
                        postponed = commitments.filter { it.status == com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.POSTPONED }
                    )
                    _uiState.value = NightReflectionUiState.Success(
                        summary = summary,
                        existingReflection = existingReflection,
                        isSaved = existingReflection != null
                    )
                    logger.logLifecycle("Reflection", "LOAD_SUCCESS", mapOf("hasExisting" to (existingReflection != null).toString()))
                }
                is Result.Failure -> {
                    logger.logLifecycle("Reflection", "LOAD_FAILURE", mapOf("error" to (result.error.message ?: "Unknown")))
                    _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to load summary")
                }
            }
        }
    }

    fun saveReflection(userId: String = "user-1", content: String) {
        viewModelScope.launch {
            if (content.isBlank()) {
                logger.logLifecycle("Reflection", "VALIDATION_FAILURE", mapOf("reason" to "Blank reflection"))
                return@launch
            }
            logger.logLifecycle("Reflection", "SAVE_START")
            val currentState = _uiState.value
            if (currentState is NightReflectionUiState.Success) {
                val result = saveReflectionUseCase(
                    userId = userId,
                    content = content
                )
                when (result) {
                    is Result.Success -> {
                        logger.logLifecycle("Reflection", "SAVE_SUCCESS")
                        _uiState.value = currentState.copy(isSaved = true)
                    }
                    is Result.Failure -> {
                        logger.logLifecycle("Reflection", "SAVE_FAILURE", mapOf("error" to (result.error.message ?: "Unknown")))
                        _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to save reflection")
                    }
                }
            }
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
                    logger = logger
                )
            }
        }
    }
}
