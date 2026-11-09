package com.sanket_satpute_20.ironmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.ObservationDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.AutonomySettingsDao
import com.sanket_satpute_20.ironmind.data.local.dao.DecisionRecordDao
import com.sanket_satpute_20.ironmind.data.local.dao.NotificationRecordDao
import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.dao.LocalTombstoneDao
import com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.entity.*

@Database(
    entities = [
        UserProfileEntity::class,
        GoalEntity::class,
        PlanEntity::class,
        TaskEntity::class,
        CommitmentEntity::class,
        OutcomeEntity::class,
        ReflectionEntity::class,
        ProtectionRuleEntity::class,
        ProtectionSessionEntity::class,
        EventEntity::class,
        MemoryEntity::class,
        OutboxEntity::class,
        ObservationEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity::class,
        AutonomySettingsEntity::class,
        GlobalAutonomyStateEntity::class,
        AppUsageObservationSettingsEntity::class,
        DecisionRecordEntity::class,
        NotificationRecordEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecordEntity::class,
        NotificationObservationSettingsEntity::class,
        CalendarObservationSettingsEntity::class,
        LocationObservationSettingsEntity::class,
        ActivityObservationSettingsEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.ExperimentRecordEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.CorrectionReviewEntity::class,
        SyncConflictEntity::class,
        AppSessionStateEntity::class,
        SyncWorkerLeaseEntity::class,
        SyncCheckpointEntity::class,
        LocalTombstoneEntity::class,
        AppUsageObservationCheckpointEntity::class
    ],
    version = 30,
    exportSchema = false
)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun ironMindDao(): IronMindDao
    abstract fun outboxDao(): OutboxDao
    abstract fun observationDao(): ObservationDao
    abstract fun patternDao(): com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
    abstract fun autonomySettingsDao(): AutonomySettingsDao
    abstract fun globalAutonomyStateDao(): com.sanket_satpute_20.ironmind.data.local.dao.GlobalAutonomyStateDao
    abstract fun appUsageObservationSettingsDao(): com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationSettingsDao
    abstract fun decisionRecordDao(): DecisionRecordDao
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun interventionDao(): com.sanket_satpute_20.ironmind.data.local.dao.InterventionDao
    abstract fun notificationObservationSettingsDao(): com.sanket_satpute_20.ironmind.data.local.dao.NotificationObservationSettingsDao
    abstract fun calendarObservationSettingsDao(): com.sanket_satpute_20.ironmind.data.local.dao.CalendarObservationSettingsDao
    abstract fun locationObservationSettingsDao(): com.sanket_satpute_20.ironmind.data.local.dao.LocationObservationSettingsDao
    abstract fun activityObservationSettingsDao(): com.sanket_satpute_20.ironmind.data.local.dao.ActivityObservationSettingsDao
    abstract fun experimentDao(): com.sanket_satpute_20.ironmind.data.local.dao.ExperimentDao
    abstract fun barrierDao(): BarrierDao
    abstract fun interventionRecommendationDao(): com.sanket_satpute_20.ironmind.data.local.dao.InterventionRecommendationDao
    abstract fun learnerEventStatusDao(): com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
    abstract fun correctionReviewDao(): com.sanket_satpute_20.ironmind.data.local.dao.CorrectionReviewDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun appSessionStateDao(): AppSessionStateDao
    abstract fun syncWorkerLeaseDao(): SyncWorkerLeaseDao
    abstract fun syncCheckpointDao(): SyncCheckpointDao
    abstract fun localTombstoneDao(): LocalTombstoneDao
    abstract fun appUsageObservationCheckpointDao(): AppUsageObservationCheckpointDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `evidenceCount` INTEGER NOT NULL,
                        `firstObservedAt` INTEGER NOT NULL,
                        `lastObservedAt` INTEGER NOT NULL,
                        `confirmationState` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `expiresAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=2")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `outbox` (
                        `operationId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`operationId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=3")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `observations` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        `subjectId` TEXT,
                        `value` TEXT NOT NULL,
                        `context` TEXT NOT NULL,
                        `confidence` REAL,
                        `provenanceSource` TEXT NOT NULL,
                        `provenanceSourceReference` TEXT,
                        `provenanceCapturedAt` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=4")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `patterns` (
                        `id` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `conditions` TEXT, 
                        `predictedBehavior` TEXT, 
                        `confidence` REAL NOT NULL, 
                        `evidenceCount` INTEGER NOT NULL, 
                        `evidenceReferences` TEXT, 
                        `firstObservedAt` INTEGER NOT NULL, 
                        `lastObservedAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `confirmationState` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=5")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `autonomy_settings` (
                        `userId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `level` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `capability`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=6")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decision_records` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `capability` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `trigger` TEXT,
                        `source` TEXT,
                        `contextSummary` TEXT,
                        `policy` TEXT,
                        `autonomyLevel` TEXT NOT NULL,
                        `reasoning` TEXT,
                        `confidence` REAL,
                        `result` TEXT NOT NULL,
                        `failureReason` TEXT,
                        `userResponse` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=7")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_records` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `deduplicationKey` TEXT,
                        `deliveryStatus` TEXT NOT NULL,
                        `suppressionReason` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=8")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intervention_records` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `resolutionReason` TEXT,
                        `contextData` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=9")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `global_autonomy_state` (
                        `userId` TEXT NOT NULL,
                        `isGlobalPauseActive` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=10")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_usage_observation_settings` (
                        `userId` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=11")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_observation_settings` (
                        `userId` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=12")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_observation_settings` (
                        `userId` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=13")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `location_observation_settings` (
                        `userId` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=14")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_observation_settings` (
                        `userId` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `lastUpdatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=15")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `intervention_records` ADD COLUMN `channel` TEXT NOT NULL DEFAULT 'IN_APP'
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=16")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `experiment_records` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `hypothesis` TEXT NOT NULL,
                        `activeVariation` TEXT NOT NULL,
                        `controlVariation` TEXT NOT NULL,
                        `targetMetric` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `outcomeSummary` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=17")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add column
                db.execSQL("ALTER TABLE `patterns` ADD COLUMN `fingerprint` TEXT NOT NULL DEFAULT ''")
                
                // Backfill deterministic fingerprint
                db.execSQL("UPDATE `patterns` SET `fingerprint` = `userId` || '_' || `type` || '_' || lower(trim(`description`))")
                
                // Add unique index
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_patterns_fingerprint` ON `patterns` (`fingerprint`)")
                
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=18")
            }
        }
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `barrier_hypotheses` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `confirmationState` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `sourceReflectionId` TEXT NOT NULL,
                        `firstObservedAt` INTEGER NOT NULL,
                        `lastObservedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=19")
            }
        }
        
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intervention_recommendations` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `interventionType` TEXT NOT NULL,
                        `targetEntityId` TEXT,
                        `targetEntityType` TEXT,
                        `rationale` TEXT NOT NULL,
                        `suggestedAction` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=20")
            }
        }
        
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `intervention_recommendations` ADD COLUMN `objective` TEXT
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=21")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learner_event_status` (
                        `eventId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `claimCount` INTEGER NOT NULL,
                        `failureCount` INTEGER NOT NULL,
                        `maxAttempts` INTEGER NOT NULL,
                        `leaseToken` TEXT,
                        `leaseExpiresAt` INTEGER,
                        `lastAttemptAt` INTEGER,
                        `nextAttemptAt` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `hadMalformedMetadata` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learner_event_status_poll` ON `learner_event_status` (`status`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learner_event_status_lease` ON `learner_event_status` (`status`, `leaseExpiresAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learner_event_status_user` ON `learner_event_status` (`userId`)")
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=22")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `intervention_recommendations` ADD COLUMN `causalHypothesisId` TEXT
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE `intervention_recommendations` ADD COLUMN `causalHypothesisType` TEXT
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=23")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `correction_reviews` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `sourceEventId` TEXT NOT NULL,
                        `recommendationId` TEXT NOT NULL,
                        `causalHypothesisId` TEXT NOT NULL,
                        `causalHypothesisType` TEXT NOT NULL,
                        `correctionScope` TEXT NOT NULL,
                        `correctedText` TEXT NOT NULL,
                        `decisionState` TEXT NOT NULL,
                        `reviewStatus` TEXT NOT NULL,
                        `reviewFeedback` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `reviewedAt` INTEGER,
                        `schemaVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_correction_reviews_user_event` ON `correction_reviews` (`userId`, `sourceEventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_correction_reviews_user_status` ON `correction_reviews` (`userId`, `reviewStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_correction_reviews_user_created` ON `correction_reviews` (`userId`, `createdAt`)")
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=24")
            }
        }
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter outbox table to add nullable userId column
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `userId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_userId` ON `outbox` (`userId`)")

                // 2. Backfill existing outbox records by parsing valid non-blank userId from payload
                val cursor = db.query("SELECT `operationId`, `payload` FROM `outbox`")
                cursor.use {
                    while (it.moveToNext()) {
                        val opId = it.getString(0)
                        val payload = it.getString(1)
                        val extractedUserId = try {
                            val json = org.json.JSONObject(payload)
                            if (json.has("userId") && !json.isNull("userId")) {
                                json.optString("userId").trim().takeIf { uid -> uid.isNotEmpty() }
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                        if (extractedUserId != null) {
                            db.execSQL("UPDATE `outbox` SET `userId` = ? WHERE `operationId` = ?", arrayOf(extractedUserId, opId))
                        }
                    }
                }

                // 3. Create sync_conflicts table and indices
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                        `conflictId` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `localPayload` TEXT NOT NULL,
                        `remotePayload` TEXT,
                        `conflictReason` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `resolutionStatus` TEXT NOT NULL,
                        `resolvedAt` INTEGER,
                        `resolvedBy` TEXT,
                        PRIMARY KEY(`conflictId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_userId` ON `sync_conflicts` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_resolutionStatus` ON `sync_conflicts` (`resolutionStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_createdAt` ON `sync_conflicts` (`createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflicts_operationId` ON `sync_conflicts` (`operationId`)")
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=25")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add distributed sync versioning and lease fencing columns to outbox
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `baseVersion` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `leaseToken` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `leaseExpiresAt` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_leaseToken` ON `outbox` (`leaseToken`)")
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=26")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_session_state` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `activeUserId` TEXT NOT NULL,
                        `currentGeneration` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_worker_leases` (
                        `leaseKey` TEXT PRIMARY KEY NOT NULL,
                        `ownerToken` TEXT NOT NULL,
                        `sessionGeneration` INTEGER NOT NULL,
                        `generationId` INTEGER NOT NULL,
                        `cursor` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `acquiredAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastCompletedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_worker_leases_sessionGeneration` ON `sync_worker_leases` (`sessionGeneration`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_worker_leases_expiresAt` ON `sync_worker_leases` (`expiresAt`)")
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=27")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val migrationTimestamp = System.currentTimeMillis()

                // 1. Authoritative sync_checkpoints Table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_checkpoints` (
                        `userId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `traversalType` TEXT NOT NULL,
                        `lastTimestamp` INTEGER NOT NULL,
                        `lastDocumentId` TEXT NOT NULL,
                        `cycleCount` INTEGER NOT NULL DEFAULT 0,
                        `lastCycleCompletedAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY (`userId`, `entityType`, `traversalType`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_checkpoints_userId` ON `sync_checkpoints` (`userId`)")

                // 2. Durable local_tombstones Table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_tombstones` (
                        `userId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `deletionOperationId` TEXT,
                        `baseServerVersion` INTEGER,
                        `canonicalServerVersion` INTEGER,
                        `localRevision` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY (`userId`, `entityType`, `entityId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_tombstones_userId` ON `local_tombstones` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_tombstones_deletedAt` ON `local_tombstones` (`deletedAt`)")

                // 3. Domain Entity Columns
                val domainTables = listOf("goal", "plan", "task", "commitment", "reflection", "memory")
                for (table in domainTables) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `localRevision` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `serverVersion` INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `legacyBaseHash` TEXT DEFAULT NULL")
                }

                // 4. Outbox Extensions
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `baseServerVersion` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `uploadedLocalRevision` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `chainedFrom` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `legacyBaseHash` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `leaseGeneration` INTEGER DEFAULT NULL")

                // 5. Clean Reset of Stalled Legacy SYNCING Leases (Executable Long String Interpolation)
                db.execSQL(
                    """
                    UPDATE `outbox`
                    SET `status` = 'PENDING',
                        `leaseToken` = NULL,
                        `leaseExpiresAt` = NULL,
                        `leaseGeneration` = NULL
                    WHERE `status` = 'SYNCING'
                      AND (`leaseGeneration` IS NULL OR `leaseExpiresAt` IS NULL OR `leaseExpiresAt` <= $migrationTimestamp)
                    """.trimIndent()
                )

                // 6. Conflict Deduplication & Partial Unique Index
                db.execSQL(
                    """
                    UPDATE sync_conflicts
                    SET resolutionStatus = 'SUPERSEDED',
                        updatedAt = $migrationTimestamp
                    WHERE resolutionStatus = 'UNRESOLVED'
                      AND conflictId NOT IN (
                          SELECT c1.conflictId
                          FROM sync_conflicts c1
                          WHERE c1.resolutionStatus = 'UNRESOLVED'
                            AND NOT EXISTS (
                                SELECT 1
                                FROM sync_conflicts c2
                                WHERE c2.resolutionStatus = 'UNRESOLVED'
                                  AND c2.userId = c1.userId
                                  AND c2.entityType = c1.entityType
                                  AND c2.entityId = c1.entityId
                                  AND (
                                      c2.createdAt > c1.createdAt 
                                      OR (c2.createdAt = c1.createdAt AND c2.conflictId > c1.conflictId)
                                  )
                            )
                      )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflicts_unresolved` ON `sync_conflicts` (`userId`, `entityType`, `entityId`) WHERE `resolutionStatus` = 'UNRESOLVED'")

                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=28")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_usage_observation_checkpoints` (
                        `userId` TEXT NOT NULL,
                        `lastCollectedIntervalEndMs` INTEGER NOT NULL,
                        `activePackageName` TEXT,
                        `activeSessionStartTimeMs` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=29")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_observations_userId_type_occurredAt` ON `observations` (`userId`, `type`, `occurredAt`)"
                )
                println("IronMindLifecycle [Database] [MIGRATION_COMPLETED] version=30")
            }
        }
    }
}
