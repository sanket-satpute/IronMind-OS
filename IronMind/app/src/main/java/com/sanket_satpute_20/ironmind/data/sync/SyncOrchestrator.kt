package com.sanket_satpute_20.ironmind.data.sync

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.dao.LocalTombstoneDao
import com.sanket_satpute_20.ironmind.data.local.entity.SyncCheckpointEntity
import com.sanket_satpute_20.ironmind.data.local.entity.LocalTombstoneEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.data.repository.RemotePayloadApplier
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncState
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository
import com.sanket_satpute_20.ironmind.domain.repository.RemoteEntityPayload
import com.sanket_satpute_20.ironmind.domain.repository.SyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
import com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncOrchestrator as DomainSyncOrchestrator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates the sync cycle with strict concurrency serialization,
 * atomic in-flight claiming, stale-entry crash recovery, durable conflict persistence,
 * and downstream remote-to-local synchronization:
 * 1. Serializes execution per-user using a keyed Mutex to eliminate duplicate uploads.
 * 2. Recovers genuinely stale abandoned SYNCING entries left by previous process terminations.
 * 3. Claims pending entries using atomic CAS.
 * 4. Executes Downstream Pull: queries remote changes across all 6 entity types using compound
 *    (updatedAt, documentId) pagination, enforces lease fencing and session-generation checks,
 *    advances checkpoints, and prepares clean remote payloads.
 * 5. Executes Upstream Outbox Upload: claims pending entries with leases, uploads to remote,
 *    completes/conflicts/fails entries deterministically.
 */
