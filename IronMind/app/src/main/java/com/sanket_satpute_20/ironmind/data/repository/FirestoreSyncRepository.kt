package com.sanket_satpute_20.ironmind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.repository.RemoteEntityPayload
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
import com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Firestore-backed SyncRepository implementation.
 * This is the ONLY class in the project that imports Firestore types.
 * The domain layer knows nothing about Firestore.
 *
 * Entity-specific conflict strategies per DATA_CONTRACT §134:
 * - UPSERT uses Firestore merge semantics (no blind overwrite)
 * - If remote document has a higher updatedAt, we treat it as a conflict
 */
class FirestoreSyncRepository(
    private val firestore: FirebaseFirestore
) : SyncRepository {

    override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
        return try {
            val collectionPath = collectionPathFor(entry.entityType)
                ?: return Result.Failure(Exception("Unknown entityType: ${entry.entityType}"))

            val docRef = firestore
                .collection("users")
                .document(userId)
                .collection(collectionPath)
                .document(entry.entityId)

            val uploadResult = firestore.runTransaction { tx ->
                val remoteSnapshot = tx.get(docRef)
                val remoteExists = remoteSnapshot.exists()

                // Determine remote monotonic version (fallback to remote updatedAt for legacy documents)
                val remoteVersion = if (remoteExists) {
                    val rawVersion = remoteSnapshot.getLong("version")
                    val rawUpdatedAt = remoteSnapshot.getLong("updatedAt")

                    if (rawVersion == null && rawUpdatedAt == null) {
                        val remoteData = remoteSnapshot.data ?: emptyMap<String, Any?>()
                        val remotePayloadJson = JSONObject(remoteData).toString()
                        val conflictReason = "Remote document lacks version and updatedAt metadata (unversioned/corrupt)"
                        println("IronMindLifecycle [Sync] [CONFLICT_DETECTED] operationId=${entry.operationId} entityId=${entry.entityId} reason=$conflictReason")
                        return@runTransaction SyncUploadResult.Conflict(
                            remotePayload = remotePayloadJson,
                            conflictReason = conflictReason
                        )
                    }

                    val calculatedVersion = rawVersion ?: rawUpdatedAt ?: 0L
                    if (calculatedVersion < 0L) {
                        val remoteData = remoteSnapshot.data ?: emptyMap<String, Any?>()
                        val remotePayloadJson = JSONObject(remoteData).toString()
                        val conflictReason = "Remote document has invalid negative version ($calculatedVersion)"
                        println("IronMindLifecycle [Sync] [CONFLICT_DETECTED] operationId=${entry.operationId} entityId=${entry.entityId} reason=$conflictReason")
                        return@runTransaction SyncUploadResult.Conflict(
                            remotePayload = remotePayloadJson,
                            conflictReason = conflictReason
                        )
                    }
                    calculatedVersion
                } else {
                    0L
                }

                // Determine expected baseVersion from outbox entry or payload
                val localPayload = parsePayload(entry.payload)
                val expectedBaseVersion = entry.baseVersion ?: ((localPayload["baseVersion"] as? Number)?.toLong()) ?: 0L

                // If remote document exists and its version is strictly newer than expected baseVersion -> Conflict!
                if (remoteExists && remoteVersion > expectedBaseVersion) {
                    val remoteData = remoteSnapshot.data ?: emptyMap<String, Any?>()
                    val remotePayloadJson = JSONObject(remoteData).toString()
                    val conflictReason = "Remote version ($remoteVersion) is newer than expected baseVersion ($expectedBaseVersion)"
                    println("IronMindLifecycle [Sync] [CONFLICT_DETECTED] operationId=${entry.operationId} entityId=${entry.entityId} reason=$conflictReason")
                    return@runTransaction SyncUploadResult.Conflict(
                        remotePayload = remotePayloadJson,
                        conflictReason = conflictReason
                    )
                }

                when (entry.operationType) {
                    OutboxOperationType.UPSERT -> {
                        val nextVersion = (maxOf(remoteVersion, expectedBaseVersion) + 1L).coerceAtLeast(1L)
                        val payloadToSave = localPayload.toMutableMap()
                        val now = System.currentTimeMillis()
                        payloadToSave["version"] = nextVersion
                        payloadToSave["isDeleted"] = false
                        val localUpdatedAt = (payloadToSave["updatedAt"] as? Number)?.toLong() ?: now
                        val publicationTime = maxOf(localUpdatedAt, now)
                        payloadToSave["updatedAt"] = publicationTime
                        // Merge so we don't clobber remote fields not present in local payload
                        tx.set(docRef, payloadToSave, SetOptions.merge())
                        println("IronMindLifecycle [Sync] [UPLOADED] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId} version=$nextVersion")
                        SyncUploadResult.Success
                    }
                    OutboxOperationType.DELETE -> {
                        val nextVersion = (maxOf(remoteVersion, expectedBaseVersion) + 1L).coerceAtLeast(1L)
                        val now = System.currentTimeMillis()
                        val tombstone = mapOf(
                            "id" to entry.entityId,
                            "userId" to userId,
                            "isDeleted" to true,
                            "deletedAt" to now,
                            "updatedAt" to now,
                            "version" to nextVersion
                        )
                        tx.set(docRef, tombstone, SetOptions.merge())
                        println("IronMindLifecycle [Sync] [DELETED_REMOTE_TOMBSTONE] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId} version=$nextVersion")
                        SyncUploadResult.Success
                    }
                }
            }.await()

            Result.Success(uploadResult)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            println("IronMindLifecycle [Sync] [UPLOAD_FAILED] operationId=${entry.operationId} error=${e.message}")
            Result.Failure(e)
        }
    }

    override suspend fun pull(
        userId: String,
        entityType: String,
        sinceTimestamp: Long,
        limit: Int
    ): Result<SyncPullResult, Exception> {
        return pull(userId, entityType, SyncCursor(sinceTimestamp, ""), limit)
    }

    override suspend fun pull(
        userId: String,
        entityType: String,
        cursor: SyncCursor,
        limit: Int
    ): Result<SyncPullResult, Exception> {
        return try {
            val collectionPath = collectionPathFor(entityType)
                ?: return Result.Failure(IllegalArgumentException("Unknown entityType: $entityType"))

            val collectionRef = firestore
                .collection("users")
                .document(userId)
                .collection(collectionPath)

            val queryLimit = (limit.coerceAtLeast(1) + 1).toLong()

            val baseQuery = collectionRef
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), com.google.firebase.firestore.Query.Direction.ASCENDING)

            val snapshot = when {
                cursor.timestamp > 0L && cursor.documentId.isNotBlank() -> {
                    baseQuery
                        .startAfter(cursor.timestamp, cursor.documentId)
                        .limit(queryLimit)
                        .get()
                        .await()
                }
                cursor.timestamp > 0L && cursor.documentId.isBlank() -> {
                    // Legacy migration: startAt(timestamp, "") includes all documents sharing that timestamp
                    baseQuery
                        .startAt(cursor.timestamp, "")
                        .limit(queryLimit)
                        .get()
                        .await()
                }
                else -> {
                    // Cold start: scan from beginning of updatedAt index
                    baseQuery
                        .limit(queryLimit)
                        .get()
                        .await()
                }
            }

            val docs = snapshot.documents
            val hasMore = docs.size > limit
            val returnedDocs = if (hasMore) docs.take(limit) else docs

            val items = mutableListOf<RemoteEntityPayload>()
            for (doc in returnedDocs) {
                val docData = doc.data
                if (docData == null) {
                    val errorMsg = "Malformed remote document: ${doc.id} has null data"
                    println("IronMindLifecycle [Sync] [PULL_MALFORMED_DOC] docId=${doc.id} error=$errorMsg")
                    return Result.Failure(IllegalStateException(errorMsg))
                }
                val entityId = doc.id
                if (entityId.isBlank()) {
                    val errorMsg = "Malformed remote document: blank document ID"
                    println("IronMindLifecycle [Sync] [PULL_MALFORMED_DOC] error=$errorMsg")
                    return Result.Failure(IllegalStateException(errorMsg))
                }

                val rawUpdatedAt = (docData["updatedAt"] as? Number)?.toLong()
                    ?: (docData["deletedAt"] as? Number)?.toLong()
                    ?: (docData["createdAt"] as? Number)?.toLong()

                if (rawUpdatedAt == null) {
                    val errorMsg = "Malformed remote document: $entityId is missing updatedAt/deletedAt/createdAt"
                    println("IronMindLifecycle [Sync] [PULL_MALFORMED_DOC] docId=$entityId error=$errorMsg")
                    return Result.Failure(IllegalStateException(errorMsg))
                }

                val rawVersion = (docData["version"] as? Number)?.toLong() ?: rawUpdatedAt
                val isDeleted = (docData["isDeleted"] as? Boolean) ?: false
                val rawJson = JSONObject(docData).toString()

                items.add(
                    RemoteEntityPayload(
                        entityType = entityType,
                        entityId = entityId,
                        version = rawVersion,
                        updatedAt = rawUpdatedAt,
                        isDeleted = isDeleted,
                        payloadJson = rawJson
                    )
                )
            }

            Result.Success(SyncPullResult.Success(items = items, hasMore = hasMore))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [Sync] [PULL_FAILED] entityType=$entityType userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }

    override suspend fun discoverLegacyDocuments(
        userId: String,
        entityType: String,
        startAfterDocId: String,
        limit: Int
    ): Result<SyncPullResult, Exception> {
        return try {
            val collectionPath = collectionPathFor(entityType)
                ?: return Result.Failure(IllegalArgumentException("Unknown entityType: $entityType"))

            val collectionRef = firestore
                .collection("users")
                .document(userId)
                .collection(collectionPath)

            val queryLimit = (limit.coerceAtLeast(1) + 1).toLong()

            val baseQuery = collectionRef
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), com.google.firebase.firestore.Query.Direction.ASCENDING)

            val snapshot = if (startAfterDocId.isNotBlank()) {
                baseQuery
                    .startAfter(startAfterDocId)
                    .limit(queryLimit)
                    .get()
                    .await()
            } else {
                baseQuery
                    .limit(queryLimit)
                    .get()
                    .await()
            }

            val docs = snapshot.documents
            val hasMore = docs.size > limit
            val returnedDocs = if (hasMore) docs.take(limit) else docs

            val items = mutableListOf<RemoteEntityPayload>()
            for (doc in returnedDocs) {
                val entityId = doc.id
                if (entityId.isBlank()) continue

                val docData = doc.data
                if (docData == null) {
                    println("IronMindLifecycle [Sync] [TIER3_SKIP_NULL_DOC] docId=$entityId")
                    continue
                }

                val hasUpdatedAt = docData.containsKey("updatedAt") && docData["updatedAt"] != null

                val finalPayloadMap: Map<String, Any?> = if (hasUpdatedAt) {
                    docData
                } else {
                    // Document missing updatedAt: transactional backfill per .6.4 and .6.5
                    val backfilled = firestore.runTransaction { tx ->
                        val snap = tx.get(doc.reference)
                        if (!snap.exists()) return@runTransaction null
                        val currentData = snap.data ?: emptyMap<String, Any?>()
                        val existingUpdatedAt = (currentData["updatedAt"] as? Number)?.toLong()
                        if (existingUpdatedAt != null) {
                            return@runTransaction currentData
                        }

                        val isDeleted = (currentData["isDeleted"] as? Boolean) ?: false
                        val currentVersion = (currentData["version"] as? Number)?.toLong()
                        val targetVersion = currentVersion ?: 1L
                        val now = System.currentTimeMillis()

                        val updates = mutableMapOf<String, Any>(
                            "updatedAt" to now,
                            "version" to targetVersion
                        )
                        if (isDeleted) {
                            updates["isDeleted"] = true
                            updates["deletedAt"] = (currentData["deletedAt"] as? Number)?.toLong() ?: now
                        }

                        tx.update(doc.reference, updates)

                        val updatedData = currentData.toMutableMap()
                        updatedData["updatedAt"] = now
                        updatedData["version"] = targetVersion
                        if (isDeleted) {
                            updatedData["isDeleted"] = true
                            updatedData["deletedAt"] = (currentData["deletedAt"] as? Number)?.toLong() ?: now
                        }
                        println("IronMindLifecycle [Sync] [TIER3_LEGACY_GRADUATED] entityType=$entityType docId=$entityId targetVersion=$targetVersion")
                        updatedData
                    }.await()

                    backfilled ?: continue
                }

                val updatedAt = (finalPayloadMap["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val version = (finalPayloadMap["version"] as? Number)?.toLong() ?: 1L
                val isDeleted = (finalPayloadMap["isDeleted"] as? Boolean) ?: false
                val rawJson = JSONObject(finalPayloadMap).toString()

                items.add(
                    RemoteEntityPayload(
                        entityType = entityType,
                        entityId = entityId,
                        version = version,
                        updatedAt = updatedAt,
                        isDeleted = isDeleted,
                        payloadJson = rawJson
                    )
                )
            }

            Result.Success(SyncPullResult.Success(items = items, hasMore = hasMore))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [Sync] [TIER3_PULL_FAILED] entityType=$entityType userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }

    internal fun collectionPathFor(entityType: String): String? = when (entityType) {
        "Goal" -> "goals"
        "Plan" -> "plans"
        "Task" -> "tasks"
        "Commitment" -> "commitments"
        "Reflection" -> "reflections"
        "Memory" -> "memories"
        else -> null
    }

    internal fun parsePayload(payload: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val json = JSONObject(payload)
        for (key in json.keys()) {
            result[key] = if (json.isNull(key)) null else json.get(key)
        }
        return result
    }
}
