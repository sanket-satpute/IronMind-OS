package com.sanket_satpute_20.ironmind.domain.voice

import java.io.File

/**
 * Port for managing temporary audio files in app-private storage.
 * Enforces privacy rules: files are stored only in app-private storage and deleted upon session completion.
 */
interface VoiceFileManager {
    fun createTempRecordingFile(): File
    fun deleteRecordingFile(file: File): Boolean
    fun cleanAllTempRecordings(): Int
}
