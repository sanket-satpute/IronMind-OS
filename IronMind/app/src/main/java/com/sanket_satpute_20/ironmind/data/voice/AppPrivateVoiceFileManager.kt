package com.sanket_satpute_20.ironmind.data.voice

import android.content.Context
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.voice.VoiceFileManager
import java.io.File

/**
 * Concrete file manager implementing app-private temporary voice storage.
 * All audio files reside in `context.cacheDir/voice_recordings` and are private to the application.
 */
class AppPrivateVoiceFileManager(
    private val context: Context,
    private val idGenerator: IdGenerator
) : VoiceFileManager {

    private val voiceDir: File
        get() {
            val dir = File(context.cacheDir, "voice_recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    override fun createTempRecordingFile(): File {
        val file = File(voiceDir, "voice_capture_${idGenerator.generateId()}.m4a")
        if (file.exists()) {
            file.delete()
        }
        return file
    }

    override fun deleteRecordingFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun cleanAllTempRecordings(): Int {
        return try {
            val files = voiceDir.listFiles() ?: emptyArray()
            var deletedCount = 0
            for (f in files) {
                if (f.delete()) {
                    deletedCount++
                }
            }
            deletedCount
        } catch (e: Exception) {
            0
        }
    }
}
