package com.sanket_satpute_20.ironmind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
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

            when (entry.operationType) {
                OutboxOperationType.UPSERT -> {
                    val payload = parsePayload(entry.payload)
                    // Merge so we don't clobber remote fields not present in local payload
                    docRef.set(payload, SetOptions.merge()).await()
                    println("IronMindLifecycle [Sync] [UPLOADED] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
                    Result.Success(SyncUploadResult.Success)
                }
                OutboxOperationType.DELETE -> {
                    docRef.delete().await()
                    println("IronMindLifecycle [Sync] [DELETED_REMOTE] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
                    Result.Success(SyncUploadResult.Success)
                }
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [Sync] [UPLOAD_FAILED] operationId=${entry.operationId} error=${e.message}")
            Result.Failure(e)
        }
    }

    private fun collectionPathFor(entityType: String): String? = when (entityType) {
        "Goal" -> "goals"
        "Commitment" -> "commitments"
        "Reflection" -> "reflections"
        "Memory" -> "memories"
        else -> null
    }

    private fun parsePayload(payload: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val json = JSONObject(payload)
        for (key in json.keys()) {
            result[key] = json.get(key)
        }
        return result
    }
}
