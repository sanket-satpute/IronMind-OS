# IronMind Voice Planner — Phase VP-0 + VP-1
## Architecture Integration Audit and Voice Capture Foundation Report

**Phase:** VP-0 (Architecture Integration Audit) + VP-1 (Voice Capture Foundation)
**Execution Mode:** Autonomous Local Engineering
**Repository:** `https://github.com/sanket-satpute_20/IronMind-OS.git`
**Base Commit SHA:** `bb48939f621df403b845867fd48969c39204d9a6`
**Target Branch:** `main`
**Date:** November 2026

---

## 1. Executive Summary

Phase VP-0 and VP-1 mark the inception of the native **IronMind Voice Planner** within the IronMind Android application. Guided by the finalized product decisions:
- **Task creation:** Automatic generation for sufficiently clear, validated intent with full review capabilities.
- **Microphone interaction:** Press-and-hold to record; release to stop.
- **Processing:** Hybrid execution model — local voice capture with AI-powered intent extraction and plan synthesis.
- **Privacy:** Strict local-first audio handling — audio is written exclusively to app-private cache directories, never uploaded raw to any cloud service, and purged immediately on cancellation, dismissal, or session cleanup.

This phase established:
1. **Architecture Integration Audit (VP-0):** An exhaustive analysis of all 10 architectural subsystems of the IronMind codebase to ensure seamless integration without architectural drift or bloat.
2. **Integration Contracts:** Defined 5 rigorous contracts governing Voice Capture, Voice Intent, Proposed Task/Schedule, Confirmation/Review, and Persistence/Sync.
3. **Voice Capture Foundation (VP-1):** Full domain, data, and presentation implementation of press-and-hold audio capture, `VoiceCaptureViewModel`, `VoicePlannerCaptureCard`, `AndroidVoiceRecorder`, and `AppPrivateVoiceFileManager`.
4. **Test Scheduler Hang Fix & Test Closure:** Diagnosed and eliminated an infinite virtual-time advancement loop in `VoiceCaptureViewModelTest`, implemented `AppPrivateVoiceFileManagerTest`, verified 100% of the repository's 2,040+ unit tests, and validated the APK build through `assembleDebug`.

---

## 2. PART B — Architecture Integration Audit

### 2.1 Entry Points & User Flows
- **Existing Navigation:** `IronMindNavHost` (`IronMindNavHost.kt`) manages top-level routes (`BottomNavItem`: `Today`, `Tasks`, `Goals`, `Plans`, `History`, `Settings`).
- **Target Surface:** `TodayScreen` (`TodayScreen.kt`) serves as the primary dashboard where daily commitments, daily briefings, and proposed actions are rendered.
- **Integration Placement:** `VoicePlannerCaptureCard` is integrated directly into `TodayScreenContent` and `TodayContent`. It appears prominently at the top of the Today screen in both empty and populated states, providing instantaneous access to voice-based planning without navigating away.
- **Navigation Compatibility:** No new global destinations were required for VP-1. For VP-3/VP-4 review flows, the proposed tasks will integrate into existing bottom sheets or dialogs alongside existing `ProposedAction` components.

### 2.2 State Management & Lifecycle
- **ViewModel Architecture:** Uses `androidx.lifecycle.ViewModel` with `StateFlow<VoiceCaptureUiState>`.
- **UI State Model:** `VoiceCaptureUiState` is an exhaustive sealed hierarchy:
  - `Idle`: Resting state awaiting user interaction.
  - `Recording(sessionId, startedAt, elapsedDurationMs, formattedDuration)`: Active recording with live duration ticker.
  - `ProcessingPlaceholder(session, formattedDuration, message)`: Recording complete, audio file ready for local transcription and intent extraction (VP-2 transition point).
  - `Error(errorType, message, canRetry)`: Structured error reporting (permission denied, recorder hardware failure, storage error).
- **Lifecycle Safety:** ViewModel coordinates coroutine cancellation in `onCleared()`, stops recording cleanly if active, releases media recorder hardware resources, and deletes transient audio files to guarantee no orphaned temporary files or leaks.

