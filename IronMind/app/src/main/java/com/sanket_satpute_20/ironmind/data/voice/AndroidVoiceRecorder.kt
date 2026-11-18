package com.sanket_satpute_20.ironmind.data.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Concrete Android implementation of [VoiceRecorder] using [MediaRecorder].
 * Supports API 30+ (using MediaRecorder(context) on API 31+ and legacy constructor on API 30).
 * Enforces mutual exclusion to strictly prevent concurrent recording sessions,
 * and guarantees hardware resource release on failure or cancellation.
 */
class AndroidVoiceRecorder(
    private val context: Context,
    private val clock: Clock,
    private val logger: IronLogger
) : VoiceRecorder {

    private val mutex = Mutex()
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    @Volatile
    private var _isRecording = false
    override val isRecording: Boolean
        get() = _isRecording

    override suspend fun startRecording(outputFile: File): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_isRecording) {
                return@withContext Result.Failure(IllegalStateException("Recording already in progress"))
            }

            try {
                releaseInternal()

                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44100)
                recorder.setAudioEncodingBitRate(96000)
                recorder.setOutputFile(outputFile.absolutePath)

                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                currentOutputFile = outputFile
                recordingStartTimeMs = clock.currentTimeMillis()
                _isRecording = true

                logger.logLifecycle("VoiceRecorder", "RECORDING_STARTED", mapOf("path" to outputFile.name))
                Result.Success(Unit)
            } catch (e: Exception) {
                logger.logLifecycle("VoiceRecorder", "START_ERROR", mapOf("error" to (e.message ?: "Unknown")))
                releaseInternal()
                Result.Failure(e)
            }
        }
    }

    override suspend fun stopRecording(): Result<Long, Exception> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!_isRecording || mediaRecorder == null) {
                return@withContext Result.Failure(IllegalStateException("No active recording to stop"))
            }

            val durationMs = (clock.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(0L)
            try {
                mediaRecorder?.stop()
                logger.logLifecycle("VoiceRecorder", "RECORDING_STOPPED", mapOf("durationMs" to durationMs))
                Result.Success(durationMs)
            } catch (e: Exception) {
                logger.logLifecycle("VoiceRecorder", "STOP_ERROR", mapOf("error" to (e.message ?: "Unknown")))
                Result.Failure(e)
            } finally {
                releaseInternal()
            }
        }
    }

    override suspend fun cancelRecording(): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!_isRecording) {
                return@withContext Result.Success(Unit)
            }

            try {
                mediaRecorder?.stop()
            } catch (ignored: Exception) {
                // Ignore stop error during cancellation
            } finally {
                releaseInternal()
                currentOutputFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }
                currentOutputFile = null
            }

            logger.logLifecycle("VoiceRecorder", "RECORDING_CANCELLED")
            Result.Success(Unit)
        }
    }

    override fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (ignored: Exception) {
            // Ignore release exceptions
        } finally {
            mediaRecorder = null
            _isRecording = false
        }
    }
}
