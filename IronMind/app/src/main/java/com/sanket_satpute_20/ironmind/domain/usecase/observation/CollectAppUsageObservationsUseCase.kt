package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects app usage observations from the OS and persists them.
 *
 * Guarantees:
 * 1. User Consent Guard: [AppUsageObservationSettings.isEnabled] must be true.
 * 2. OS Permission Guard: Android PACKAGE_USAGE_STATS permission must be granted.
 * 3. Persistent Checkpoint: Resumes collection from [AppUsageCheckpoint.lastCollectedIntervalEndMs].
 * 4. Crash-Safe Atomicity: Observations and checkpoint cursor advance together in one transaction.
 * 5. Delayed Collection Recovery: Handles multi-hour delays via bounded sequential chunks.
 * 6. Boundary Reconciliation: Reconnects active foreground sessions across intervals.
 * 7. Midnight Slicing: Slices cross-midnight sessions into daily bounded observation entities.
 * 8. Epistemic Integrity: Permission denial never produces false 0ms observations or advances the cursor.
 */
class CollectAppUsageObservationsUseCase(
    private val settingsRepository: AppUsageObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val appUsageObservationProvider: AppUsageObservationProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val checkpointStore: AppUsageCheckpointStore? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val defaultInitialLookbackMillis: Long = 60 * 60 * 1000L, // 1 hour initial lookback
    private val maxRetentionMillis: Long = 7L * 24 * 60 * 60 * 1000L, // Bounded query lookback ceiling (Android UsageEvents retention is OS-managed and variable across OEMs/devices)
    private val maxChunkDurationMillis: Long = 6L * 60 * 60 * 1000L // 6 hours per chunk
) : ObservationCollector {

    companion object {
        private val userMutexes = ConcurrentHashMap<String, Mutex>()
    }

    override val collectorName: String = "AppUsage"

    override suspend operator fun invoke(userId: String): Result<Int, Exception> = withContext(Dispatchers.IO) {
        val mutex = userMutexes.computeIfAbsent(userId) { Mutex() }
        mutex.withLock {
            try {
                // Guard 1: User consent
                val settingsResult = settingsRepository.getSettings(userId)
                if (settingsResult is Result.Failure) return@withLock Result.Failure(settingsResult.error)
                val settings = (settingsResult as Result.Success).data

                if (!settings.isEnabled) {
                    println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=disabled userId=$userId")
                    return@withLock Result.Success(0)
                }

                // Guard 2: OS permission (Special App Access: Usage Access)
                if (!appUsageObservationProvider.isPermissionGranted()) {
                    println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=permission_not_granted userId=$userId")
                    return@withLock Result.Success(0)
                }

                val now = clock.currentTimeMillis()

                // Step 2 & 5: Load persistent checkpoint cursor
                val existingCheckpoint = if (checkpointStore != null) {
                    when (val cpResult = checkpointStore.getCheckpoint(userId)) {
                        is Result.Success -> cpResult.data
                        is Result.Failure -> return@withLock Result.Failure(cpResult.error)
                    }
                } else {
                    null
                }

                // Determine fromTime and initial active foreground state
                val fromTime: Long
                var currentActiveSession: ActiveForegroundSession?

                if (existingCheckpoint != null) {
                    val lastCollected = existingCheckpoint.lastCollectedIntervalEndMs
                    if (lastCollected >= now) {
                        println("IronMindLifecycle [AppUsageObservation] [UP_TO_DATE] userId=$userId lastCollected=$lastCollected now=$now")
                        return@withLock Result.Success(0)
                    }

                    // Protect against checkpoints older than Android's available UsageEvents retention
                    if (lastCollected < now - maxRetentionMillis) {
                        val gapMs = (now - maxRetentionMillis) - lastCollected
                        println("IronMindLifecycle [AppUsageObservation] [DATA_GAP_BEYOND_RETENTION] userId=$userId gapMs=$gapMs")
                        fromTime = now - maxRetentionMillis
                        currentActiveSession = null // active session state from > 7 days ago is stale
                    } else {
                        fromTime = lastCollected
                        currentActiveSession = existingCheckpoint.activePackageName?.let { pkg ->
                            existingCheckpoint.activeSessionStartTimeMs?.let { start ->
                                ActiveForegroundSession(pkg, start)
                            }
                        }
                    }
                } else {
                    // Initial run for this user
                    fromTime = now - defaultInitialLookbackMillis
                    currentActiveSession = null
                }

                // Process collection in bounded sequential chunks to avoid memory spikes and huge transactions
                var chunkStart = fromTime
                var totalSavedCount = 0

                while (chunkStart < now) {
                    val chunkEnd = minOf(chunkStart + maxChunkDurationMillis, now)

                    val reconstruction = appUsageObservationProvider.getAppUsageBetween(
                        fromTimeMillis = chunkStart,
                        toTimeMillis = chunkEnd,
                        initialActiveSession = currentActiveSession
                    )

                    currentActiveSession = reconstruction.activeSession

                    val chunkObservations = mutableListOf<Observation>()

                    for (event in reconstruction.events) {
                        // Step 3: Split sessions crossing midnight into daily bounded slices
                        val slices = splitEventByDay(event, zoneId)

                        for (slice in slices) {
                            // Deterministic ID generation based on user, package, and slice start time
                            val deterministicId = UUID.nameUUIDFromBytes(
                                "appusage_${userId}_${slice.packageName}_${slice.startTimeMillis}".toByteArray()
                            ).toString()

                            chunkObservations.add(
                                Observation(
                                    id = deterministicId,
                                    userId = userId,
                                    type = ObservationType.APP_USAGE_SESSION,
                                    source = ObservationSource.APP_USAGE,
                                    occurredAt = slice.startTimeMillis,
                                    recordedAt = now,
                                    subjectId = null,
                                    value = slice.packageName,
                                    context = "duration_ms=${slice.totalDurationMillis}",
                                    confidence = null,
                                    provenance = ObservationProvenance(
                                        source = ObservationSource.APP_USAGE,
                                        sourceReference = "UsageStatsManager",
                                        capturedAt = now
                                    )
                                )
                            )
                        }
                    }

                    val newCheckpoint = AppUsageCheckpoint(
                        userId = userId,
                        lastCollectedIntervalEndMs = chunkEnd,
                        activePackageName = currentActiveSession?.packageName,
                        activeSessionStartTimeMs = currentActiveSession?.startTimeMillis,
                        updatedAt = now,
                        schemaVersion = 1
                    )

                    // Step 4: Atomic persistence
                    if (checkpointStore != null) {
                        val saveResult = checkpointStore.saveObservationsWithCheckpoint(
                            observations = chunkObservations,
                            checkpoint = newCheckpoint
                        )
                        if (saveResult is Result.Failure) {
                            println("IronMindLifecycle [AppUsageObservation] [CHUNK_PERSIST_FAILED] userId=$userId chunk=[$chunkStart, $chunkEnd] error=${saveResult.error.message}")
                            return@withLock Result.Failure(saveResult.error)
                        }
                    } else {
                        for (obs in chunkObservations) {
                            val insertResult = observationRepository.insertObservation(obs)
                            if (insertResult is Result.Failure) {
                                return@withLock Result.Failure(insertResult.error)
                            }
                        }
                    }

                    totalSavedCount += chunkObservations.size
                    chunkStart = chunkEnd
                }

                println("IronMindLifecycle [AppUsageObservation] [COLLECTED] userId=$userId count=$totalSavedCount active=${currentActiveSession?.packageName}")
                Result.Success(totalSavedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [AppUsageObservation] [COLLECT_FAILED] userId=$userId error=${e.message}")
                Result.Failure(e)
            }
        }
    }

    /**
     * Slices an [AppUsageEvent] that may cross calendar midnight into separate daily bounded events.
     * Each daily slice retains its accurate startTime, endTime, and duration within that specific day.
     */
    private fun splitEventByDay(event: AppUsageEvent, zone: ZoneId): List<AppUsageEvent> {
        if (event.totalDurationMillis <= 0L || event.startTimeMillis >= event.endTimeMillis) {
            return emptyList()
        }

        val slices = mutableListOf<AppUsageEvent>()
        var currentStart = event.startTimeMillis
        val finalEnd = event.endTimeMillis

        while (currentStart < finalEnd) {
            val currentZdt = Instant.ofEpochMilli(currentStart).atZone(zone)
            val nextMidnightZdt = currentZdt.toLocalDate().plusDays(1).atStartOfDay(zone)
            val nextMidnightMs = nextMidnightZdt.toInstant().toEpochMilli()

            val currentEnd = minOf(finalEnd, nextMidnightMs)
            val sliceDuration = currentEnd - currentStart
            if (sliceDuration > 0L) {
                slices.add(
                    AppUsageEvent(
                        packageName = event.packageName,
                        startTimeMillis = currentStart,
                        endTimeMillis = currentEnd,
                        totalDurationMillis = sliceDuration
                    )
                )
            }
            currentStart = currentEnd
        }

        return slices
    }
}