### 2.3 Domain Models & Data Structures
- **Domain Separation:** Domain models are decoupled from Android framework APIs:
  - `VoiceRecordingSession`: Immutable value object capturing `sessionId`, `audioFile`, `durationMs`, and `recordedAt`.
  - `VoiceCaptureState` & `VoiceCaptureErrorType`: Strongly typed domain state representations.
- **Compatibility with Target Entities:** Mapped alignment with `com.sanket_satpute_20.ironmind.domain.model.task.Task`, `Commitment`, `Goal`, `Plan`, and `SpeechInput`. Tasks generated from voice planning in VP-3 will set `EntitySource.VOICE` or preserve audio session references for auditing.

### 2.4 Repository & Persistence
- **Storage Strategy:** Temporary audio recordings reside solely on disk in `context.cacheDir/voice_recordings/` managed by `VoiceFileManager`.
- **Clean Retention Policy:** Audio files are transient by contract. Once intent extraction is completed in future phases (or upon user cancellation/dismissal in VP-1), audio files are wiped from disk.
- **Room Integration:** No audio BLOBs or file paths are stored in the Room database, keeping SQLite storage lightweight and preventing backup bloat.

### 2.5 AI & Natural Language Processing
- **Existing AI Infrastructure:** `IronMindAI` interface backed by `GeminiIronMindAI` and `CloudflareWorkerIronMindAITest`/`CloudflareWorkerIronMindAI`.
- **Use Cases:** `ExtractIntentUseCase` (`ExtractIntentUseCase.kt`) extracts structured intent from raw text.
- **VP-2 / VP-3 Bridge:** In VP-1, a clear placeholder state `VoiceCaptureUiState.ProcessingPlaceholder` provides the exact hand-off boundary. In VP-2, on-device Speech-to-Text (`SpeechToTextProvider`) will convert the `.m4a` file to text, which is then fed directly into `ExtractIntentUseCase` for task and schedule synthesis.

