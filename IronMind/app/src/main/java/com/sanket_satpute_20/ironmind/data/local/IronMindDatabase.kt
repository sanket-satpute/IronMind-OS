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
        DecisionRecordEntity::class,
        NotificationRecordEntity::class,
        com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecordEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun ironMindDao(): IronMindDao
    abstract fun outboxDao(): OutboxDao
    abstract fun observationDao(): ObservationDao
    abstract fun patternDao(): com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
    abstract fun autonomySettingsDao(): AutonomySettingsDao
    abstract fun decisionRecordDao(): DecisionRecordDao
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun interventionDao(): com.sanket_satpute_20.ironmind.data.local.dao.InterventionDao

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
    }
}