class SyncOrchestrator(
    private val outboxRepository: OutboxRepository,
    private val syncRepository: SyncRepository,
    private val syncConflictDao: SyncConflictDao? = null,
    private val ironMindDao: IronMindDao? = null,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val checkpointStore: SyncCheckpointStore? = null,
    private val userSessionProvider: UserSessionProvider? = null,
    private val syncWorkerLeaseDao: SyncWorkerLeaseDao? = null,
    private val appSessionStateDao: AppSessionStateDao? = null,
    private val syncCheckpointDao: SyncCheckpointDao? = null,
    private val localTombstoneDao: LocalTombstoneDao? = null
) : DomainSyncOrchestrator {

    companion object {
        /**
         * Entries stuck in SYNCING longer than 5 minutes are considered abandoned
         * due to unexpected process termination or app kill, and will be recovered.
         */
        const val STALE_SYNCING_THRESHOLD_MS: Long = 5 * 60 * 1000L

        /**
         * In-flight synchronization lease duration.
         */
        const val LEASE_DURATION_MS: Long = 60 * 1000L

        /**
         * Maximum number of documents fetched per entity pull query page.
         */
        const val PULL_BATCH_LIMIT: Int = 50

        /**
         * Shared coordination lease key for sync execution.
         */
        const val LEASE_KEY_SYNC_EXECUTION: String = "sync_execution_lock"

        /**
         * Authoritative traversal types for sync_checkpoints table.
         */
        const val TRAVERSAL_DOWNSTREAM: String = "DOWNSTREAM"
        const val TRAVERSAL_TIER3_LEGACY: String = "TIER3_LEGACY"
    }

    private val effectiveLeaseDao: SyncWorkerLeaseDao?
        get() = syncWorkerLeaseDao ?: database?.syncWorkerLeaseDao()

    private val effectiveSessionStateDao: AppSessionStateDao?
        get() = appSessionStateDao ?: database?.appSessionStateDao()

    private val effectiveOutboxDao: OutboxDao?
        get() = outboxDao ?: database?.outboxDao()

    private val effectiveSyncConflictDao: SyncConflictDao?
        get() = syncConflictDao ?: database?.syncConflictDao()

    private val effectiveIronMindDao: IronMindDao?
        get() = ironMindDao ?: database?.ironMindDao()

    private val effectiveSyncCheckpointDao: SyncCheckpointDao?
        get() = syncCheckpointDao ?: database?.syncCheckpointDao()

    private val effectiveLocalTombstoneDao: LocalTombstoneDao?
        get() = localTombstoneDao ?: database?.localTombstoneDao()

    internal data class LocalEntitySnapshot(
        val id: String,
        val userId: String,
        val updatedAt: Long,
        val serializedJson: String,
        val isContentEquivalent: (RemoteEntityPayload) -> Boolean
    )

    internal fun readLocalEntity(dao: IronMindDao, entityType: String, entityId: String): LocalEntitySnapshot? {
        return when (entityType) {
            "Goal" -> dao.getGoal(entityId)?.let { g ->
                val json = org.json.JSONObject(mapOf(
                    "id" to g.id, "userId" to g.userId, "title" to g.title,
                    "importance" to g.importance, "status" to g.status,
                    "updatedAt" to g.updatedAt, "createdAt" to g.createdAt
                )).toString()
                LocalEntitySnapshot(g.id, g.userId, g.updatedAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        g.title == rJson.optString("title") &&
                        g.status == rJson.optString("status") &&
                        g.importance == rJson.optInt("importance", 1)
                    } catch (_: Exception) { false }
                }
            }
            "Plan" -> dao.getPlan(entityId)?.let { p ->
                val json = org.json.JSONObject(mapOf(
                    "id" to p.id, "userId" to p.userId, "goalId" to p.goalId,
                    "title" to p.title, "status" to p.status,
                    "updatedAt" to p.updatedAt, "createdAt" to p.createdAt
                )).toString()
                LocalEntitySnapshot(p.id, p.userId, p.updatedAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        p.title == rJson.optString("title") && p.status == rJson.optString("status")
                    } catch (_: Exception) { false }
                }
            }
            "Task" -> dao.getTask(entityId)?.let { t ->
                val json = org.json.JSONObject(mapOf(
                    "id" to t.id, "userId" to t.userId, "planId" to t.planId,
                    "title" to t.title, "status" to t.status,
                    "updatedAt" to t.updatedAt, "createdAt" to t.createdAt
                )).toString()
                LocalEntitySnapshot(t.id, t.userId, t.updatedAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        t.title == rJson.optString("title") && t.status == rJson.optString("status")
                    } catch (_: Exception) { false }
                }
            }
            "Commitment" -> dao.getCommitment(entityId)?.let { c ->
                val json = org.json.JSONObject(mapOf(
                    "id" to c.id, "userId" to c.userId, "title" to c.title,
                    "status" to c.status, "updatedAt" to c.updatedAt, "committedAt" to c.committedAt
                )).toString()
                LocalEntitySnapshot(c.id, c.userId, c.updatedAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        c.title == rJson.optString("title") && c.status == rJson.optString("status")
                    } catch (_: Exception) { false }
                }
            }
            "Reflection" -> dao.getReflection(entityId)?.let { r ->
                val json = org.json.JSONObject(mapOf(
                    "id" to r.id, "userId" to r.userId, "content" to r.content,
                    "sentiment" to r.sentiment, "createdAt" to r.createdAt
                )).toString()
                LocalEntitySnapshot(r.id, r.userId, r.createdAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        r.content == rJson.optString("content", rJson.optString("response", ""))
                    } catch (_: Exception) { false }
                }
            }
            "Memory" -> dao.getMemoryById(entityId)?.let { m ->
                val json = org.json.JSONObject(mapOf(
                    "id" to m.id, "userId" to m.userId, "content" to m.content,
                    "type" to m.type, "status" to m.status, "updatedAt" to m.updatedAt
                )).toString()
                LocalEntitySnapshot(m.id, m.userId, m.updatedAt, json) { remote ->
                    try {
                        val rJson = org.json.JSONObject(remote.payloadJson)
                        m.content == rJson.optString("content") && m.status == rJson.optString("status")
                    } catch (_: Exception) { false }
                }
            }
            else -> null
        }
    }

    internal fun applySingleRemoteEntity(
        targetDao: IronMindDao,
        userId: String,
        entityType: String,
        item: RemoteEntityPayload,
        now: Long
    ) {
        // Step 1: Active Outbox Check
        val activeOutbox = effectiveOutboxDao?.getActiveEntryForEntity(userId, entityType, item.entityId)
        if (activeOutbox != null) {
            val localBaseVersion = activeOutbox.baseVersion
            val isDivergent = if (localBaseVersion != null && localBaseVersion > 0L) {
                item.version > localBaseVersion
            } else {
                item.updatedAt > activeOutbox.createdAt
            }

            if (isDivergent) {
                println("IronMindLifecycle [Sync] [QUARANTINE_REMOTE_DIVERGENCE] entityType=$entityType entityId=${item.entityId} remoteVersion=${item.version} localBaseVersion=$localBaseVersion")
                effectiveOutboxDao?.quarantineEntryToConflict(activeOutbox.operationId, userId, now)
                effectiveSyncConflictDao?.insertConflict(
                    SyncConflictEntity(
                        conflictId = UUID.randomUUID().toString(),
                        operationId = activeOutbox.operationId,
                        userId = userId,
                        entityType = entityType,
                        entityId = item.entityId,
                        localPayload = activeOutbox.payload,
                        remotePayload = item.payloadJson,
                        conflictReason = "REMOTE_VERSION_DIVERGENCE: remoteVersion=${item.version} > localBaseVersion=$localBaseVersion",
                        createdAt = now,
                        updatedAt = now,
                        resolutionStatus = "UNRESOLVED"
                    )
                )
                return
            } else {
                println("IronMindLifecycle [Sync] [SKIPPED_LOCAL_NEWER] entityType=$entityType entityId=${item.entityId} remoteVersion=${item.version} localBaseVersion=$localBaseVersion")
                return
            }
        }

        // Step 1.5: Check Local Tombstone
        val localTombstone = effectiveLocalTombstoneDao?.getTombstone(userId, entityType, item.entityId)
        if (localTombstone != null && localTombstone.status == "PENDING") {
            val tombstoneBaseVer = localTombstone.baseServerVersion
            val isDivergent = if (tombstoneBaseVer != null && tombstoneBaseVer > 0L) {
                item.version > tombstoneBaseVer
            } else {
                item.updatedAt > localTombstone.deletedAt
            }
            if (isDivergent) {
                println("IronMindLifecycle [Sync] [QUARANTINE_TOMBSTONE_DIVERGENCE] entityType=$entityType entityId=${item.entityId} remoteVersion=${item.version} tombstoneBaseVer=$tombstoneBaseVer")
                effectiveSyncConflictDao?.insertConflict(
                    SyncConflictEntity(
                        conflictId = UUID.randomUUID().toString(),
                        operationId = "conflict-${UUID.randomUUID()}",
                        userId = userId,
                        entityType = entityType,
                        entityId = item.entityId,
                        localPayload = """{"deleted":true,"deletedAt":${localTombstone.deletedAt}}""",
                        remotePayload = item.payloadJson,
                        conflictReason = "TOMBSTONE_DIVERGENCE: remoteVersion=${item.version} > tombstoneBaseVer=$tombstoneBaseVer",
                        createdAt = now,
                        updatedAt = now,
                        resolutionStatus = "UNRESOLVED"
                    )
                )
                return
            } else {
                println("IronMindLifecycle [Sync] [SKIPPED_LOCAL_TOMBSTONE_NEWER] entityType=$entityType entityId=${item.entityId} remoteVersion=${item.version} tombstoneBaseVer=$tombstoneBaseVer")
                return
            }
        }

        // Step 2: Read Local Entity State from Room
        val localSnapshot = readLocalEntity(targetDao, entityType, item.entityId)

        if (localSnapshot == null) {
            // Local entity absent from Room
            if (item.isDeleted) {
                // Rule: Remote tombstone on absent local entity -> No-op!
                println("IronMindLifecycle [Sync] [TOMBSTONE_ON_ABSENT_IGNORED] entityType=$entityType entityId=${item.entityId}")
                return
            } else {
                // Insert remote active entity into Room
                println("IronMindLifecycle [Sync] [INSERT_REMOTE_ACTIVE] entityType=$entityType entityId=${item.entityId} version=${item.version}")
                RemotePayloadApplier.apply(
                    ironMindDao = targetDao,
                    entityType = entityType,
                    remotePayload = item.payloadJson,
                    isDeleted = false
                )
                effectiveLocalTombstoneDao?.deleteTombstone(userId, entityType, item.entityId)
                return
            }
        }

        // Local entity present in Room
        if (localSnapshot.userId != userId) {
            println("IronMindLifecycle [Sync] [USER_MISMATCH_SKIPPED] entityType=$entityType entityId=${item.entityId} localUserId=${localSnapshot.userId} requestedUserId=$userId")
            return
        }

        val localUpdatedAt = localSnapshot.updatedAt

        if (item.isDeleted) {
            // Remote is a tombstone
            when {
                item.updatedAt > localUpdatedAt -> {
                    // Newer remote tombstone deletes older local active entity
                    println("IronMindLifecycle [Sync] [DELETE_LOCAL_TOMBSTONE_APPLIED] entityType=$entityType entityId=${item.entityId} remoteUpdatedAt=${item.updatedAt} localUpdatedAt=$localUpdatedAt")
                    when (entityType) {
                        "Goal" -> targetDao.deleteGoal(userId, item.entityId)
                        "Plan" -> targetDao.deletePlan(userId, item.entityId)
                        "Task" -> targetDao.deleteTask(userId, item.entityId)
                        "Commitment" -> targetDao.deleteCommitment(userId, item.entityId)
                        "Reflection" -> targetDao.deleteReflection(userId, item.entityId)
                        "Memory" -> targetDao.deleteMemory(userId, item.entityId)
                    }
                    effectiveLocalTombstoneDao?.insertTombstone(
                        LocalTombstoneEntity(
                            userId = userId,
                            entityType = entityType,
                            entityId = item.entityId,
                            baseServerVersion = item.version,
                            deletedAt = item.updatedAt,
                            status = "SYNCED"
                        )
                    )
                }
                item.updatedAt < localUpdatedAt -> {
                    // Older remote tombstone must never delete newer local active entity!
                    println("IronMindLifecycle [Sync] [OLDER_REMOTE_TOMBSTONE_REJECTED] entityType=$entityType entityId=${item.entityId} remoteUpdatedAt=${item.updatedAt} localUpdatedAt=$localUpdatedAt")
                }
                else -> {
                    // Ambiguous tombstone conflict (equal timestamps)
                    println("IronMindLifecycle [Sync] [AMBIGUOUS_TOMBSTONE_CONFLICT] entityType=$entityType entityId=${item.entityId}")
                    effectiveSyncConflictDao?.insertConflict(
                        SyncConflictEntity(
                            conflictId = UUID.randomUUID().toString(),
                            operationId = "conflict-${UUID.randomUUID()}",
                            userId = userId,
                            entityType = entityType,
                            entityId = item.entityId,
                            localPayload = localSnapshot.serializedJson,
                            remotePayload = item.payloadJson,
                            conflictReason = "AMBIGUOUS_TOMBSTONE_CONFLICT: remote.updatedAt == local.updatedAt ($localUpdatedAt)",
                            createdAt = now,
                            updatedAt = now,
                            resolutionStatus = "UNRESOLVED"
                        )
                    )
                }
            }
            return
        }

        // Remote is active
        when {
            item.updatedAt > localUpdatedAt -> {
                println("IronMindLifecycle [Sync] [UPDATE_LOCAL_ACTIVE] entityType=$entityType entityId=${item.entityId} remoteUpdatedAt=${item.updatedAt} localUpdatedAt=$localUpdatedAt")
                RemotePayloadApplier.apply(
                    ironMindDao = targetDao,
                    entityType = entityType,
                    remotePayload = item.payloadJson,
                    isDeleted = false
                )
            }
            item.updatedAt < localUpdatedAt -> {
                println("IronMindLifecycle [Sync] [LOCAL_NEWER_PRESERVED] entityType=$entityType entityId=${item.entityId} remoteUpdatedAt=${item.updatedAt} localUpdatedAt=$localUpdatedAt")
            }
            else -> {
                if (localSnapshot.isContentEquivalent(item)) {
                    println("IronMindLifecycle [Sync] [IDENTICAL_PAYLOAD_NOOP] entityType=$entityType entityId=${item.entityId}")
                } else {
                    println("IronMindLifecycle [Sync] [EQUAL_VERSION_DIVERGENT_QUARANTINE] entityType=$entityType entityId=${item.entityId}")
                    effectiveSyncConflictDao?.insertConflict(
                        SyncConflictEntity(
                            conflictId = UUID.randomUUID().toString(),
                            operationId = "conflict-${UUID.randomUUID()}",
                            userId = userId,
                            entityType = entityType,
                            entityId = item.entityId,
                            localPayload = localSnapshot.serializedJson,
                            remotePayload = item.payloadJson,
                            conflictReason = "EQUAL_VERSION_DIVERGENT_PAYLOAD: remote.updatedAt == local.updatedAt ($localUpdatedAt) with divergent content",
                            createdAt = now,
                            updatedAt = now,
                            resolutionStatus = "UNRESOLVED"
                        )
                    )
                }
            }
        }
    }

    private class RefCountedMutex {
        val mutex = Mutex()
        val refCount = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val userSyncMutexes = ConcurrentHashMap<String, RefCountedMutex>()

    internal fun activeMutexCount(): Int = userSyncMutexes.size

    internal suspend fun <T> withUserSyncLock(userId: String, action: suspend () -> T): T {
        val holder = userSyncMutexes.compute(userId) { _, existing ->
            (existing ?: RefCountedMutex()).apply { refCount.incrementAndGet() }
        }!!
        return try {
            holder.mutex.withLock {
                action()
            }
        } finally {
            userSyncMutexes.compute(userId) { _, existing ->
                if (existing != null) {
                    if (existing.refCount.decrementAndGet() == 0 && !existing.mutex.isLocked) {
                        null
                    } else {
                        existing
                    }
                } else {
                    null
                }
            }
        }
    }

    override suspend fun sync(userId: String): Result<SyncState, Exception> {
        return withUserSyncLock(userId) {
            executeSync(userId)
        }
    }

    private suspend fun executeSync(userId: String): Result<SyncState, Exception> {
        println("IronMindLifecycle [Sync] [STARTED] userId=$userId")
        val now = System.currentTimeMillis()

        // 1. Crash and lease recovery for this user
        outboxRepository.recoverExpiredLeasesForUser(userId, now)
        val staleCutoff = now - STALE_SYNCING_THRESHOLD_MS
        val recoverResult = outboxRepository.recoverStaleSyncingEntriesForUser(userId, staleCutoff)
        if (recoverResult is Result.Success && recoverResult.data > 0) {
            println("IronMindLifecycle [Sync] [RECOVERED_STALE_ENTRIES] count=${recoverResult.data} userId=$userId")
        }

        // 2. Run bounded legacy ownership recovery for authenticated active user
        outboxRepository.claimLegacyEntriesForUser(userId)

        // 3. Downstream Pull: pull remote changes, detect divergence, update Room & checkpoints
        val pullResult = pullRemoteChanges(userId)
        val pullError = if (pullResult is Result.Failure) pullResult.error.message else null
        if (pullError != null) {
            println("IronMindLifecycle [Sync] [PULL_FAILED_CONTINUING] error=$pullError")
        }

        // 4. Fetch strictly owned pending entries
        val pendingResult = outboxRepository.getPendingEntriesForUser(userId)
        if (pendingResult is Result.Failure) {
            println("IronMindLifecycle [Sync] [FAILED] reason=outbox_read_error")
            return Result.Failure(pendingResult.error)
        }

        val entries = (pendingResult as Result.Success).data
        if (entries.isEmpty()) {
            println("IronMindLifecycle [Sync] [COMPLETED] uploaded=0 conflicts=0 failures=0")
            return Result.Success(
                SyncState(
                    isIdle = true,
                    isSyncing = false,
                    lastSyncedAt = now,
                    lastError = pullError,
                    pendingCount = 0
                )
            )
        }

        var uploaded = 0
        var conflicts = 0
        var failures = 0

        for (entry in entries) {
            val payloadUserId = try {
                val json = org.json.JSONObject(entry.payload)
                if (json.has("userId")) json.optString("userId").takeIf { it.isNotBlank() } else null
            } catch (_: Exception) {
                null
            }

            if (payloadUserId == null) {
                // Ambiguous or malformed record: quarantine to CONFLICT to prevent endless retries or cross-user upload
                println("IronMindLifecycle [Sync] [QUARANTINED_INVALID_PAYLOAD] operationId=${entry.operationId} entityType=${entry.entityType}")
                outboxRepository.updateStatusForUser(
                    operationId = entry.operationId,
                    userId = userId,
                    status = SyncStatus.CONFLICT,
                    retryCount = entry.retryCount,
                    lastAttemptAt = now
                )
                conflicts++
                continue
            }

            if (payloadUserId != userId) {
                println("IronMindLifecycle [Sync] [SKIPPED_USER_MISMATCH] operationId=${entry.operationId} payloadUserId=$payloadUserId targetUserId=$userId")
                continue
            }

            // Atomic status transition with lease token: claim entry for sync
            val leaseToken = UUID.randomUUID().toString()
            val leaseExpiresAt = now + LEASE_DURATION_MS
            val claimResult = outboxRepository.claimEntryWithLease(
                operationId = entry.operationId,
                userId = userId,
                leaseToken = leaseToken,
                leaseExpiresAt = leaseExpiresAt,
                now = now
            )
            val claimedRows = (claimResult as? Result.Success)?.data ?: 0
            if (claimedRows <= 0) {
                println("IronMindLifecycle [Sync] [SKIPPED_ALREADY_CLAIMED] operationId=${entry.operationId}")
                continue
            }

            val entryWithLease = entry.copy(leaseToken = leaseToken, leaseExpiresAt = leaseExpiresAt)
            val uploadResult = syncRepository.upload(userId, entryWithLease)

            when {
                uploadResult is Result.Success && uploadResult.data is SyncUploadResult.Success -> {
                    val completeResult = outboxRepository.completeEntryWithLease(entry.operationId, userId, leaseToken)
                    val completedRows = (completeResult as? Result.Success)?.data ?: 0
                    if (completedRows > 0) {
                        uploaded++
                    } else {
                        println("IronMindLifecycle [Sync] [LEASE_EXPIRED_COMPLETE_DROPPED] operationId=${entry.operationId}")
                    }
                }
                uploadResult is Result.Success && uploadResult.data is SyncUploadResult.Conflict -> {
                    val conflictData = uploadResult.data as SyncUploadResult.Conflict
                    val conflictResult = outboxRepository.conflictEntryWithLease(
                        operationId = entry.operationId,
                        userId = userId,
                        leaseToken = leaseToken,
                        retryCount = entry.retryCount + 1,
                        lastAttemptAt = now
                    )
                    val conflictRows = (conflictResult as? Result.Success)?.data ?: 0
                    if (conflictRows > 0) {
                        // Persist durable conflict record in sync_conflicts table
                        syncConflictDao?.insertConflict(
                            SyncConflictEntity(
                                conflictId = UUID.randomUUID().toString(),
                                operationId = entry.operationId,
                                userId = userId,
                                entityType = entry.entityType,
                                entityId = entry.entityId,
                                localPayload = entry.payload,
                                remotePayload = conflictData.remotePayload,
                                conflictReason = conflictData.conflictReason,
                                createdAt = now,
                                updatedAt = now,
                                resolutionStatus = "UNRESOLVED"
                            )
                        )
                        println("IronMindLifecycle [Sync] [CONFLICT] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
                        conflicts++
                    } else {
                        println("IronMindLifecycle [Sync] [LEASE_EXPIRED_CONFLICT_DROPPED] operationId=${entry.operationId}")
                    }
                }
                else -> {
                    outboxRepository.failEntryWithLease(
                        operationId = entry.operationId,
                        userId = userId,
                        leaseToken = leaseToken,
                        retryCount = entry.retryCount + 1,
                        lastAttemptAt = now
                    )
                    println("IronMindLifecycle [Sync] [RETRY] operationId=${entry.operationId} retryCount=${entry.retryCount + 1}")
                    failures++
                }
            }
        }

        val pendingCountResult = outboxRepository.countPendingEntriesForUser(userId)
        val remainingPending = (pendingCountResult as? Result.Success)?.data ?: 0

        println("IronMindLifecycle [Sync] [COMPLETED] uploaded=$uploaded conflicts=$conflicts failures=$failures")

        val combinedError = when {
            failures > 0 -> "Some entries failed to sync"
            pullError != null -> pullError
            else -> null
        }

        return Result.Success(
            SyncState(
                isIdle = remainingPending == 0,
                isSyncing = false,
                lastSyncedAt = now,
                lastError = combinedError,
                pendingCount = remainingPending
            )
        )
    }

    /**
     * Executes bounded incremental Firestore pull using compound
     * (updatedAt, documentId) pagination, enforcing authoritative session generation
     * checks, active lease fencing, and atomic Room persistence.
     *
     * In Stage B.2:
     * When [applyToDatabase] is true, remote entities, active outbox protections,
     * conflicts, and tombstones are reconciled according to the A.6.5 decision matrix
     * and committed together with lease progress inside an atomic [database.withTransaction].
     *
     * When [applyToDatabase] is false (Stage B.1 mode), local Room entities are preserved
     * without modification, while checkpoints and in-memory payloads advance.
     */
    suspend fun pullEntityIncremental(
        userId: String,
        entityType: String,
        limit: Int = PULL_BATCH_LIMIT,
        callerGeneration: Long? = null,
        leaseOwnerToken: String? = null,
        applyToDatabase: Boolean = false
    ): Result<SyncPullResult.Success, Exception> {
        val activeUserId = userSessionProvider?.getActiveUserId() ?: userId
        if (activeUserId != userId) {
            println("IronMindLifecycle [Sync] [PULL_ABORTED_USER_CHANGED] requestedUserId=$userId activeUserId=$activeUserId")
            return Result.Failure(IllegalStateException("Active session changed during pull: requested=$userId, active=$activeUserId"))
        }

        val authGen = effectiveSessionStateDao?.getSessionState()?.currentGeneration
        val expectedGen = callerGeneration ?: userSessionProvider?.getSessionGeneration() ?: authGen ?: 1L

        // Stale session generation verification
        if (authGen != null && expectedGen != authGen) {
            println("IronMindLifecycle [Sync] [PULL_ABORTED_STALE_GENERATION] callerGeneration=$expectedGen authoritativeGen=$authGen")
            return Result.Failure(IllegalStateException("Stale session generation: caller=$expectedGen, authoritative=$authGen"))
        }

        val leaseDao = effectiveLeaseDao
        val ownerToken = leaseOwnerToken ?: UUID.randomUUID().toString()
        var leaseAcquiredLocally = false

        if (leaseDao != null && leaseOwnerToken == null) {
            val now = System.currentTimeMillis()
            val acq = leaseDao.acquireOrTakeoverLease(
                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                callerOwnerToken = ownerToken,
                callerGeneration = expectedGen,
                leaseDurationMs = LEASE_DURATION_MS,
                now = now
            )
            if (acq <= 0L) {
                println("IronMindLifecycle [Sync] [PULL_ABORTED_LEASE_REJECTED] generation=$expectedGen")
                return Result.Failure(IllegalStateException("Failed to acquire sync lease: active lease held or session generation ($expectedGen) is stale"))
            }
            leaseAcquiredLocally = true
        }

        return try {
            val storedCursor = checkpointStore?.getLastCursor(userId, entityType) ?: SyncCursor(0L, "")
            var activeCursor = storedCursor
            var currentSavedCursor = storedCursor
            val accumulatedItems = mutableListOf<RemoteEntityPayload>()
            var hasMore = true

            while (hasMore) {
                if (userSessionProvider != null && userSessionProvider.getActiveUserId() != userId) {
                    println("IronMindLifecycle [Sync] [PULL_ABORTED_USER_CHANGED] entityType=$entityType")
                    return Result.Failure(IllegalStateException("Active session changed during pull loop"))
                }

                // Verify and renew lease validity before each page pull
                if (leaseDao != null) {
                    val now = System.currentTimeMillis()
                    val renewed = leaseDao.renewLease(
                        leaseKey = LEASE_KEY_SYNC_EXECUTION,
                        ownerToken = ownerToken,
                        expectedGeneration = expectedGen,
                        leaseDurationMs = LEASE_DURATION_MS,
                        now = now
                    )
                    if (renewed <= 0) {
                        println("IronMindLifecycle [Sync] [PULL_ABORTED_LEASE_LOST] ownerToken=$ownerToken generation=$expectedGen")
                        return Result.Failure(IllegalStateException("Sync lease lost or expired during pagination"))
                    }
                }

                val pullResult = syncRepository.pull(
                    userId = userId,
                    entityType = entityType,
                    cursor = activeCursor,
                    limit = limit
                )

                if (pullResult is Result.Failure) {
                    println("IronMindLifecycle [Sync] [PULL_ENTITY_FAILED] entityType=$entityType error=${pullResult.error.message}")
                    return Result.Failure(pullResult.error)
                }

                val successData = (pullResult as Result.Success).data
                if (successData !is SyncPullResult.Success) {
                    break
                }

                val pageItems = successData.items
                if (pageItems.isEmpty()) {
                    // Empty collection or empty final page: do not alter checkpoint
                    hasMore = false
                    break
                }

                val lastItem = pageItems.last()
                val nextCursor = SyncCursor(timestamp = lastItem.updatedAt, documentId = lastItem.entityId)

                if (applyToDatabase) {
                    val targetDao = effectiveIronMindDao
                        ?: return Result.Failure(IllegalStateException("IronMindDao unavailable for database application"))

                    val targetDb = database
                    val now = System.currentTimeMillis()

                    val applyPageInTransaction: suspend () -> Unit = {
                        for (item in pageItems) {
                            applySingleRemoteEntity(
                                targetDao = targetDao,
                                userId = userId,
                                entityType = entityType,
                                item = item,
                                now = now
                            )
                        }

                        // Persist checkpoint in sync_checkpoints inside the transaction
                        effectiveSyncCheckpointDao?.upsertCheckpoint(
                            SyncCheckpointEntity(
                                userId = userId,
                                entityType = entityType,
                                traversalType = TRAVERSAL_DOWNSTREAM,
                                lastTimestamp = nextCursor.timestamp,
                                lastDocumentId = nextCursor.documentId,
                                updatedAt = now
                            )
                        )

                        // Atomic progress commit and lease fencing check:
                        if (leaseDao != null) {
                            val commitRows = leaseDao.commitPageProgress(
                                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                                ownerToken = ownerToken,
                                expectedGeneration = expectedGen,
                                nextCursor = "${entityType}:${nextCursor.timestamp}:${nextCursor.documentId}",
                                leaseDurationMs = LEASE_DURATION_MS,
                                now = System.currentTimeMillis()
                            )
                            if (commitRows <= 0) {
                                throw IllegalStateException("Lease fencing failed at page commit boundary: lease lost or expired (ownerToken=$ownerToken, generation=$expectedGen)")
                            }
                        }
                    }

                    try {
                        if (targetDb != null) {
                            targetDb.withTransaction {
                                applyPageInTransaction()
                            }
                        } else {
                            applyPageInTransaction()
                        }
                    } catch (e: Exception) {
                        println("IronMindLifecycle [Sync] [PAGE_APPLY_FAILED] entityType=$entityType error=${e.message}")
                        return Result.Failure(e)
                    }

                    // Monotonically advance external checkpointStore only after SQLite transaction commits
                    val isNewer = nextCursor.timestamp > currentSavedCursor.timestamp ||
                        (nextCursor.timestamp == currentSavedCursor.timestamp && nextCursor.documentId > currentSavedCursor.documentId)
                    if (isNewer) {
                        checkpointStore?.setLastCursor(userId, entityType, nextCursor)
                        currentSavedCursor = nextCursor
                    }
                } else {
                    // Stage B.1 mode: do not mutate Room; advance in-memory cursor and external checkpointStore
                    val isNewer = nextCursor.timestamp > currentSavedCursor.timestamp ||
                        (nextCursor.timestamp == currentSavedCursor.timestamp && nextCursor.documentId > currentSavedCursor.documentId)
                    if (isNewer) {
                        checkpointStore?.setLastCursor(userId, entityType, nextCursor)
                        currentSavedCursor = nextCursor
                    }
                }

                accumulatedItems.addAll(pageItems)
                activeCursor = nextCursor
                hasMore = successData.hasMore
            }

            Result.Success(SyncPullResult.Success(items = accumulatedItems, hasMore = false))
        } finally {
            if (leaseAcquiredLocally && leaseDao != null) {
                leaseDao.releaseLease(
                    leaseKey = LEASE_KEY_SYNC_EXECUTION,
                    ownerToken = ownerToken,
                    expectedGeneration = expectedGen,
                    now = System.currentTimeMillis()
                )
            }
        }
    }

    internal suspend fun pullRemoteChanges(
        userId: String,
        lookbackWindowMs: Long = 0L,
        applyToDatabase: Boolean = true
    ): Result<Int, Exception> {
        val targetDao = effectiveIronMindDao
        if (targetDao == null && applyToDatabase) {
            return Result.Success(0)
        }

        if (userSessionProvider != null && userSessionProvider.getActiveUserId() != userId) {
            println("IronMindLifecycle [Sync] [PULL_ABORTED_USER_CHANGED] requestedUserId=$userId activeUserId=${userSessionProvider.getActiveUserId()}")
            return Result.Failure(IllegalStateException("Active session changed during pull"))
        }

        val entityTypes = listOf("Goal", "Plan", "Task", "Commitment", "Reflection", "Memory")
        val leaseDao = effectiveLeaseDao
        val authGen = effectiveSessionStateDao?.getSessionState()?.currentGeneration
        val expectedGen = userSessionProvider?.getSessionGeneration() ?: authGen ?: 1L
        val ownerToken = UUID.randomUUID().toString()
        var leaseAcquired = false

        if (leaseDao != null) {
            val acq = leaseDao.acquireOrTakeoverLease(
                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                callerOwnerToken = ownerToken,
                callerGeneration = expectedGen,
                leaseDurationMs = LEASE_DURATION_MS,
                now = System.currentTimeMillis()
            )
            if (acq <= 0L) {
                return Result.Failure(IllegalStateException("Failed to acquire sync lease: active lease held or session generation ($expectedGen) is stale"))
            }
            leaseAcquired = true
        }

        var totalPulledAndApplied = 0
        try {
            for (entityType in entityTypes) {
                val entityResult = pullEntityIncremental(
                    userId = userId,
                    entityType = entityType,
                    limit = PULL_BATCH_LIMIT,
                    callerGeneration = expectedGen,
                    leaseOwnerToken = ownerToken,
                    applyToDatabase = applyToDatabase
                )
                if (entityResult is Result.Failure) {
                    return Result.Failure(entityResult.error)
                }
                val success = (entityResult as Result.Success).data
                totalPulledAndApplied += success.items.size
            }
            println("IronMindLifecycle [Sync] [PULL_COMPLETED] userId=$userId totalApplied=$totalPulledAndApplied")
            return Result.Success(totalPulledAndApplied)
        } finally {
            if (leaseAcquired && leaseDao != null) {
                leaseDao.releaseLease(
                    leaseKey = LEASE_KEY_SYNC_EXECUTION,
                    ownerToken = ownerToken,
                    expectedGeneration = expectedGen,
                    now = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Traverses a single entity collection by documentId to discover and graduate legacy documents.
     * Manages SQLite checkpoints with traversalType = "TIER3_LEGACY".
     * Advances cycleCount and lastCycleCompletedAt upon full collection completion.
     */
    suspend fun reconcileEntityTier3Legacy(
        userId: String,
        entityType: String,
        limit: Int = PULL_BATCH_LIMIT,
        applyToDatabase: Boolean = true,
        leaseOwnerToken: String? = null,
        callerGeneration: Long? = null
    ): Result<SyncPullResult.Success, Exception> {
        val authGen = effectiveSessionStateDao?.getSessionState()?.currentGeneration
        val expectedGen = callerGeneration ?: userSessionProvider?.getSessionGeneration() ?: authGen ?: 1L
        if (authGen != null && expectedGen != authGen) {
            println("IronMindLifecycle [Sync] [TIER3_ABORTED_STALE_GENERATION] callerGeneration=$expectedGen authoritativeGen=$authGen")
            return Result.Failure(IllegalStateException("Stale session generation: caller=$expectedGen, authoritative=$authGen"))
        }

        val leaseDao = effectiveLeaseDao
        val ownerToken = leaseOwnerToken ?: UUID.randomUUID().toString()
        var leaseAcquiredLocally = false

        if (leaseDao != null && leaseOwnerToken == null) {
            val now = System.currentTimeMillis()
            val acq = leaseDao.acquireOrTakeoverLease(
                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                callerOwnerToken = ownerToken,
                callerGeneration = expectedGen,
                leaseDurationMs = LEASE_DURATION_MS,
                now = now
            )
            if (acq <= 0L) {
                println("IronMindLifecycle [Sync] [TIER3_ABORTED_LEASE_REJECTED] generation=$expectedGen")
                return Result.Failure(IllegalStateException("Failed to acquire sync lease: active lease held or session generation ($expectedGen) is stale"))
            }
            leaseAcquiredLocally = true
        }

        return try {
            val checkpointDao = effectiveSyncCheckpointDao
            val existingCheckpoint = checkpointDao?.getCheckpoint(userId, entityType, TRAVERSAL_TIER3_LEGACY)
            var activeDocId = existingCheckpoint?.lastDocumentId ?: ""
            val currentCycleCount = existingCheckpoint?.cycleCount ?: 0
            val lastCycleCompletedAt = existingCheckpoint?.lastCycleCompletedAt ?: 0L

            val accumulatedItems = mutableListOf<RemoteEntityPayload>()
            var hasMore = true

            while (hasMore) {
                if (userSessionProvider != null && userSessionProvider.getActiveUserId() != userId) {
                    println("IronMindLifecycle [Sync] [TIER3_ABORTED_USER_CHANGED] entityType=$entityType")
                    return Result.Failure(IllegalStateException("Active session changed during Tier 3 pull loop"))
                }

                // Verify and renew lease validity before each page pull
                if (leaseDao != null) {
                    val now = System.currentTimeMillis()
                    val renewed = leaseDao.renewLease(
                        leaseKey = LEASE_KEY_SYNC_EXECUTION,
                        ownerToken = ownerToken,
                        expectedGeneration = expectedGen,
                        leaseDurationMs = LEASE_DURATION_MS,
                        now = now
                    )
                    if (renewed <= 0) {
                        println("IronMindLifecycle [Sync] [TIER3_ABORTED_LEASE_LOST] ownerToken=$ownerToken generation=$expectedGen")
                        return Result.Failure(IllegalStateException("Sync lease lost or expired during Tier 3 pagination"))
                    }
                }

                val pullResult = syncRepository.discoverLegacyDocuments(
                    userId = userId,
                    entityType = entityType,
                    startAfterDocId = activeDocId,
                    limit = limit
                )

                if (pullResult is Result.Failure) {
                    println("IronMindLifecycle [Sync] [TIER3_PULL_FAILED] entityType=$entityType error=${pullResult.error.message}")
                    return Result.Failure(pullResult.error)
                }

                val successData = (pullResult as Result.Success).data
                if (successData !is SyncPullResult.Success) {
                    break
                }

                val pageItems = successData.items
                hasMore = successData.hasMore

                if (pageItems.isEmpty()) {
                    // Empty collection or end of traversal reached: perform cycle rollover
                    val now = System.currentTimeMillis()
                    val nextCycleCount = currentCycleCount + 1
                    val nextCycleCompletedAt = now

                    val targetDb = database
                    val completeCycleInTransaction: suspend () -> Unit = {
                        checkpointDao?.upsertCheckpoint(
                            SyncCheckpointEntity(
                                userId = userId,
                                entityType = entityType,
                                traversalType = TRAVERSAL_TIER3_LEGACY,
                                lastTimestamp = 0L,
                                lastDocumentId = "",
                                cycleCount = nextCycleCount,
                                lastCycleCompletedAt = nextCycleCompletedAt,
                                updatedAt = now
                            )
                        )
                        if (leaseDao != null) {
                            val compRows = leaseDao.completeGeneration(
                                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                                ownerToken = ownerToken,
                                expectedGeneration = expectedGen,
                                leaseDurationMs = LEASE_DURATION_MS,
                                now = now
                            )
                            if (compRows <= 0) {
                                throw IllegalStateException("Lease fencing failed at cycle completion: lease lost or superseded")
                            }
                        }
                    }

                    if (targetDb != null) {
                        targetDb.withTransaction {
                            completeCycleInTransaction()
                        }
                    } else {
                        completeCycleInTransaction()
                    }
                    break
                }

                val lastItem = pageItems.last()
                val nextDocId = lastItem.entityId

                val targetDao = effectiveIronMindDao
                val targetDb = database
                val now = System.currentTimeMillis()

                val applyPageInTransaction: suspend () -> Unit = {
                    if (applyToDatabase && targetDao != null) {
                        for (item in pageItems) {
                            applySingleRemoteEntity(
                                targetDao = targetDao,
                                userId = userId,
                                entityType = entityType,
                                item = item,
                                now = now
                            )
                        }
                    }

                    // Persist checkpoint in sync_checkpoints inside the transaction
                    checkpointDao?.upsertCheckpoint(
                        SyncCheckpointEntity(
                            userId = userId,
                            entityType = entityType,
                            traversalType = TRAVERSAL_TIER3_LEGACY,
                            lastTimestamp = 0L,
                            lastDocumentId = nextDocId,
                            cycleCount = currentCycleCount,
                            lastCycleCompletedAt = lastCycleCompletedAt,
                            updatedAt = now
                        )
                    )

                    // Atomic progress commit and lease fencing check:
                    if (leaseDao != null) {
                        val commitRows = leaseDao.commitPageProgress(
                            leaseKey = LEASE_KEY_SYNC_EXECUTION,
                            ownerToken = ownerToken,
                            expectedGeneration = expectedGen,
                            nextCursor = "${entityType}:${nextDocId}",
                            leaseDurationMs = LEASE_DURATION_MS,
                            now = System.currentTimeMillis()
                        )
                        if (commitRows <= 0) {
                            throw IllegalStateException("Lease fencing failed at page commit boundary: lease lost or expired (ownerToken=$ownerToken, generation=$expectedGen)")
                        }
                    }
                }

                if (targetDb != null) {
                    targetDb.withTransaction {
                        applyPageInTransaction()
                    }
                } else {
                    applyPageInTransaction()
                }

                accumulatedItems.addAll(pageItems)
                activeDocId = nextDocId

                if (!hasMore) {
                    // Reached end of collection on the final non-empty page: perform cycle rollover
                    val cycleNow = System.currentTimeMillis()
                    val nextCycleCount = currentCycleCount + 1
                    val nextCycleCompletedAt = cycleNow

                    val completeCycleInTransaction: suspend () -> Unit = {
                        checkpointDao?.upsertCheckpoint(
                            SyncCheckpointEntity(
                                userId = userId,
                                entityType = entityType,
                                traversalType = TRAVERSAL_TIER3_LEGACY,
                                lastTimestamp = 0L,
                                lastDocumentId = "",
                                cycleCount = nextCycleCount,
                                lastCycleCompletedAt = nextCycleCompletedAt,
                                updatedAt = cycleNow
                            )
                        )
                        if (leaseDao != null) {
                            val compRows = leaseDao.completeGeneration(
                                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                                ownerToken = ownerToken,
                                expectedGeneration = expectedGen,
                                leaseDurationMs = LEASE_DURATION_MS,
                                now = cycleNow
                            )
                            if (compRows <= 0) {
                                throw IllegalStateException("Lease fencing failed at cycle completion: lease lost or superseded")
                            }
                        }
                    }

                    if (targetDb != null) {
                        targetDb.withTransaction {
                            completeCycleInTransaction()
                        }
                    } else {
                        completeCycleInTransaction()
                    }
                    break
                }
            }

            Result.Success(SyncPullResult.Success(items = accumulatedItems, hasMore = false))
        } finally {
            if (leaseAcquiredLocally && leaseDao != null) {
                leaseDao.releaseLease(
                    leaseKey = LEASE_KEY_SYNC_EXECUTION,
                    ownerToken = ownerToken,
                    expectedGeneration = expectedGen,
                    now = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Reconciles Tier 3 legacy documents across all six domain entity types.
     * Evaluates session generation, acquires the durable sync execution lock,
     * iterates across entities, and safely releases the lock.
     */
    suspend fun reconcileTier3Legacy(
        userId: String,
        limit: Int = PULL_BATCH_LIMIT,
        applyToDatabase: Boolean = true
    ): Result<Int, Exception> {
        val targetDao = effectiveIronMindDao
        if (targetDao == null && applyToDatabase) {
            return Result.Success(0)
        }

        if (userSessionProvider != null && userSessionProvider.getActiveUserId() != userId) {
            println("IronMindLifecycle [Sync] [TIER3_ABORTED_USER_CHANGED] requestedUserId=$userId activeUserId=${userSessionProvider.getActiveUserId()}")
            return Result.Failure(IllegalStateException("Active session changed during Tier 3 reconciliation"))
        }

        val entityTypes = listOf("Goal", "Plan", "Task", "Commitment", "Reflection", "Memory")
        val leaseDao = effectiveLeaseDao
        val authGen = effectiveSessionStateDao?.getSessionState()?.currentGeneration
        val expectedGen = userSessionProvider?.getSessionGeneration() ?: authGen ?: 1L
        val ownerToken = UUID.randomUUID().toString()
        var leaseAcquired = false

        if (leaseDao != null) {
            val acq = leaseDao.acquireOrTakeoverLease(
                leaseKey = LEASE_KEY_SYNC_EXECUTION,
                callerOwnerToken = ownerToken,
                callerGeneration = expectedGen,
                leaseDurationMs = LEASE_DURATION_MS,
                now = System.currentTimeMillis()
            )
            if (acq <= 0L) {
                return Result.Failure(IllegalStateException("Failed to acquire sync lease: active lease held or session generation ($expectedGen) is stale"))
            }
            leaseAcquired = true
        }

        var totalGraduatedAndApplied = 0
        try {
            for (entityType in entityTypes) {
                val entityResult = reconcileEntityTier3Legacy(
                    userId = userId,
                    entityType = entityType,
                    limit = limit,
                    applyToDatabase = applyToDatabase,
                    leaseOwnerToken = ownerToken,
                    callerGeneration = expectedGen
                )
                if (entityResult is Result.Failure) {
                    return Result.Failure(entityResult.error)
                }
                val success = (entityResult as Result.Success).data
                totalGraduatedAndApplied += success.items.size
            }
            println("IronMindLifecycle [Sync] [TIER3_ALL_COMPLETED] userId=$userId totalApplied=$totalGraduatedAndApplied")
            return Result.Success(totalGraduatedAndApplied)
        } finally {
            if (leaseAcquired && leaseDao != null) {
                leaseDao.releaseLease(
                    leaseKey = LEASE_KEY_SYNC_EXECUTION,
                    ownerToken = ownerToken,
                    expectedGeneration = expectedGen,
                    now = System.currentTimeMillis()
                )
            }
        }
    }
}
