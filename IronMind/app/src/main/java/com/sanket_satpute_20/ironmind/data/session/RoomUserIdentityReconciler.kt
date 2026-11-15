package com.sanket_satpute_20.ironmind.data.session

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.PatternFingerprintCollisionException
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Concrete implementation of [UserIdentityReconciler] that executes atomic
 * reconciliation of all 26 user-partitioned Room tables and pending outbox payloads.
 *
 * Safety & Invariants:
 * - Executes in a single SQLite transaction using [IronMindDatabase.withTransaction].
 * - Any failure rolls back all database modifications completely.
 * - Idempotent: repeated calls produce zero side-effects once legacy data is migrated.
 * - Collision-aware: merges settings and profiles using documented conflict policies.
 * - Guarded uniqueness: detects and prevents global pattern fingerprint collisions without data loss.
 * - Sanitizes queued outbox JSON payloads by updating only the root-level userId property.
 */
class RoomUserIdentityReconciler(
    private val database: IronMindDatabase
) : UserIdentityReconciler {

    companion object {
        /** Tables whose identity is directly rewritten or merged during reconciliation. */
        internal val MIGRATED_SINGLE_USER_TABLES = setOf(
            "user_profile",
            "global_autonomy_state",
            "autonomy_settings",
            "app_usage_observation_settings",
            "notification_observation_settings",
            "calendar_observation_settings",
            "location_observation_settings",
            "activity_observation_settings",
            "app_usage_observation_checkpoints"
        )

        internal val MIGRATED_DOMAIN_TABLES = listOf(
            "goal",
            "plan",
            "task",
            "commitment",
            "outcome",
            "reflection",
            "protection_rules",
            "protection_sessions",
            "events",
            "memory",
            "observations",
            "intervention_records",
            "experiment_records",
            "barrier_hypotheses",
            "intervention_recommendations",
            "learner_event_status",
            "correction_reviews",
            "sync_conflicts"
        )

        internal val MIGRATED_SYNC_TABLES = listOf(
            "sync_checkpoints",
            "local_tombstones"
        )

        internal val MIGRATED_USER_SCOPED_TABLES =
            MIGRATED_SINGLE_USER_TABLES + MIGRATED_DOMAIN_TABLES + MIGRATED_SYNC_TABLES + "patterns"
    }

    override suspend fun reconcile(
        legacyUserId: String,
        authenticatedUserId: String
    ): Result<ReconciliationResult, Exception> = withContext(Dispatchers.IO) {
        if (legacyUserId.isBlank() || authenticatedUserId.isBlank() || legacyUserId == authenticatedUserId) {
            return@withContext Result.Success(
                ReconciliationResult(
                    isReconciled = true,
                    recordsUpdated = 0,
                    message = "No reconciliation required: legacy and target IDs are identical or blank."
                )
            )
        }

        try {
            var totalUpdated = 0

            database.withTransaction {
                val sdb = database.openHelper.writableDatabase

                fun executeUpdate(sql: String, bindArgs: Array<out Any?>): Int {
                    val stmt = sdb.compileStatement(sql)
                    try {
                        bindArgs.forEachIndexed { index, arg ->
                            when (arg) {
                                null -> stmt.bindNull(index + 1)
                                is String -> stmt.bindString(index + 1, arg)
                                is Long -> stmt.bindLong(index + 1, arg)
                                is Int -> stmt.bindLong(index + 1, arg.toLong())
                                is Double -> stmt.bindDouble(index + 1, arg)
                                is ByteArray -> stmt.bindBlob(index + 1, arg)
                                else -> stmt.bindString(index + 1, arg.toString())
                            }
                        }
                        val count = stmt.executeUpdateDelete()
                        totalUpdated += count
                        return count
                    } finally {
                        stmt.close()
                    }
                }

                fun queryCount(sql: String, bindArgs: Array<String>): Long {
                    val cursor = sdb.query(sql, bindArgs)
                    return cursor.use {
                        if (it.moveToFirst()) it.getLong(0) else 0L
                    }
                }

                val now = System.currentTimeMillis()

                // =========================================================================
                // 1. CATEGORY 1: Settings & Profile (Collision-Aware Merge Rules)
                // =========================================================================

                // Table 1: user_profile (@PrimaryKey id: String)
                // Schema: id, createdAt, updatedAt, displayName, timezone, createdFrom, status, schemaVersion
                val profileCountNew = queryCount("SELECT COUNT(*) FROM user_profile WHERE id = ?", arrayOf(authenticatedUserId))
                val profileCountLegacy = queryCount("SELECT COUNT(*) FROM user_profile WHERE id = ?", arrayOf(legacyUserId))
                if (profileCountLegacy > 0L) {
                    if (profileCountNew == 0L) {
                        executeUpdate("UPDATE user_profile SET id = ? WHERE id = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE user_profile
                            SET displayName = CASE WHEN displayName = '' OR displayName IS NULL THEN (SELECT displayName FROM user_profile WHERE id = ?) ELSE displayName END,
                                timezone = CASE WHEN timezone = '' OR timezone IS NULL THEN (SELECT timezone FROM user_profile WHERE id = ?) ELSE timezone END,
                                updatedAt = ?
                            WHERE id = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM user_profile WHERE id = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 2: global_autonomy_state (@PrimaryKey userId: String)
                // Schema: userId, isGlobalPauseActive (Boolean), updatedAt (Long)
                val stateCountNew = queryCount("SELECT COUNT(*) FROM global_autonomy_state WHERE userId = ?", arrayOf(authenticatedUserId))
                val stateCountLegacy = queryCount("SELECT COUNT(*) FROM global_autonomy_state WHERE userId = ?", arrayOf(legacyUserId))
                if (stateCountLegacy > 0L) {
                    if (stateCountNew == 0L) {
                        executeUpdate("UPDATE global_autonomy_state SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        // Merge: if legacy had pause active, preserve that in target
                        executeUpdate(
                            """
                            UPDATE global_autonomy_state
                            SET isGlobalPauseActive = CASE WHEN (SELECT isGlobalPauseActive FROM global_autonomy_state WHERE userId = ?) = 1 THEN 1 ELSE isGlobalPauseActive END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM global_autonomy_state WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 3: autonomy_settings (primaryKeys = ["userId", "capability"])
                // Schema: userId, capability, level, updatedAt
                if (queryCount("SELECT COUNT(*) FROM autonomy_settings WHERE userId = ?", arrayOf(legacyUserId)) > 0L) {
                    executeUpdate(
                        """
                        UPDATE autonomy_settings
                        SET userId = ?
                        WHERE userId = ?
                          AND capability NOT IN (SELECT capability FROM autonomy_settings WHERE userId = ?)
                        """.trimIndent(),
                        arrayOf(authenticatedUserId, legacyUserId, authenticatedUserId)
                    )
                    executeUpdate("DELETE FROM autonomy_settings WHERE userId = ?", arrayOf(legacyUserId))
                }

                // Table 4: app_usage_observation_settings (@PrimaryKey userId: String)
                // Schema: userId, isEnabled (Boolean), updatedAt (Long)
                val appUsageCountNew = queryCount("SELECT COUNT(*) FROM app_usage_observation_settings WHERE userId = ?", arrayOf(authenticatedUserId))
                val appUsageCountLegacy = queryCount("SELECT COUNT(*) FROM app_usage_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                if (appUsageCountLegacy > 0L) {
                    if (appUsageCountNew == 0L) {
                        executeUpdate("UPDATE app_usage_observation_settings SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE app_usage_observation_settings
                            SET isEnabled = CASE WHEN (SELECT isEnabled FROM app_usage_observation_settings WHERE userId = ?) = 1 THEN 1 ELSE isEnabled END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM app_usage_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 5: notification_observation_settings (@PrimaryKey userId: String)
                // Schema: userId, isEnabled (Boolean), updatedAt (Long)
                val notifCountNew = queryCount("SELECT COUNT(*) FROM notification_observation_settings WHERE userId = ?", arrayOf(authenticatedUserId))
                val notifCountLegacy = queryCount("SELECT COUNT(*) FROM notification_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                if (notifCountLegacy > 0L) {
                    if (notifCountNew == 0L) {
                        executeUpdate("UPDATE notification_observation_settings SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE notification_observation_settings
                            SET isEnabled = CASE WHEN (SELECT isEnabled FROM notification_observation_settings WHERE userId = ?) = 1 THEN 1 ELSE isEnabled END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM notification_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 6: calendar_observation_settings (@PrimaryKey userId: String)
                // Schema: userId, isEnabled (Boolean), updatedAt (Long)
                val calCountNew = queryCount("SELECT COUNT(*) FROM calendar_observation_settings WHERE userId = ?", arrayOf(authenticatedUserId))
                val calCountLegacy = queryCount("SELECT COUNT(*) FROM calendar_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                if (calCountLegacy > 0L) {
                    if (calCountNew == 0L) {
                        executeUpdate("UPDATE calendar_observation_settings SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE calendar_observation_settings
                            SET isEnabled = CASE WHEN (SELECT isEnabled FROM calendar_observation_settings WHERE userId = ?) = 1 THEN 1 ELSE isEnabled END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM calendar_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 7: location_observation_settings (@PrimaryKey userId: String)
                // Schema: userId, isEnabled (Boolean), updatedAt (Long)
                val locCountNew = queryCount("SELECT COUNT(*) FROM location_observation_settings WHERE userId = ?", arrayOf(authenticatedUserId))
                val locCountLegacy = queryCount("SELECT COUNT(*) FROM location_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                if (locCountLegacy > 0L) {
                    if (locCountNew == 0L) {
                        executeUpdate("UPDATE location_observation_settings SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE location_observation_settings
                            SET isEnabled = CASE WHEN (SELECT isEnabled FROM location_observation_settings WHERE userId = ?) = 1 THEN 1 ELSE isEnabled END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM location_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 8: activity_observation_settings (@PrimaryKey userId: String)
                // Schema: userId, isEnabled (Boolean), lastUpdatedAt (Long)
                val actCountNew = queryCount("SELECT COUNT(*) FROM activity_observation_settings WHERE userId = ?", arrayOf(authenticatedUserId))
                val actCountLegacy = queryCount("SELECT COUNT(*) FROM activity_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                if (actCountLegacy > 0L) {
                    if (actCountNew == 0L) {
                        executeUpdate("UPDATE activity_observation_settings SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE activity_observation_settings
                            SET isEnabled = CASE WHEN (SELECT isEnabled FROM activity_observation_settings WHERE userId = ?) = 1 THEN 1 ELSE isEnabled END,
                                lastUpdatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM activity_observation_settings WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // Table 9: app_usage_observation_checkpoints (@PrimaryKey userId: String)
                // Schema: userId, lastCollectedIntervalEndMs, activePackageName, activeSessionStartTimeMs, updatedAt, schemaVersion
                val usageCpCountNew = queryCount("SELECT COUNT(*) FROM app_usage_observation_checkpoints WHERE userId = ?", arrayOf(authenticatedUserId))
                val usageCpCountLegacy = queryCount("SELECT COUNT(*) FROM app_usage_observation_checkpoints WHERE userId = ?", arrayOf(legacyUserId))
                if (usageCpCountLegacy > 0L) {
                    if (usageCpCountNew == 0L) {
                        executeUpdate("UPDATE app_usage_observation_checkpoints SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                    } else {
                        executeUpdate(
                            """
                            UPDATE app_usage_observation_checkpoints
                            SET lastCollectedIntervalEndMs = CASE
                                WHEN (SELECT lastCollectedIntervalEndMs FROM app_usage_observation_checkpoints WHERE userId = ?) > lastCollectedIntervalEndMs
                                THEN (SELECT lastCollectedIntervalEndMs FROM app_usage_observation_checkpoints WHERE userId = ?)
                                ELSE lastCollectedIntervalEndMs END,
                                updatedAt = ?
                            WHERE userId = ?
                            """.trimIndent(),
                            arrayOf<Any?>(legacyUserId, legacyUserId, now, authenticatedUserId)
                        )
                        executeUpdate("DELETE FROM app_usage_observation_checkpoints WHERE userId = ?", arrayOf(legacyUserId))
                    }
                }

                // =========================================================================
                // 2. CATEGORY 2: Standard Foreign Column Multi-Row Domain Tables
                // =========================================================================
                // NOTE: Table "patterns" is handled separately below due to UNIQUE(fingerprint).
                // NOTE: The events table is named "events" (not "event") per EventEntity.
                for (table in MIGRATED_DOMAIN_TABLES) {
                    executeUpdate("UPDATE $table SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))
                }

                // =========================================================================
                // 2B. CATEGORY 2B: Sync State & Checkpoints (Collision-Safe Multi-Row)
                // =========================================================================
                if (queryCount("SELECT COUNT(*) FROM sync_checkpoints WHERE userId = ?", arrayOf(legacyUserId)) > 0L) {
                    executeUpdate(
                        """
                        UPDATE sync_checkpoints
                        SET userId = ?
                        WHERE userId = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM sync_checkpoints cp2
                              WHERE cp2.userId = ?
                                AND cp2.entityType = sync_checkpoints.entityType
                                AND cp2.traversalType = sync_checkpoints.traversalType
                          )
                        """.trimIndent(),
                        arrayOf(authenticatedUserId, legacyUserId, authenticatedUserId)
                    )
                    executeUpdate("DELETE FROM sync_checkpoints WHERE userId = ?", arrayOf(legacyUserId))
                }

                if (queryCount("SELECT COUNT(*) FROM local_tombstones WHERE userId = ?", arrayOf(legacyUserId)) > 0L) {
                    executeUpdate(
                        """
                        UPDATE local_tombstones
                        SET userId = ?
                        WHERE userId = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM local_tombstones lt2
                              WHERE lt2.userId = ?
                                AND lt2.entityType = local_tombstones.entityType
                                AND lt2.entityId = local_tombstones.entityId
                          )
                        """.trimIndent(),
                        arrayOf(authenticatedUserId, legacyUserId, authenticatedUserId)
                    )
                    executeUpdate("DELETE FROM local_tombstones WHERE userId = ?", arrayOf(legacyUserId))
                }

                // =========================================================================
                // 3. CATEGORY 3: Table "patterns" (Guarded against UNIQUE(fingerprint) Collisions)
                // =========================================================================
                val collisionCursor = sdb.query(
                    """
                    SELECT p_legacy.fingerprint
                    FROM patterns p_legacy
                    INNER JOIN patterns p_target ON p_legacy.fingerprint = p_target.fingerprint
                    WHERE p_legacy.userId = ? AND p_target.userId = ?
                    """.trimIndent(),
                    arrayOf(legacyUserId, authenticatedUserId)
                )
                val collidingFingerprints = mutableListOf<String>()
                collisionCursor.use {
                    while (it.moveToNext()) {
                        collidingFingerprints.add(it.getString(0))
                    }
                }

                if (collidingFingerprints.isNotEmpty()) {
                    throw PatternFingerprintCollisionException(
                        "Pattern fingerprint collision detected during identity reconciliation for fingerprints: $collidingFingerprints. Transaction rolled back to preserve both legacy and target records.",
                        collidingFingerprints
                    )
                }

                // Collision-free: safely migrate patterns
                executeUpdate("UPDATE patterns SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))

                // =========================================================================
                // 4. OUTBOX SANITIZATION
                // =========================================================================
                // Update outbox userId column for legacy user
                executeUpdate("UPDATE outbox SET userId = ? WHERE userId = ?", arrayOf(authenticatedUserId, legacyUserId))

                val outboxCursor = sdb.query(
                    "SELECT operationId, payload FROM outbox WHERE payload LIKE '%' || ? || '%'",
                    arrayOf(legacyUserId)
                )
                val payloadsToUpdate = mutableListOf<Pair<String, String>>()
                outboxCursor.use {
                    while (it.moveToNext()) {
                        val opId = it.getString(0)
                        val rawPayload = it.getString(1)
                        try {
                            val json = JSONObject(rawPayload)
                            if (json.has("userId") && json.optString("userId") == legacyUserId) {
                                json.put("userId", authenticatedUserId)
                                payloadsToUpdate.add(opId to json.toString())
                            }
                        } catch (e: Exception) {
                            println("RoomUserIdentityReconciler [WARN]: Preserving malformed outbox payload for operationId=$opId: ${e.message}")
                        }
                    }
                }

                for ((opId, updatedPayload) in payloadsToUpdate) {
                    executeUpdate("UPDATE outbox SET payload = ?, userId = ? WHERE operationId = ?", arrayOf(updatedPayload, authenticatedUserId, opId))
                }
            }

            println("RoomUserIdentityReconciler [SUCCESS]: Reconciled $totalUpdated records from '$legacyUserId' to '$authenticatedUserId'.")
            Result.Success(
                ReconciliationResult(
                    isReconciled = true,
                    recordsUpdated = totalUpdated,
                    message = "Successfully reconciled $totalUpdated records from '$legacyUserId' to '$authenticatedUserId'."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("RoomUserIdentityReconciler [ERROR]: Failed to reconcile records: ${e.message}")
            Result.Failure(e)
        }
    }
}
