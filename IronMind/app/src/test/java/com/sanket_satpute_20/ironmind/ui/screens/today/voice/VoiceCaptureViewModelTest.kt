package com.sanket_satpute_20.ironmind.ui.screens.today.voice

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.voice.VoiceCaptureErrorType
import com.sanket_satpute_20.ironmind.domain.voice.VoiceFileManager
import com.sanket_satpute_20.ironmind.domain.voice.VoiceRecorder
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var fakeRecorder: FakeVoiceRecorder
    private lateinit var fakeFileManager: FakeVoiceFileManager
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var logger: IronLogger
    private lateinit var viewModel: VoiceCaptureViewModel

    @Before
    fun setup() {
        fakeRecorder = FakeVoiceRecorder()
        fakeFileManager = FakeVoiceFileManager()
        clock = FakeClock()
        clock.setTime(1000000L)
        idGenerator = FakeIdGenerator()
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        viewModel = VoiceCaptureViewModel(
            voiceRecorder = fakeRecorder,
            voiceFileManager = fakeFileManager,
            clock = clock,
            idGenerator = idGenerator,
            logger = logger
        )
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `recording starts and duration ticker updates`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is VoiceCaptureUiState.Recording)
        val recordingState = state as VoiceCaptureUiState.Recording
        assertEquals("00:00", recordingState.formattedDuration)
        assertTrue(fakeRecorder.isRecording)
        assertEquals(1, fakeFileManager.createdFiles.size)

        // Advance virtual time by 3000ms
        clock.advanceTimeBy(3000L)
        advanceTimeBy(3000L)
        runCurrent()

        val updatedState = viewModel.uiState.value
        assertTrue(updatedState is VoiceCaptureUiState.Recording)
        assertEquals("00:03", (updatedState as VoiceCaptureUiState.Recording).formattedDuration)

        // Cleanly stop to cancel ticker
        viewModel.onReleaseStop()
        runCurrent()
    }

    @Test
    fun `duplicate press-start is ignored when already recording`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        val initialSessionId = (viewModel.uiState.value as VoiceCaptureUiState.Recording).sessionId
        assertEquals(1, fakeFileManager.createdFiles.size)

        // Duplicate start attempt
        viewModel.onPressStart()
        runCurrent()

        val currentSessionId = (viewModel.uiState.value as VoiceCaptureUiState.Recording).sessionId
        assertEquals(initialSessionId, currentSessionId)
        assertEquals(1, fakeFileManager.createdFiles.size)

        // Cleanly stop to cancel ticker
        viewModel.onReleaseStop()
        runCurrent()
    }

    @Test
    fun `stop recording cancels ticker and duration no longer changes`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        clock.advanceTimeBy(2000L)
        advanceTimeBy(2000L)
        runCurrent()

        fakeRecorder.durationToReturn = 2000L
        viewModel.onReleaseStop()
        runCurrent()

        assertFalse(fakeRecorder.isRecording)
        val state = viewModel.uiState.value
        assertTrue(state is VoiceCaptureUiState.ProcessingPlaceholder)
        val placeholder = state as VoiceCaptureUiState.ProcessingPlaceholder
        assertEquals("00:02", placeholder.formattedDuration)

        // Advance time further — placeholder duration must remain fixed
        clock.advanceTimeBy(5000L)
        advanceTimeBy(5000L)
        runCurrent()

        val finalState = viewModel.uiState.value
        assertTrue(finalState is VoiceCaptureUiState.ProcessingPlaceholder)
        assertEquals("00:02", (finalState as VoiceCaptureUiState.ProcessingPlaceholder).formattedDuration)
    }

    @Test
    fun `repeated start and stop cycles do not create duplicate ticker jobs`() = runTest {
        // Cycle 1
        viewModel.onPressStart()
        runCurrent()
        clock.advanceTimeBy(1000L)
        advanceTimeBy(1000L)
        runCurrent()
        fakeRecorder.durationToReturn = 1000L
        viewModel.onReleaseStop()
        runCurrent()

        assertTrue(viewModel.uiState.value is VoiceCaptureUiState.ProcessingPlaceholder)
        viewModel.onDismissPlaceholder()
        runCurrent()
        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)

        // Cycle 2
        viewModel.onPressStart()
        runCurrent()
        clock.advanceTimeBy(2000L)
        advanceTimeBy(2000L)
        runCurrent()

        val state2 = viewModel.uiState.value
        assertTrue(state2 is VoiceCaptureUiState.Recording)
        assertEquals("00:02", (state2 as VoiceCaptureUiState.Recording).formattedDuration)

        fakeRecorder.durationToReturn = 2000L
        viewModel.onReleaseStop()
        runCurrent()

        assertTrue(viewModel.uiState.value is VoiceCaptureUiState.ProcessingPlaceholder)
        assertEquals("00:02", (viewModel.uiState.value as VoiceCaptureUiState.ProcessingPlaceholder).formattedDuration)
    }

    @Test
    fun `onReleaseStop with brief tap below 400ms cleans up file and returns to Idle`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        clock.advanceTimeBy(150L)
        fakeRecorder.durationToReturn = 150L

        viewModel.onReleaseStop()
        runCurrent()

        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)
        assertFalse(fakeRecorder.isRecording)
        assertEquals(1, fakeFileManager.deletedFiles.size)
    }

    @Test
    fun `onCancel cleans up audio file, cancels recorder and resets to Idle`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        assertTrue(fakeRecorder.isRecording)
        val file = fakeFileManager.createdFiles.first()

        viewModel.onCancel()
        runCurrent()

        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)
        assertFalse(fakeRecorder.isRecording)
        assertEquals(1, fakeRecorder.cancelledCount)
        assertTrue(fakeFileManager.deletedFiles.contains(file))
    }

    @Test
    fun `permission denial transitions to Error state without starting recorder`() = runTest {
        viewModel.onPermissionDenied()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is VoiceCaptureUiState.Error)
        val errorState = state as VoiceCaptureUiState.Error
        assertEquals(VoiceCaptureErrorType.PERMISSION_DENIED, errorState.errorType)
        assertFalse(fakeRecorder.isRecording)
        assertEquals(0, fakeFileManager.createdFiles.size)
    }

    @Test
    fun `recording failure on start cleans up temp file and enters error state`() = runTest {
        fakeRecorder.failureOnStart = RuntimeException("Hardware mic locked")

        viewModel.onPressStart()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is VoiceCaptureUiState.Error)
        val errorState = state as VoiceCaptureUiState.Error
        assertEquals(VoiceCaptureErrorType.RECORDING_FAILED, errorState.errorType)
        assertEquals("Hardware mic locked", errorState.message)

        assertFalse(fakeRecorder.isRecording)
        assertEquals(1, fakeFileManager.deletedFiles.size)
    }

    @Test
    fun `recording failure on stop cleans up temp file and enters error state`() = runTest {
        fakeRecorder.failureOnStop = RuntimeException("Audio codec error")

        viewModel.onPressStart()
        runCurrent()

        viewModel.onReleaseStop()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is VoiceCaptureUiState.Error)
        val errorState = state as VoiceCaptureUiState.Error
        assertEquals(VoiceCaptureErrorType.RECORDING_FAILED, errorState.errorType)
        assertEquals("Audio codec error", errorState.message)

        assertEquals(1, fakeFileManager.deletedFiles.size)
    }

    @Test
    fun `dismiss placeholder deletes temporary file and returns to Idle`() = runTest {
        viewModel.onPressStart()
        runCurrent()

        clock.advanceTimeBy(1500L)
        fakeRecorder.durationToReturn = 1500L
        viewModel.onReleaseStop()
        runCurrent()

        assertTrue(viewModel.uiState.value is VoiceCaptureUiState.ProcessingPlaceholder)

        viewModel.onDismissPlaceholder()
        runCurrent()

        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)
        assertEquals(1, fakeFileManager.deletedFiles.size)
    }

    @Test
    fun `dismissError resets Error state back to Idle`() = runTest {
        viewModel.onPermissionDenied()
        assertTrue(viewModel.uiState.value is VoiceCaptureUiState.Error)

        viewModel.dismissError()
        assertEquals(VoiceCaptureUiState.Idle, viewModel.uiState.value)
    }

    // --- Test Doubles ---

    private class FakeVoiceRecorder : VoiceRecorder {
        private var _isRecording = false
        override val isRecording: Boolean
            get() = _isRecording

        var failureOnStart: Exception? = null
        var failureOnStop: Exception? = null
        var durationToReturn: Long = 1000L
        var releasedCount: Int = 0
        var cancelledCount: Int = 0
        var recordedFile: File? = null

        override suspend fun startRecording(outputFile: File): Result<Unit, Exception> {
            if (_isRecording) {
                return Result.Failure(IllegalStateException("Already recording"))
            }
            failureOnStart?.let { return Result.Failure(it) }
            _isRecording = true
            recordedFile = outputFile
            return Result.Success(Unit)
        }

        override suspend fun stopRecording(): Result<Long, Exception> {
            if (!_isRecording) {
                return Result.Failure(IllegalStateException("Not recording"))
            }
            _isRecording = false
            failureOnStop?.let { return Result.Failure(it) }
            return Result.Success(durationToReturn)
        }

        override suspend fun cancelRecording(): Result<Unit, Exception> {
            _isRecording = false
            cancelledCount++
            return Result.Success(Unit)
        }

        override fun release() {
            _isRecording = false
            releasedCount++
        }
    }

    private class FakeVoiceFileManager : VoiceFileManager {
        val createdFiles = mutableListOf<File>()
        val deletedFiles = mutableListOf<File>()
        private var idCounter = 0

        override fun createTempRecordingFile(): File {
            val file = File.createTempFile("fake_voice_${++idCounter}", ".m4a")
            createdFiles.add(file)
            return file
        }

        override fun deleteRecordingFile(file: File): Boolean {
            deletedFiles.add(file)
            return if (file.exists()) file.delete() else true
        }

        override fun cleanAllTempRecordings(): Int {
            val count = createdFiles.size
            for (f in createdFiles) {
                deleteRecordingFile(f)
            }
            return count
        }
    }
}
