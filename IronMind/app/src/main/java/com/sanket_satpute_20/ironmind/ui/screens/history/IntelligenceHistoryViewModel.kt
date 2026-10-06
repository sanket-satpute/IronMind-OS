package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryItem
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetIntelligenceHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface IntelligenceHistoryUiState {
    data object Loading : IntelligenceHistoryUiState
    data class Success(val items: List<IntelligenceHistoryItem>) : IntelligenceHistoryUiState
    data class Error(val message: String) : IntelligenceHistoryUiState
}

class IntelligenceHistoryViewModel(
    private val getIntelligenceHistoryUseCase: GetIntelligenceHistoryUseCase,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow<IntelligenceHistoryUiState>(IntelligenceHistoryUiState.Loading)
    val uiState: StateFlow<IntelligenceHistoryUiState> = _uiState.asStateFlow()

    // Using a hardcoded user ID for V0.5 as session management doesn't exist yet
    private val currentUserId = "user-1"

    init {
        loadIntelligenceHistory()
    }

    fun loadIntelligenceHistory() {
        viewModelScope.launch {
            _uiState.value = IntelligenceHistoryUiState.Loading

            val now = clock.currentTimeMillis()
            val startTime = now - HISTORY_WINDOW_MS
            val endTime = now

            when (val result = getIntelligenceHistoryUseCase(
                userId = currentUserId,
                startTime = startTime,
                endTime = endTime,
                limit = HISTORY_LIMIT
            )) {
                is Result.Success -> {
                    _uiState.value = IntelligenceHistoryUiState.Success(result.data)
                }
                is Result.Failure -> {
                    _uiState.value = IntelligenceHistoryUiState.Error(
                        result.error.message ?: "Failed to load intelligence history"
                    )
                }
            }
        }
    }

    companion object {
        /** 30-day bounded history window. */
        private const val HISTORY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /** Maximum number of recommendations to retrieve. */
        private const val HISTORY_LIMIT = 50

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                IntelligenceHistoryViewModel(
                    getIntelligenceHistoryUseCase = container.getIntelligenceHistoryUseCase,
                    clock = container.clock
                )
            }
        }
    }
}
