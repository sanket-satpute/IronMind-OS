package com.sanket_satpute_20.ironmind.ui.screens.today.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.voice.VoiceCaptureErrorType
import com.sanket_satpute_20.ironmind.domain.model.voice.VoiceRecordingSession
import com.sanket_satpute_20.ironmind.domain.voice.VoiceFileManager
import com.sanket_satpute_20.ironmind.domain.voice.VoiceRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed class VoiceCaptureUiState {
    object Idle : VoiceCaptureUiState()

    data class Recording(
        val sessionId: String,
        val startedAt: Long,
        val elapsedDurationMs: Long,
        val formattedDuration: String
    ) : VoiceCaptureUiState()

    data class ProcessingPlaceholder(
        val session: VoiceRecordingSession,
        val formattedDuration: String,
        val message: String
    ) : VoiceCaptureUiState()

    data class Error(
        val errorType: VoiceCaptureErrorType,
        val message: String,
        val canRetry: Boolean = true
    ) : VoiceCaptureUiState()
}

/**
 * ViewModel orchestrating the press-and-hold recording interaction,
 * live duration ticker, state transitions, and audio lifecycle cleanup.
 */
class VoiceCaptureViewModel(
    private val voiceRecorder: VoiceRecorder,
    private val voiceFileManager: VoiceFileManager,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val logger: IronLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Idle)
    val uiState: StateFlow<VoiceCaptureUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null
    private var activeAudioFile: File? = null
    private var recordingStartedAt: Long = 0L
    private var tickerJob: Job? = null

    fun onPressStart() {
        if (_uiState.value is VoiceCaptureUiState.Recording || voiceRecorder.isRecording) {
            return // Guard against duplicate start gestures
        }

        val sessionId = idGenerator.generateId()
        val tempFile = voiceFileManager.createTempRecordingFile()
        activeAudioFile = tempFile
        currentSessionId = sessionId
        recordingStartedAt = clock.currentTimeMillis()

        viewModelScope.launch {
            logger.logLifecycle("VoiceCaptureViewModel", "START_REQUESTED", mapOf("sessionId" to sessionId))
            when (val result = voiceRecorder.startRecording(tempFile)) {
                is Result.Success -> {
                    _uiState.value = VoiceCaptureUiState.Recording(
                        sessionId = sessionId,
                        startedAt = recordingStartedAt,
                        elapsedDurationMs = 0L,
                        formattedDuration = "00:00"
                    )
                    startDurationTicker()
                }
                is Result.Failure -> {
                    tickerJob?.cancel()
                    voiceFileManager.deleteRecordingFile(tempFile)
                    activeAudioFile = null
                    currentSessionId = null
                    _uiState.value = VoiceCaptureUiState.Error(
                        errorType = VoiceCaptureErrorType.RECORDING_FAILED,
                        message = result.error.message ?: "Failed to start microphone recording"
                    )
                }
            }
        }
    }

    fun onReleaseStop() {
        val currentState = _uiState.value
        if (currentState !in listOf<VoiceCaptureUiState>() && currentState !is VoiceCaptureUiState.Recording) {
            return
        }

        tickerJob?.cancel()
        val sessionId = currentSessionId ?: idGenerator.generateId()
        val file = activeAudioFile

        viewModelScope.launch {
            logger.logLifecycle("VoiceCaptureViewModel", "STOP_REQUESTED", mapOf("sessionId" to sessionId))
            when (val result = voiceRecorder.stopRecording()) {
                is Result.Success -> {
                    val durationMs = result.data
                    if (durationMs < 400L || file == null || !file.exists()) {
                        // Gesture was a brief tap rather than an intentional utterance
                        file?.let { voiceFileManager.deleteRecordingFile(it) }
                        activeAudioFile = null
                        currentSessionId = null
                        _uiState.value = VoiceCaptureUiState.Idle
                    } else {
                        val session = VoiceRecordingSession(
                            id = sessionId,
                            audioFile = file,
                            durationMs = durationMs,
                            recordedAt = clock.currentTimeMillis()
                        )
                        activeAudioFile = null
                        currentSessionId = null
                        _uiState.value = VoiceCaptureUiState.ProcessingPlaceholder(
                            session = session,
                            formattedDuration = formatDuration(durationMs),
                            message = "Captured ${formatDuration(durationMs)} of audio. Local analysis placeholder (VP-2)."
                        )
                    }
                }
                is Result.Failure -> {
                    file?.let { voiceFileManager.deleteRecordingFile(it) }
                    activeAudioFile = null
                    currentSessionId = null
                    _uiState.value = VoiceCaptureUiState.Error(
                        errorType = VoiceCaptureErrorType.RECORDING_FAILED,
                        message = result.error.message ?: "Failed to complete audio recording"
                    )
                }
            }
        }
    }

    fun onCancel() {
        tickerJob?.cancel()
        val file = activeAudioFile
        activeAudioFile = null
        currentSessionId = null

        viewModelScope.launch {
            voiceRecorder.cancelRecording()
            file?.let { voiceFileManager.deleteRecordingFile(it) }
            _uiState.value = VoiceCaptureUiState.Idle
            logger.logLifecycle("VoiceCaptureViewModel", "CANCELLED")
        }
    }

    fun onDismissPlaceholder() {
        val currentState = _uiState.value
        if (currentState is VoiceCaptureUiState.ProcessingPlaceholder) {
            voiceFileManager.deleteRecordingFile(currentState.session.audioFile)
        } else {
            activeAudioFile?.let { voiceFileManager.deleteRecordingFile(it) }
        }
        activeAudioFile = null
        currentSessionId = null
        _uiState.value = VoiceCaptureUiState.Idle
    }

    fun onPermissionDenied() {
        _uiState.value = VoiceCaptureUiState.Error(
            errorType = VoiceCaptureErrorType.PERMISSION_DENIED,
            message = "Microphone permission is required to capture voice plans.",
            canRetry = true
        )
    }

    fun dismissError() {
        _uiState.value = VoiceCaptureUiState.Idle
    }

    private fun startDurationTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(200L)
                val current = _uiState.value
                if (current is VoiceCaptureUiState.Recording) {
                    val elapsed = (clock.currentTimeMillis() - current.startedAt).coerceAtLeast(0L)
                    _uiState.value = current.copy(
                        elapsedDurationMs = elapsed,
                        formattedDuration = formatDuration(elapsed)
                    )
                } else {
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        voiceRecorder.release()
        val currentFile = (_uiState.value as? VoiceCaptureUiState.ProcessingPlaceholder)?.session?.audioFile ?: activeAudioFile
        currentFile?.let { voiceFileManager.deleteRecordingFile(it) }
        activeAudioFile = null
    }

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as com.sanket_satpute_20.ironmind.IronMindApplication)
                VoiceCaptureViewModel(
                    voiceRecorder = application.container.voiceRecorder,
                    voiceFileManager = application.container.voiceFileManager,
                    clock = application.container.clock,
                    idGenerator = application.container.idGenerator,
                    logger = application.container.logger
                )
            }
        }
    }
}