### 2.6 Permissions & System Services
- **Microphone Permission:** `android.permission.RECORD_AUDIO` is declared in `AndroidManifest.xml`.
- **Runtime Flow:** Handled gracefully via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` in Compose. If permission is denied, the UI transitions to `VoiceCaptureUiState.Error(PERMISSION_DENIED)` with clear guidance and retry capability.
- **No Background Listening:** Microphone hardware is acquired ONLY while the user is physically pressing and holding the recording card, and released immediately on pointer release or cancellation.

### 2.7 Offline-First Architecture & Sync
- **Air-Gapped Recording:** Audio capture, file generation, and file deletion function 100% offline with zero network connectivity.
- **Sync Safety:** No voice recordings enter `OutboxRepository` or `SyncRepository`. The sync engine is completely shielded from high-bandwidth audio data.

### 2.8 Notifications & Background Work
- **WorkManager Interaction:** No WorkManager jobs are triggered during voice capture, preventing battery drain or unwanted background wakeups.
- **Active Recording Isolation:** Audio capture is synchronous with user interaction; no sticky foreground notifications are required because recording is strictly interactive (press-and-hold).

### 2.9 Session & Identity
- **Session Isolation:** `VoiceCaptureViewModel` integrates with `UserSessionProvider` and `IdGenerator`. Each recording session receives a unique UUID.
- **Multi-Tenant Protection:** On user sign-out or session invalidation, `cleanAllTempRecordings()` purges any residual recordings.

### 2.10 Dependency Injection
- **DI Container:** `AppContainer` (`AppContainerImpl` in `AppContainer.kt`) is updated cleanly:
  - `idGenerator: IdGenerator` exposed on interface and implemented.
  - `voiceFileManager: VoiceFileManager` provided via `AppPrivateVoiceFileManager(context, idGenerator)`.
  - `voiceRecorder: VoiceRecorder` provided via `AndroidVoiceRecorder(context, clock, logger)`.
- **Factory Provider:** `VoiceCaptureViewModel.Factory` retrieves dependencies cleanly from `(context.applicationContext as IronMindApplication).appContainer`.

---

## 3. PART C — Integration Contracts

| Contract Area | Interface / Model | Guarantee |
|---|---|---|
| **Voice Capture Contract** | `VoiceRecorder`, `VoiceFileManager` | Thread-safe Mutex-guarded recording; guaranteed release of `MediaRecorder`; private cache-only file paths (`cacheDir/voice_recordings/`); sub-400ms taps treated as inadvertent and cleaned up automatically. |
| **Voice Intent Contract** | `VoiceRecordingSession` -> `SpeechInput` | Bridges captured audio to text transcription in VP-2 without leaking file handles. |
| **Proposed Task & Schedule Contract** | `ExtractIntentUseCase` -> `ProposedAction` | Output schema aligned with existing `TodayScreen` proposed action and planning workflows. |
| **User Review & Confirmation Contract** | `VoiceCaptureUiState.ProcessingPlaceholder` | Explicit user agency: option to confirm, edit, or dismiss with automated file destruction upon dismissal. |
| **Persistence & Sync Contract** | `AppPrivateVoiceFileManager` | Zero network overhead; zero database bloat; offline-first guarantee. |

---

## 4. Implementation Details (VP-1)

### 4.1 Source Components Created

1. **`com.sanket_satpute_20.ironmind.domain.model.voice.VoiceRecordingSession`**
   - Immutable data class holding `sessionId`, `audioFile: File`, `durationMs: Long`, `recordedAt: Long`.

2. **`com.sanket_satpute_20.ironmind.domain.model.voice.VoiceCaptureState`**
   - Sealed interface defining `Idle`, `Recording`, `ProcessingPlaceholder`, and `Error`.
   - Enum `VoiceCaptureErrorType` (`PERMISSION_DENIED`, `RECORDER_INITIALIZATION_FAILED`, `RECORDING_FAILED`, `FILE_ERROR`).

3. **`com.sanket_satpute_20.ironmind.domain.voice.VoiceRecorder`**
   - Domain port interface:
     - `suspend fun startRecording(outputFile: File): Result<Unit>`
     - `suspend fun stopRecording(): Result<Long>`
     - `suspend fun cancelRecording(): Result<Unit>`
     - `fun isRecording(): Boolean`
     - `fun release()`

4. **`com.sanket_satpute_20.ironmind.domain.voice.VoiceFileManager`**
   - Domain port interface:
     - `fun createTempRecordingFile(): File`
     - `fun deleteRecordingFile(file: File): Boolean`
     - `fun cleanAllTempRecordings(): Int`

5. **`com.sanket_satpute_20.ironmind.data.voice.AppPrivateVoiceFileManager`**
   - Concrete implementation utilizing `context.cacheDir/voice_recordings/`.
   - Creates uniquely named `voice_capture_<uuid>.m4a` files.
   - Cleans files safely and deletes orphaned temp files.

6. **`com.sanket_satpute_20.ironmind.data.voice.AndroidVoiceRecorder`**
   - Concrete `MediaRecorder` wrapper using `Mutex` for hardware serialization.
   - Configures `MIC` audio source, `MPEG_4` output format, `AAC` audio encoder at 128 kbps / 44.1 kHz.
   - API 31+ compatible (`MediaRecorder(context)` constructor) with fallback to deprecated constructor for older runtimes.
   - Safe error handling and resource cleanup on hardware exceptions.

7. **`com.sanket_satpute_20.ironmind.ui.screens.today.voice.VoiceCaptureViewModel`**
   - Manages state machine transitions.
   - Employs a coroutine duration ticker updating every 200ms while recording is active.
   - Discards brief taps (< 400ms) to prevent accidental recordings.
   - Ensures single-deletion hygiene in `onDismissPlaceholder()` and `onCleared()`.

8. **`com.sanket_satpute_20.ironmind.ui.screens.today.voice.VoicePlannerCaptureCard`**
   - Jetpack Compose card with smooth pulsing animations (`animateFloat` pulse effect while recording).
   - Press-and-hold gesture detection using `pointerInput` with `awaitPointerEventScope` and `tryAwaitRelease()`.
   - Live duration readout (`00:04`).
   - Integrated microphone permission check and request launcher.
   - VP-2 placeholder feedback display with Dismiss action.

### 4.2 Integration Touchpoints Modified

1. **`IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/di/AppContainer.kt`**
   - Exposed `idGenerator`, `voiceRecorder`, and `voiceFileManager` on `AppContainer` interface and `DefaultAppContainer`.

2. **`IronMind/app/src/main/java/com/sanket_satpute_20/ironmind/ui/screens/today/TodayScreen.kt`**
   - Integrated `VoicePlannerCaptureCard` in `TodayScreenContent` and `TodayContent` (both empty state and populated state).
   - Fully backward-compatible default parameters (`voiceCaptureViewModel: VoiceCaptureViewModel? = null`).

---

## 5. Test Scheduler Hang Diagnosis & Resolution

### 5.1 Root Cause Analysis
During initial execution of `VoiceCaptureViewModelTest`, the Gradle test worker stalled indefinitely at `TestScope.advanceUntilIdle()`.
- **Thread Dump Evidence:** PID 12324 thread dump pinpointed `advanceUntilIdle()` in `kotlinx.coroutines.test`.
- **Mechanism:** `startDurationTicker()` launched a recurring coroutine:
  ```kotlin
  while (isActive) {
      delay(200L)
      // update elapsed duration
  }
  ```
  `advanceUntilIdle()` continually executes scheduled tasks and advances virtual time until the queue is exhausted. Because the ticker loop scheduled another delay immediately in each iteration, the queue was never empty, causing an infinite loop advancing virtual time.

### 5.2 Remediation
1. In `VoiceCaptureViewModelTest.kt`, replaced unbounded `advanceUntilIdle()` calls with `runCurrent()` (for executing tasks at the current virtual timestamp) and explicit, bounded `advanceTimeBy(millis)` when testing duration progression.
2. Verified all start/stop cycles explicitly cancel the ticker before test completion.
3. Fixed double deletion in `VoiceCaptureViewModel.onDismissPlaceholder()`.
4. Result: Targeted test suite execution time dropped from infinite hang to **0.37 seconds** for all 12 tests.

---

## 6. Verification and Validation Results

### 6.1 Targeted Voice Unit Tests
- **`VoiceCaptureViewModelTest`**: 12/12 passed (0.373s)
  - `initial state is Idle`
  - `recording starts and duration ticker updates`
  - `duplicate press-start is ignored when already recording`
  - `stop recording cancels ticker and duration no longer changes`
  - `repeated start and stop cycles do not create duplicate ticker jobs`
  - `onReleaseStop with brief tap below 400ms cleans up file and returns to Idle`
  - `recording failure on start cleans up temp file and enters error state`
  - `recording failure on stop cleans up temp file and enters error state`
  - `onCancel cleans up audio file, cancels recorder and resets to Idle`
  - `dismiss placeholder deletes temporary file and returns to Idle`
  - `dismissError resets Error state back to Idle`
  - `permission denial transitions to Error state without starting recorder`
- **`AppPrivateVoiceFileManagerTest`**: 4/4 passed (8.9s with Robolectric)
  - `createTempRecordingFile creates path inside app-private cache voice_recordings directory`
  - `deleteRecordingFile deletes existing file and returns true`
  - `deleteRecordingFile returns true if file does not exist`
  - `cleanAllTempRecordings deletes all files in voice directory and returns count`

### 6.2 Full Repository Unit Test Suite
- **Command:** `cd IronMind; .\gradlew testDebugUnitTest`
- **Duration:** 6m 3s
- **Total Tests:** 2,040+ tests executed
- **Pass Rate:** **100%** (0 failures, 0 errors, 0 skipped)

### 6.3 APK Build Gate
- **Command:** `cd IronMind; .\gradlew assembleDebug`
- **Duration:** 1m 3s
- **Result:** **BUILD SUCCESSFUL** (APK generated cleanly with 0 compilation errors)

---

## 7. Git & Preservation Compliance

- **Pre-existing tracked files preserved:** All 12 pre-existing tracked modified files (e.g., `gradle.properties`, `google-services.json`, `.idea/*`, historical reports) and untracked artifacts remain completely untouched.
- **Destructive operations:** Zero uses of `git reset`, `clean`, `restore`, `stash`, or `force push`.
- **Targeted Staging:** Only the specific VP-1 voice production and test files, along with this report, are staged and committed.
