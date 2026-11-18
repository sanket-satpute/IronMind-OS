package com.sanket_satpute_20.ironmind.domain.voice

import com.sanket_satpute_20.ironmind.domain.common.Result
import java.io.File

/**
 * Domain port for managing the physical audio recording lifecycle.
 * Implementations must be thread-safe, handle device interrupts,
 * and guarantee release of hardware recording resources on failure or cancellation.
 */
interface VoiceRecorder {
    val isRecording: Boolean

    suspend fun startRecording(outputFile: File): Result<Unit, Exception>
    suspend fun stopRecording(): Result<Long, Exception> // Returns durationMs
    suspend fun cancelRecording(): Result<Unit, Exception>
    fun release()
}
