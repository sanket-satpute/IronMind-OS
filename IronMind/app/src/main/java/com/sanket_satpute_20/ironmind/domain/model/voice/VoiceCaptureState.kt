package com.sanket_satpute_20.ironmind.domain.model.voice

import java.io.File

/**
 * State machine representing the voice capture lifecycle in IronMind Voice Planner.
 */
sealed class VoiceCaptureState {
    object Idle : VoiceCaptureState()

    data class Recording(
        val sessionId: String,
        val startedAt: Long,
        val outputFile: File,
        val elapsedDurationMs: Long = 0L
    ) : VoiceCaptureState()

    data class ProcessingPlaceholder(
        val session: VoiceRecordingSession,
        val placeholderMessage: String = "Voice captured. Ready for local analysis in VP-2."
    ) : VoiceCaptureState()

    data class Error(
        val errorType: VoiceCaptureErrorType,
        val message: String,
        val canRetry: Boolean = true
    ) : VoiceCaptureState()
}

enum class VoiceCaptureErrorType {
    PERMISSION_DENIED,
    RECORDING_FAILED,
    STORAGE_UNAVAILABLE,
    CANCELLED
}
