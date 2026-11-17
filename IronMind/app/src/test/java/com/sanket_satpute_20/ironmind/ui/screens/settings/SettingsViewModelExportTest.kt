package com.sanket_satpute_20.ironmind.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings
import com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.NotificationObservationProvider
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetAppUsageObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetNotificationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetAppUsageObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetNotificationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelExportTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var context: Context
    private lateinit var getAutonomySettingsUseCase: GetAutonomySettingsUseCase
    private lateinit var toggleGlobalPauseUseCase: ToggleGlobalPauseUseCase
    private lateinit var getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase
    private lateinit var setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase
    private lateinit var appUsageObservationProvider: AppUsageObservationProvider
    private lateinit var getNotificationObservationSettingsUseCase: GetNotificationObservationSettingsUseCase
    private lateinit var setNotificationObservationEnabledUseCase: SetNotificationObservationEnabledUseCase
    private lateinit var notificationObservationProvider: NotificationObservationProvider
    private lateinit var getCalendarObservationSettingsUseCase: GetCalendarObservationSettingsUseCase
    private lateinit var setCalendarObservationEnabledUseCase: SetCalendarObservationEnabledUseCase
    private lateinit var calendarObservationProvider: CalendarObservationProvider
    private lateinit var getLocationObservationSettingsUseCase: GetLocationObservationSettingsUseCase
    private lateinit var setLocationObservationEnabledUseCase: SetLocationObservationEnabledUseCase
    private lateinit var locationObservationProvider: LocationObservationProvider
    private lateinit var getActivityObservationSettingsUseCase: GetActivityObservationSettingsUseCase
    private lateinit var setActivityObservationEnabledUseCase: SetActivityObservationEnabledUseCase
    private lateinit var activityObservationProvider: ActivityObservationProvider

    private lateinit var mockExportUserDataToFileUseCase: ExportUserDataToFileUseCase
    private lateinit var mockDeleteUserDataUseCase: DeleteUserDataUseCase
    private lateinit var mockShareManager: UserDataExportShareManager
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var viewModel: SettingsViewModel
    private lateinit var testExportFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        testExportFile = File(exportDir, "ironmind_export_user-1_1000.json").apply {
            writeText("{\"data\":\"export_content\"}")
        }

        getAutonomySettingsUseCase = mockk(relaxed = true)
        toggleGlobalPauseUseCase = mockk(relaxed = true)
        getAppUsageObservationSettingsUseCase = mockk(relaxed = true)
        setAppUsageObservationEnabledUseCase = mockk(relaxed = true)
        appUsageObservationProvider = mockk(relaxed = true)
        getNotificationObservationSettingsUseCase = mockk(relaxed = true)
        setNotificationObservationEnabledUseCase = mockk(relaxed = true)
        notificationObservationProvider = mockk(relaxed = true)
        getCalendarObservationSettingsUseCase = mockk(relaxed = true)
        setCalendarObservationEnabledUseCase = mockk(relaxed = true)
        calendarObservationProvider = mockk(relaxed = true)
        getLocationObservationSettingsUseCase = mockk(relaxed = true)
        setLocationObservationEnabledUseCase = mockk(relaxed = true)
        locationObservationProvider = mockk(relaxed = true)
        getActivityObservationSettingsUseCase = mockk(relaxed = true)
        setActivityObservationEnabledUseCase = mockk(relaxed = true)
        activityObservationProvider = mockk(relaxed = true)

        coEvery { getAutonomySettingsUseCase(any()) } returns Result.Success(
            AutonomySettings(userId = "user-1", levels = emptyMap(), isGlobalPauseActive = false)
        )
        coEvery { getAppUsageObservationSettingsUseCase(any()) } returns Result.Success(
            AppUsageObservationSettings(userId = "user-1", isEnabled = true)
        )
        coEvery { getNotificationObservationSettingsUseCase(any()) } returns Result.Success(
            NotificationObservationSettings(userId = "user-1", isEnabled = true)
        )
        coEvery { getCalendarObservationSettingsUseCase(any()) } returns Result.Success(
            CalendarObservationSettings(userId = "user-1", isEnabled = true, updatedAt = 1000L)
        )
        coEvery { getLocationObservationSettingsUseCase(any()) } returns Result.Success(
            LocationObservationSettings(userId = "user-1", isEnabled = true, updatedAt = 1000L)
        )
        coEvery { getActivityObservationSettingsUseCase(any()) } returns Result.Success(
            ActivityObservationSettings(userId = "user-1", isEnabled = true, lastUpdatedAt = 1000L)
        )

        mockExportUserDataToFileUseCase = mockk(relaxed = true)
        mockDeleteUserDataUseCase = mockk(relaxed = true)
        mockShareManager = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))

        viewModel = SettingsViewModel(
            getAutonomySettingsUseCase = getAutonomySettingsUseCase,
            toggleGlobalPauseUseCase = toggleGlobalPauseUseCase,
            getAppUsageObservationSettingsUseCase = getAppUsageObservationSettingsUseCase,
            setAppUsageObservationEnabledUseCase = setAppUsageObservationEnabledUseCase,
            appUsageObservationProvider = appUsageObservationProvider,
            getNotificationObservationSettingsUseCase = getNotificationObservationSettingsUseCase,
            setNotificationObservationEnabledUseCase = setNotificationObservationEnabledUseCase,
            notificationObservationProvider = notificationObservationProvider,
            getCalendarObservationSettingsUseCase = getCalendarObservationSettingsUseCase,
            setCalendarObservationEnabledUseCase = setCalendarObservationEnabledUseCase,
            calendarObservationProvider = calendarObservationProvider,
            getLocationObservationSettingsUseCase = getLocationObservationSettingsUseCase,
            setLocationObservationEnabledUseCase = setLocationObservationEnabledUseCase,
            locationObservationProvider = locationObservationProvider,
            getActivityObservationSettingsUseCase = getActivityObservationSettingsUseCase,
            setActivityObservationEnabledUseCase = setActivityObservationEnabledUseCase,
            activityObservationProvider = activityObservationProvider,
            userSessionProvider = sessionProvider,
            deleteUserDataUseCase = mockDeleteUserDataUseCase,
            exportUserDataToFileUseCase = mockExportUserDataToFileUseCase,
            shareManager = mockShareManager
        )
    }

    @Test
    fun exportUserData_success_updatesUiStateWithMetadata() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 42,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)

        viewModel.exportUserData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertEquals(metadata, state.exportSuccessMetadata)
        assertNull(state.exportErrorMessage)
    }

    @Test
    fun exportUserData_duplicateTap_preventsConcurrentExport() = runTest {
        val deferred = CompletableDeferred<Result<ExportFileMetadata, Exception>>()
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } coAnswers { deferred.await() }

        // First tap: begins exporting
        viewModel.exportUserData()
        assertTrue("isExporting must be true while in flight", viewModel.uiState.value.isExporting)

        // Second tap while still in flight
        viewModel.exportUserData()

        // Complete the export
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 10,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        deferred.complete(Result.Success(metadata))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockExportUserDataToFileUseCase("user-1", any()) }
    }

    @Test
    fun exportUserData_failure_surfacesErrorMessage() = runTest {
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Failure(
            IOException("Disk full error")
        )

        viewModel.exportUserData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNull(state.exportSuccessMetadata)
        assertEquals("Disk full error", state.exportErrorMessage)
    }

    @Test
    fun dismissExportMessages_clearsErrorState() = runTest {
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Failure(
            IOException("Export failed")
        )

        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.exportErrorMessage)

        viewModel.dismissExportMessages()
        assertNull(viewModel.uiState.value.exportErrorMessage)
    }

    @Test
    fun dismissExportMessages_clearsSuccessState() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 10,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)

        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull("Success metadata should be present after export", viewModel.uiState.value.exportSuccessMetadata)

        viewModel.dismissExportMessages()
        assertNull("Success metadata must be cleared after dismissal", viewModel.uiState.value.exportSuccessMetadata)
        assertNull("Error message must also be null after dismissal", viewModel.uiState.value.exportErrorMessage)
    }

    @Test
    fun dismissExportMessages_shareUnavailableAfterDismissal() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 10,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)
        val expectedChooser = Intent(Intent.ACTION_CHOOSER)
        coEvery { mockShareManager.createShareChooserIntent(context, metadata) } returns expectedChooser

        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull("Share intent should be available prior to dismissal", viewModel.getShareIntent(context))

        viewModel.dismissExportMessages()

        val shareIntent = viewModel.getShareIntent(context)
        assertNull("Share intent must be null after export success card is dismissed", shareIntent)
        assertTrue("Underlying export file must remain intact and not deleted", testExportFile.exists())
    }

    @Test
    fun dismissExportMessages_reExportAfterDismissal_succeeds() = runTest {
        val metadata1 = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 5,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        val file2 = File(testExportFile.parentFile, "ironmind_export_user-1_2000.json").apply { writeText("{}") }
        val metadata2 = ExportFileMetadata(
            file = file2,
            fileName = file2.name,
            sizeBytes = file2.length(),
            recordCount = 8,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 2000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata1) andThen Result.Success(metadata2)

        // First export
        viewModel.exportUserData()
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.exportSuccessMetadata?.recordCount)

        // Dismiss
        viewModel.dismissExportMessages()
        assertNull(viewModel.uiState.value.exportSuccessMetadata)

        // Re-export
        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull("Re-export should succeed after dismissal", viewModel.uiState.value.exportSuccessMetadata)
        assertEquals(8, viewModel.uiState.value.exportSuccessMetadata?.recordCount)
        assertEquals(file2.name, viewModel.uiState.value.exportSuccessMetadata?.fileName)
    }

    @Test
    fun sessionChange_clearsExportSuccessMetadata() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 15,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)

        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.exportSuccessMetadata)

        // Switch user session
        sessionProvider.setState(SessionState.Authenticated("user-2", false))
        advanceUntilIdle()

        assertNull("Export metadata must be cleared on user session switch",
            viewModel.uiState.value.exportSuccessMetadata)
    }

    @Test
    fun getShareIntent_activeUserMismatch_returnsNull() = runTest {
        // Metadata belongs to user-other, active user is user-1
        val foreignMetadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 5,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-other"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(foreignMetadata)

        viewModel.exportUserData()
        advanceUntilIdle()

        val shareIntent = viewModel.getShareIntent(context)
        assertNull("Sharing data belonging to another user must be blocked", shareIntent)
        coVerify(exactly = 0) { mockShareManager.createShareChooserIntent(any(), any()) }
    }

    @Test
    fun getShareIntent_deletedFile_returnsNullAndSurfacesError() = runTest {
        val deletedFile = File(context.cacheDir, "exports/deleted_file.json")
        val metadata = ExportFileMetadata(
            file = deletedFile,
            fileName = deletedFile.name,
            sizeBytes = 100L,
            recordCount = 5,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)

        viewModel.exportUserData()
        advanceUntilIdle()

        val shareIntent = viewModel.getShareIntent(context)
        assertNull("Sharing a missing file must return null", shareIntent)
        assertEquals("Export file is no longer available", viewModel.uiState.value.exportErrorMessage)
    }

    @Test
    fun getShareIntent_validFileAndUser_delegatesToShareManager() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 10,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)
        val expectedChooser = Intent(Intent.ACTION_CHOOSER)
        coEvery { mockShareManager.createShareChooserIntent(context, metadata) } returns expectedChooser

        viewModel.exportUserData()
        advanceUntilIdle()

        val resultIntent = viewModel.getShareIntent(context)
        assertEquals(expectedChooser, resultIntent)
    }

    @Test
    fun eraseLocalData_clearsExportMetadata() = runTest {
        val metadata = ExportFileMetadata(
            file = testExportFile,
            fileName = testExportFile.name,
            sizeBytes = testExportFile.length(),
            recordCount = 10,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 1000L,
            userId = "user-1"
        )
        coEvery { mockExportUserDataToFileUseCase("user-1", any()) } returns Result.Success(metadata)
        coEvery { mockDeleteUserDataUseCase("user-1") } returns Result.Success(Unit)

        viewModel.exportUserData()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.exportSuccessMetadata)

        viewModel.eraseLocalData()
        advanceUntilIdle()

        assertNull("Export metadata must be purged upon local data erasure",
            viewModel.uiState.value.exportSuccessMetadata)
        assertEquals("Local IronMind data has been safely erased.", viewModel.uiState.value.erasureSuccessMessage)
    }
}

