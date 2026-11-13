package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class GetDailyAppUsageIntelligenceUseCaseTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var fakeProvider: FakeAppUsageProvider
    private lateinit var fakeSettingsRepo: FakeAppUsageSettingsRepo
    private lateinit var fakeObservationRepo: FakeObservationRepo
    private lateinit var useCase: GetDailyAppUsageIntelligenceUseCase

    private val testUserId = "user_digital_test"
    private val baseTime = 1_700_000_000_000L // arbitrary fixed epoch millis
    private val dayDuration = 86_400_000L

    @Before
    fun setUp() {
        fakeClock = FakeClock(currentTime = baseTime)
        fakeProvider = FakeAppUsageProvider()
        fakeSettingsRepo = FakeAppUsageSettingsRepo()
        fakeObservationRepo = FakeObservationRepo()

        useCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = fakeProvider,
            appUsageSettingsRepository = fakeSettingsRepo,
            observationRepository = fakeObservationRepo,
            clock = fakeClock
        )
    }

    // =========================================================================
    // 1. Permission and Consent Gates (Epistemic Honesty: Never false zero)
    // =========================================================================

    @Test
    fun whenPermissionDenied_returnsPermissionDeniedAndNullDurationNeverZero() = runBlocking {
        fakeProvider.permissionGranted = false

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = baseTime,
            customWindowEndMs = baseTime + dayDuration
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(UsagePermissionStatus.DENIED, data.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, data.availability)
        assertNull("Missing permission MUST yield null duration, never 0ms", data.totalObservedDurationMs)
        assertNull("Missing permission MUST yield null session count", data.totalSessionsCount)
        assertTrue(data.appUsages.isEmpty())
        assertTrue(data.mostUsedApps.isEmpty())
        assertNull(data.comparisonWithPreviousDay)
        assertFalse(data.isFresh)
        assertNotNull(data.notes)
        assertTrue(data.notes!!.contains("PACKAGE_USAGE_STATS"))
    }

    @Test
    fun whenSettingDisabled_returnsPermissionNotRequestedAndNullDurationNeverZero() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = false // User disabled observation toggle

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = baseTime,
            customWindowEndMs = baseTime + dayDuration
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(UsagePermissionStatus.DENIED, data.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_NOT_REQUESTED, data.availability)
        assertNull("Disabled setting MUST yield null duration, never 0ms", data.totalObservedDurationMs)
        assertNull(data.totalSessionsCount)
        assertTrue(data.appUsages.isEmpty())
        assertNull(data.comparisonWithPreviousDay)
    }

    @Test
    fun whenProviderMissing_returnsUnavailableHardwareAndNullDuration() = runBlocking {
        val noProviderUseCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = null,
            appUsageSettingsRepository = fakeSettingsRepo,
            observationRepository = fakeObservationRepo,
            clock = fakeClock
        )

        val result = noProviderUseCase(
            userId = testUserId,
            customWindowStartMs = baseTime,
            customWindowEndMs = baseTime + dayDuration
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, data.availability)
        assertNull(data.totalObservedDurationMs)
    }

    // =========================================================================
    // 2. Empty Data vs Observed Data Differentiation
    // =========================================================================

    @Test
    fun whenPermissionGranted_andZeroEvents_returnsEmptyNoDataWithZeroDuration() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true
        fakeProvider.events = emptyList()

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = baseTime,
            customWindowEndMs = baseTime + dayDuration
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(UsagePermissionStatus.GRANTED, data.permissionStatus)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, data.availability)
        assertEquals(0L, data.totalObservedDurationMs) // Legitimately observed 0ms
        assertEquals(0, data.totalSessionsCount)
        assertTrue(data.appUsages.isEmpty())
        assertTrue(data.mostUsedApps.isEmpty())
        assertNull(data.comparisonWithPreviousDay)
        assertTrue(data.isFresh)
    }

    // =========================================================================
    // 3. Factual Aggregation, Deterministic Sorting & Epistemic Authority
    // =========================================================================

    @Test
    fun whenPermissionGranted_withEvents_computesTotalsAndDeterministicRanks() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Chrome: 2 sessions totaling 30m (1800_000ms)
        val c1 = AppUsageEvent("com.android.chrome", dayStart + 1000, dayStart + 601000, 600000)
        val c2 = AppUsageEvent("com.android.chrome", dayStart + 2000000, dayStart + 3200000, 1200000)
        // Slack: 1 session totaling 30m (1800_000ms) - Equal duration to Chrome
        val s1 = AppUsageEvent("com.slack", dayStart + 5000000, dayStart + 6800000, 1800000)
        // YouTube: 1 session totaling 60m (3600_000ms)
        val y1 = AppUsageEvent("com.google.android.youtube", dayStart + 10000000, dayStart + 13600000, 3600000)
        // Notes: 1 session totaling 10m (600_000ms)
        val n1 = AppUsageEvent("com.notes.app", dayStart + 15000000, dayStart + 15600000, 600000)

        fakeProvider.events = listOf(c1, c2, s1, y1, n1)

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = dayStart,
            customWindowEndMs = dayEnd,
            topAppsLimit = 3
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(UsagePermissionStatus.GRANTED, data.permissionStatus)
        assertEquals(SourceAvailability.AVAILABLE, data.availability)
        assertEquals(7800000L, data.totalObservedDurationMs) // 3600k + 1800k + 1800k + 600k = 7800k (2h 10m)
        assertEquals(5, data.totalSessionsCount)
        assertEquals(4, data.appUsages.size)

        // Verify Rank: 1. YouTube (3600k), 2. Chrome (1800k), 3. Slack (1800k - tie-broken alphabetically), 4. Notes (600k)
        assertEquals("com.google.android.youtube", data.appUsages[0].packageName)
        assertEquals(3600000L, data.appUsages[0].totalDurationMs)
        assertEquals(1, data.appUsages[0].sessionCount)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, data.appUsages[0].epistemicAuthority)

        assertEquals("com.android.chrome", data.appUsages[1].packageName)
        assertEquals(1800000L, data.appUsages[1].totalDurationMs)
        assertEquals(2, data.appUsages[1].sessionCount)

        assertEquals("com.slack", data.appUsages[2].packageName)
        assertEquals(1800000L, data.appUsages[2].totalDurationMs)
        assertEquals(1, data.appUsages[2].sessionCount)

        assertEquals("com.notes.app", data.appUsages[3].packageName)

        // Top Apps limited to 3
        assertEquals(3, data.mostUsedApps.size)
        assertEquals("com.google.android.youtube", data.mostUsedApps[0].packageName)
        assertEquals("com.android.chrome", data.mostUsedApps[1].packageName)
        assertEquals("com.slack", data.mostUsedApps[2].packageName)

        // Percentages check
        val ytPercentage = (3600000f / 7800000f) * 100f
        assertEquals(ytPercentage, data.appUsages[0].percentageOfTotal, 0.1f)
    }

    // =========================================================================
    // 4. Boundary Clamping (Window Accuracy)
    // =========================================================================

    @Test
    fun boundaryClamping_clampsEventsSpanningAcrossWindowBoundaries() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Event started 30 mins before window, ended 30 mins after window start (60m total duration)
        // Inside window portion is only 30m (1800_000ms)
        val spanningStart = AppUsageEvent(
            packageName = "com.podcast.app",
            startTimeMillis = dayStart - 1800_000L,
            endTimeMillis = dayStart + 1800_000L,
            totalDurationMillis = 3600_000L
        )

        // Event started 30 mins before window end, ended 30 mins after window end (60m total duration)
        // Inside window portion is only 30m (1800_000ms)
        val spanningEnd = AppUsageEvent(
            packageName = "com.music.app",
            startTimeMillis = dayEnd - 1800_000L,
            endTimeMillis = dayEnd + 1800_000L,
            totalDurationMillis = 3600_000L
        )

        fakeProvider.events = listOf(spanningStart, spanningEnd)

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = dayStart,
            customWindowEndMs = dayEnd
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        // Both clamped to 1800_000L: Total = 3600_000L
        assertEquals(3600_000L, data.totalObservedDurationMs)
        val podcast = data.appUsages.first { it.packageName == "com.podcast.app" }
        val music = data.appUsages.first { it.packageName == "com.music.app" }
        assertEquals(1800_000L, podcast.totalDurationMs)
        assertEquals(1800_000L, music.totalDurationMs)
    }

    // =========================================================================
    // 5. Day-Over-Day Comparison (Only When Baseline Data Exists)
    // =========================================================================

    @Test
    fun comparisonWithPreviousDay_computedOnlyWhenBaselineExists() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration
        val prevStart = dayStart - dayDuration
        val prevEnd = dayStart

        // Previous day: 1 hour (3600_000ms)
        val prevEvent = AppUsageEvent("com.study.app", prevStart + 1000, prevStart + 3601000, 3600_000L)
        // Current day: 2 hours (7200_000ms)
        val currEvent = AppUsageEvent("com.study.app", dayStart + 1000, dayStart + 7201000, 7200_000L)

        fakeProvider.events = listOf(prevEvent, currEvent)

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = dayStart,
            customWindowEndMs = dayEnd
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertNotNull("Comparison must be present when previous day baseline exists", data.comparisonWithPreviousDay)
        val comp = data.comparisonWithPreviousDay!!

        assertEquals(3600_000L, comp.previousDayDurationMs)
        assertEquals(7200_000L, comp.currentDayDurationMs)
        assertEquals(3600_000L, comp.durationDeltaMs)
        assertEquals(100.0f, comp.percentageDelta!!, 0.1f)
        assertTrue(comp.comparisonNote.contains("higher than the previous day"))
    }

    @Test
    fun comparisonWithPreviousDay_isNullWhenPreviousDayHasNoData() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Only current day has events; previous day is empty
        val currEvent = AppUsageEvent("com.study.app", dayStart + 1000, dayStart + 3601000, 3600_000L)
        fakeProvider.events = listOf(currEvent)

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = dayStart,
            customWindowEndMs = dayEnd
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertNull("Comparison must be null when previous day has no baseline data", data.comparisonWithPreviousDay)
    }

    // =========================================================================
    // 6. Historical Observation Fallback
    // =========================================================================

    @Test
    fun historicalFallback_queriesObservationRepositoryWhenProviderReturnsEmpty() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true
        fakeProvider.events = emptyList() // Provider has no in-memory events for historical day

        val pastStart = baseTime - (10 * dayDuration)
        val pastEnd = pastStart + dayDuration

        fakeObservationRepo.observations = listOf(
            Observation(
                id = "obs_hist_1",
                userId = testUserId,
                type = ObservationType.APP_USAGE_SESSION,
                source = ObservationSource.APP_USAGE,
                occurredAt = pastStart + 5000,
                recordedAt = pastStart + 5000,
                subjectId = null,
                value = "com.legacy.app",
                context = "duration_ms=2400000",
                confidence = null,
                provenance = ObservationProvenance(ObservationSource.APP_USAGE, "test", pastStart)
            )
        )

        val result = useCase(
            userId = testUserId,
            customWindowStartMs = pastStart,
            customWindowEndMs = pastEnd
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(SourceAvailability.AVAILABLE, data.availability)
        assertEquals(2400000L, data.totalObservedDurationMs)
        assertEquals(1, data.appUsages.size)
        assertEquals("com.legacy.app", data.appUsages[0].packageName)
    }

    // =========================================================================
    // 7. Input Validation Guards
    // =========================================================================

    @Test
    fun validation_rejectsBlankUserIdOrInvertedWindow() = runBlocking {
        fakeProvider.permissionGranted = true

        val blankUserRes = useCase(userId = "")
        assertTrue(blankUserRes is Result.Failure)

        val invertedWindowRes = useCase(
            userId = testUserId,
            customWindowStartMs = 2000L,
            customWindowEndMs = 1000L
        )
        assertTrue(invertedWindowRes is Result.Failure)
    }

    // =========================================================================
    // 8. Phase 3.6.7.8B.3 — Interval Mathematics, Union Semantics & Day Boundaries
    // =========================================================================

    @Test
    fun samePackageOverlappingIntervals_areUnionedNotSummed() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Chrome interval 1: [10:00..10:30] (1800_000ms)
        // Chrome interval 2: [10:15..10:45] (1800_000ms)
        // Union should be [10:00..10:45] (2700_000ms = 45m), NOT 3600_000ms!
        val c1 = AppUsageEvent("com.android.chrome", dayStart + 36_000_000L, dayStart + 37_800_000L, 1800_000L)
        val c2 = AppUsageEvent("com.android.chrome", dayStart + 36_900_000L, dayStart + 38_700_000L, 1800_000L)

        fakeProvider.events = listOf(c1, c2)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(2700_000L, data.totalObservedDurationMs)
        assertEquals(1, data.appUsages.size)
        assertEquals(2700_000L, data.appUsages[0].totalDurationMs)
        assertEquals(2, data.appUsages[0].sessionCount)
        assertEquals(2, data.totalSessionsCount)
    }

    @Test
    fun duplicateIntervals_doNotInflatePackageDuration() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Exactly identical events emitted twice
        val c1 = AppUsageEvent("com.android.chrome", dayStart + 10_000L, dayStart + 600_000L, 590_000L)
        val c2 = AppUsageEvent("com.android.chrome", dayStart + 10_000L, dayStart + 600_000L, 590_000L)

        fakeProvider.events = listOf(c1, c2)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(590_000L, data.totalObservedDurationMs)
        assertEquals(1, data.appUsages.size)
        assertEquals(590_000L, data.appUsages[0].totalDurationMs)
        assertEquals(1, data.appUsages[0].sessionCount) // Deduplicated
        assertEquals(1, data.totalSessionsCount)
    }

    @Test
    fun differentPackagesOverlappingIntervals_areNotMergedAcrossPackages() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // App A: [10:00..10:30] (1800_000ms)
        // App B: [10:15..10:45] (1800_000ms)
        // Overlapping in time, but different packages MUST maintain separate package-level durations!
        val a1 = AppUsageEvent("com.app.a", dayStart + 36_000_000L, dayStart + 37_800_000L, 1800_000L)
        val b1 = AppUsageEvent("com.app.b", dayStart + 36_900_000L, dayStart + 38_700_000L, 1800_000L)

        fakeProvider.events = listOf(a1, b1)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(2, data.appUsages.size)
        val appA = data.appUsages.first { it.packageName == "com.app.a" }
        val appB = data.appUsages.first { it.packageName == "com.app.b" }

        assertEquals(1800_000L, appA.totalDurationMs)
        assertEquals(1800_000L, appB.totalDurationMs)
        // Total observed equals sum of package durations
        assertEquals(3600_000L, data.totalObservedDurationMs)
        assertEquals(50.0f, appA.percentageOfTotal, 0.1f)
        assertEquals(50.0f, appB.percentageOfTotal, 0.1f)
    }

    @Test
    fun adjacentIntervals_followDocumentedMergeContract() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Interval 1: [10:00..10:30]
        // Interval 2: [10:30..11:00] (adjacent at 10:30)
        val c1 = AppUsageEvent("com.android.chrome", dayStart + 36_000_000L, dayStart + 37_800_000L, 1800_000L)
        val c2 = AppUsageEvent("com.android.chrome", dayStart + 37_800_000L, dayStart + 39_600_000L, 1800_000L)

        fakeProvider.events = listOf(c1, c2)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        // 3600_000ms union duration, 2 sessions
        assertEquals(3600_000L, data.totalObservedDurationMs)
        assertEquals(3600_000L, data.appUsages[0].totalDurationMs)
        assertEquals(2, data.appUsages[0].sessionCount)
        assertEquals(2, data.totalSessionsCount)
    }

    @Test
    fun intervalsOutsideWindow_doNotContribute() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        val before = AppUsageEvent("com.app.early", dayStart - 100_000L, dayStart - 50_000L, 50_000L)
        val after = AppUsageEvent("com.app.late", dayEnd + 10_000L, dayEnd + 50_000L, 40_000L)
        val valid = AppUsageEvent("com.app.valid", dayStart + 1000L, dayStart + 60_000L, 59_000L)

        fakeProvider.events = listOf(before, after, valid)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.appUsages.size)
        assertEquals("com.app.valid", data.appUsages[0].packageName)
        assertEquals(59_000L, data.totalObservedDurationMs)
    }

    @Test
    fun midnightCrossingIntervals_clippedCorrectlyToWindow() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        // Crosses start of day: 23:30 previous day to 00:30 today (60m total, 30m inside window)
        val midnightCross = AppUsageEvent("com.app.night", dayStart - 1800_000L, dayStart + 1800_000L, 3600_000L)

        fakeProvider.events = listOf(midnightCross)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1800_000L, data.totalObservedDurationMs)
        assertEquals(dayStart, data.appUsages[0].firstObservedAt)
        assertEquals(dayStart + 1800_000L, data.appUsages[0].lastObservedAt)
    }

    @Test
    fun invalidAndZeroLengthIntervals_handledSafelyWithoutCrash() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        val dayStart = baseTime
        val dayEnd = baseTime + dayDuration

        val zeroDuration = AppUsageEvent("com.app.zero", dayStart + 10_000L, dayStart + 10_000L, 0L)
        val inverted = AppUsageEvent("com.app.inverted", dayStart + 20_000L, dayStart + 10_000L, -10_000L)
        val blankPkg = AppUsageEvent("   ", dayStart + 30_000L, dayStart + 40_000L, 10_000L)
        val valid = AppUsageEvent("com.app.valid", dayStart + 50_000L, dayStart + 100_000L, 50_000L)

        fakeProvider.events = listOf(zeroDuration, inverted, blankPkg, valid)

        val result = useCase(userId = testUserId, customWindowStartMs = dayStart, customWindowEndMs = dayEnd)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.appUsages.size)
        assertEquals("com.app.valid", data.appUsages[0].packageName)
        assertEquals(50_000L, data.totalObservedDurationMs)
    }

    @Test
    fun daylightSavingTransitionDay_respectsTrueDayBoundariesWithoutAssuming24Hours() = runBlocking {
        fakeProvider.permissionGranted = true
        fakeSettingsRepo.isEnabled = true

        // Europe/London spring forward (23-hour day) on 2026-03-29
        val londonZone = java.time.ZoneId.of("Europe/London")
        val springForwardDate = java.time.LocalDate.of(2026, 3, 29)
        val dayStartLondon = springForwardDate.atStartOfDay(londonZone).toInstant().toEpochMilli()
        val dayEndLondon = springForwardDate.plusDays(1).atStartOfDay(londonZone).toInstant().toEpochMilli()

        // Verify day duration is exactly 23 hours (82_800_000ms), not 24 hours
        assertEquals(23 * 3600_000L, dayEndLondon - dayStartLondon)

        val ev = AppUsageEvent("com.app.uk", dayStartLondon + 3600_000L, dayStartLondon + 7200_000L, 3600_000L)
        fakeProvider.events = listOf(ev)

        val result = useCase(
            userId = testUserId,
            targetDayMillis = dayStartLondon + 10_000L,
            zoneId = londonZone
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(dayStartLondon, data.targetDayStartMs)
        assertEquals(dayEndLondon, data.targetDayEndMs)
        assertEquals(3600_000L, data.totalObservedDurationMs)
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    private class FakeAppUsageProvider : AppUsageObservationProvider {
        var permissionGranted: Boolean = true
        var events: List<AppUsageEvent> = emptyList()

        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> {
            return events.filter { it.startTimeMillis >= fromTimeMillis || it.endTimeMillis >= fromTimeMillis }
        }
    }

    private class FakeAppUsageSettingsRepo : AppUsageObservationSettingsRepository {
        var isEnabled: Boolean = true

        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> {
            return Result.Success(
                AppUsageObservationSettings(userId = userId, isEnabled = isEnabled)
            )
        }
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    private class FakeObservationRepo : ObservationRepository {
        var observations: List<Observation> = emptyList()

        override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getObservations(userId: String, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations)
        override suspend fun getObservationsByType(userId: String, type: ObservationType, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations.filter { it.type == type })
        override suspend fun getObservationById(id: String): Result<Observation, Exception> = Result.Failure(NoSuchElementException())
        override suspend fun getObservation(userId: String, id: String): Result<Observation, Exception> = Result.Failure(NoSuchElementException())
        override suspend fun deleteObservation(id: String): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getObservationsForTimeWindow(userId: String, type: ObservationType, startTimeMs: Long, endTimeMs: Long): Result<List<Observation>, Exception> {
            return Result.Success(
                observations.filter { it.userId == userId && it.type == type && it.occurredAt in startTimeMs..endTimeMs }
            )
        }
        override suspend fun getObservationsForTimeWindow(userId: String, startTimeMs: Long, endTimeMs: Long, types: List<ObservationType>?, limit: Int, orderAsc: Boolean): Result<List<Observation>, Exception> {
            return Result.Success(
                observations.filter { it.userId == userId && (types == null || it.type in types) && it.occurredAt in startTimeMs..endTimeMs }
            )
        }
    }
}
