package com.sanket_satpute_20.ironmind.data.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPrivateVoiceFileManagerTest {

    private lateinit var context: Context
    private lateinit var fileManager: AppPrivateVoiceFileManager
    private var idCounter = 0

    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "test_${++idCounter}"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = AppPrivateVoiceFileManager(context, fakeIdGenerator)
        fileManager.cleanAllTempRecordings()
    }

    @After
    fun tearDown() {
        fileManager.cleanAllTempRecordings()
    }

    @Test
    fun `createTempRecordingFile creates path inside app-private cache voice_recordings directory`() {
        val file = fileManager.createTempRecordingFile()
        val expectedParent = File(context.cacheDir, "voice_recordings")

        assertEquals(expectedParent.canonicalPath, file.parentFile?.canonicalPath)
        assertTrue(file.name.startsWith("voice_capture_test_1"))
        assertTrue(file.name.endsWith(".m4a"))
    }

    @Test
    fun `deleteRecordingFile deletes existing file and returns true`() {
        val file = fileManager.createTempRecordingFile()
        file.writeText("fake audio data")
        assertTrue(file.exists())

        val result = fileManager.deleteRecordingFile(file)
        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteRecordingFile returns true if file does not exist`() {
        val file = File(context.cacheDir, "non_existent.m4a")
        assertFalse(file.exists())

        val result = fileManager.deleteRecordingFile(file)
        assertTrue(result)
    }

    @Test
    fun `cleanAllTempRecordings deletes all files in voice directory and returns count`() {
        val file1 = fileManager.createTempRecordingFile()
        file1.writeText("audio 1")
        val file2 = fileManager.createTempRecordingFile()
        file2.writeText("audio 2")

        assertTrue(file1.exists())
        assertTrue(file2.exists())

        val deletedCount = fileManager.cleanAllTempRecordings()
        assertEquals(2, deletedCount)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }
}
