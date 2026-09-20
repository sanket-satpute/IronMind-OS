package com.sanket_satpute_20.ironmind.ui.screens.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.domain.repository.DevControlRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DevControlCenterUiState(
    val eventCount: Int = 0,
    val observationCount: Int = 0,
    val memoryCount: Int = 0,
    val patternCount: Int = 0,
    val decisionCount: Int = 0,
    val interventionCount: Int = 0,
    val experimentCount: Int = 0,
    val outboxCount: Int = 0,
    val protectionRuleCount: Int = 0
)

class DevelopmentControlCenterViewModel(
    private val devControlRepository: DevControlRepository
) : ViewModel() {

    val uiState: StateFlow<DevControlCenterUiState> = combine(
        listOf(
            devControlRepository.getEventCount(),
            devControlRepository.getObservationCount(),
            devControlRepository.getMemoryCount(),
            devControlRepository.getPatternCount(),
            devControlRepository.getDecisionCount(),
            devControlRepository.getInterventionCount(),
            devControlRepository.getExperimentCount(),
            devControlRepository.getOutboxCount(),
            devControlRepository.getProtectionRuleCount()
        )
    ) { counts ->
        DevControlCenterUiState(
            eventCount = counts[0],
            observationCount = counts[1],
            memoryCount = counts[2],
            patternCount = counts[3],
            decisionCount = counts[4],
            interventionCount = counts[5],
            experimentCount = counts[6],
            outboxCount = counts[7],
            protectionRuleCount = counts[8]
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevControlCenterUiState()
    )

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return DevelopmentControlCenterViewModel(
                    (application as IronMindApplication).container.devControlRepository
                ) as T
            }
        }
    }
}
