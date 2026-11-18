package com.sanket_satpute_20.ironmind.domain.model.voice

import java.io.File

/**
 * Immutable record of a completed voice capture session stored in app-private storage.
 * Audio is strictly local-first and cleaned up after processing or dismissal.
 */
data class VoiceRecordingSession(
    val id: String,
    val audioFile: File,
    val durationMs: Long,
    val recordedAt: Long,
    val isCleanedUp: Boolean = false
)
