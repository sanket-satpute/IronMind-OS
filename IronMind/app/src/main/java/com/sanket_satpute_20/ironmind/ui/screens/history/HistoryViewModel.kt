package com.sanket_satpute_20.ironmind.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.domain.usecase.history.GetTimelineUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val timelineItems: List<TimelineItem>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

class HistoryViewModel(
    private val getTimelineUseCase: GetTimelineUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val currentUserId = "user-1"

    init {
        loadTimeline()
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            when (val result = getTimelineUseCase(currentUserId)) {
                is Result.Success -> {
                    _uiState.value = HistoryUiState.Success(result.data)
                }
                is Result.Failure -> {
                    _uiState.value = HistoryUiState.Error(result.error.message ?: "Failed to load timeline")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronMindApplication)
                val container = application.container
                HistoryViewModel(
                    getTimelineUseCase = container.getTimelineUseCase
                )
            }
        }
    }
}
