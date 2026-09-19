package com.sanket_satpute_20.ironmind.ui.screens.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.SaveReflectionUseCase
import com.sanket_satpute_20.ironmind.domain.common.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class DaySummary(
    val totalPlanned: Int = 0,
    val completed: Int = 0,
    val missed: Int = 0,
    val postponed: Int = 0
)

sealed class NightReflectionUiState {
    object Loading : NightReflectionUiState()
    data class Success(val summary: DaySummary, val isSaved: Boolean = false) : NightReflectionUiState()
    data class Error(val message: String) : NightReflectionUiState()
}

class NightReflectionViewModel(
    private val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase,
    private val saveReflectionUseCase: SaveReflectionUseCase,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow<NightReflectionUiState>(NightReflectionUiState.Loading)
    val uiState: StateFlow<NightReflectionUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    fun loadSummary(userId: String = "user-1") { // Default user for V0
        viewModelScope.launch {
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
            
            when (result) {
                is Result.Success -> {
                    val commitments = result.data
                    val summary = DaySummary(
                        totalPlanned = commitments.size,
                        completed = commitments.count { it.status == CommitmentStatus.COMPLETED },
                        missed = commitments.count { it.status == CommitmentStatus.MISSED },
                        postponed = commitments.count { it.status == CommitmentStatus.POSTPONED }
                    )
                    _uiState.value = NightReflectionUiState.Success(summary)
                }
                is Result.Failure -> {
                    _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to load summary")
                }
            }
        }
    }

    fun saveReflection(userId: String = "user-1", content: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is NightReflectionUiState.Success) {
                val result = saveReflectionUseCase(
                    userId = userId,
                    content = content
                )
                when (result) {
                    is Result.Success -> {
                        _uiState.value = currentState.copy(isSaved = true)
                    }
                    is Result.Failure -> {
                        _uiState.value = NightReflectionUiState.Error(result.error.message ?: "Failed to save reflection")
                    }
                }
            }
        }
    }
}
