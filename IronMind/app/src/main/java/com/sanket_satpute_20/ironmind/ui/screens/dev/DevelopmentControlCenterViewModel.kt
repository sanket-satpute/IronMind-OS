package com.sanket_satpute_20.ironmind.ui.screens.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sanket_satpute_20.ironmind.IronMindApplication
import com.sanket_satpute_20.ironmind.data.ai.AuthVerificationResult
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerDiagnostic
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerDiagnosticReport
import com.sanket_satpute_20.ironmind.data.ai.ProductionInferenceResult
import com.sanket_satpute_20.ironmind.domain.repository.DevControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DevControlCenterUiState(
    val eventCount: Int = 0,
    val observationCount: Int = 0,
    val memoryCount: Int = 0,
    val patternCount: Int = 0,
    val decisionCount: Int = 0,
    val interventionCount: Int = 0,
    val experimentCount: Int = 0,
    val outboxCount: Int = 0,
    val protectionRuleCount: Int = 0,
    val diagnosticReport: CloudflareWorkerDiagnosticReport? = null,
    val isDiagnosticRunning: Boolean = false,
    val authVerificationResult: AuthVerificationResult? = null,
    val isAuthVerificationRunning: Boolean = false,
    val productionInferenceResult: ProductionInferenceResult? = null,
    val isProductionInferenceRunning: Boolean = false
)

private data class DiagnosticGroup(
    val report: CloudflareWorkerDiagnosticReport?,
    val isDiagRunning: Boolean,
    val authResult: AuthVerificationResult?,
    val isAuthRunning: Boolean
)

class DevelopmentControlCenterViewModel(
    private val devControlRepository: DevControlRepository,
    private val cloudflareWorkerDiagnostic: CloudflareWorkerDiagnostic? = null
) : ViewModel() {

    private val _diagnosticReport = MutableStateFlow<CloudflareWorkerDiagnosticReport?>(null)
    private val _isDiagnosticRunning = MutableStateFlow(false)
    private val _authVerificationResult = MutableStateFlow<AuthVerificationResult?>(null)
    private val _isAuthVerificationRunning = MutableStateFlow(false)
    private val _productionInferenceResult = MutableStateFlow<ProductionInferenceResult?>(null)
    private val _isProductionInferenceRunning = MutableStateFlow(false)

    val uiState: StateFlow<DevControlCenterUiState> = combine(
        combine(
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
        ) { counts -> counts },
        combine(
            _diagnosticReport,
            _isDiagnosticRunning,
            _authVerificationResult,
            _isAuthVerificationRunning
        ) { report, isRunning, authResult, isAuthRunning ->
            DiagnosticGroup(report, isRunning, authResult, isAuthRunning)
        },
        _productionInferenceResult,
        _isProductionInferenceRunning
    ) { counts, diagGroup, prodResult, isProdRunning ->
        DevControlCenterUiState(
            eventCount = counts[0],
            observationCount = counts[1],
            memoryCount = counts[2],
            patternCount = counts[3],
            decisionCount = counts[4],
            interventionCount = counts[5],
            experimentCount = counts[6],
            outboxCount = counts[7],
            protectionRuleCount = counts[8],
            diagnosticReport = diagGroup.report,
            isDiagnosticRunning = diagGroup.isDiagRunning,
            authVerificationResult = diagGroup.authResult,
            isAuthVerificationRunning = diagGroup.isAuthRunning,
            productionInferenceResult = prodResult,
            isProductionInferenceRunning = isProdRunning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevControlCenterUiState()
    )

    fun runCloudflareWorkerDiagnostic() {
        if (cloudflareWorkerDiagnostic == null) return
        viewModelScope.launch {
            _isDiagnosticRunning.value = true
            try {
                val report = cloudflareWorkerDiagnostic.runDiagnostic()
                _diagnosticReport.value = report
            } finally {
                _isDiagnosticRunning.value = false
            }
        }
    }

    fun verifyWorkerAuth() {
        if (cloudflareWorkerDiagnostic == null) return
        viewModelScope.launch {
            _isAuthVerificationRunning.value = true
            try {
                val result = cloudflareWorkerDiagnostic.verifyWorkerAuth()
                _authVerificationResult.value = result
            } finally {
                _isAuthVerificationRunning.value = false
            }
        }
    }

    fun testProductionInference() {
        if (cloudflareWorkerDiagnostic == null) return
        viewModelScope.launch {
            _isProductionInferenceRunning.value = true
            try {
                val result = cloudflareWorkerDiagnostic.testSingleProductionInference()
                _productionInferenceResult.value = result
            } finally {
                _isProductionInferenceRunning.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as IronMindApplication
                return DevelopmentControlCenterViewModel(
                    devControlRepository = application.container.devControlRepository,
                    cloudflareWorkerDiagnostic = application.container.cloudflareWorkerDiagnostic
                ) as T
            }
        }
    }
}
