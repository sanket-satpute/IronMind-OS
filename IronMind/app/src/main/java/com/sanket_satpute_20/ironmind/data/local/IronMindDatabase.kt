package com.sanket_satpute_20.ironmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.ObservationDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
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
        ObservationEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun ironMindDao(): IronMindDao
    abstract fun outboxDao(): OutboxDao
    abstract fun observationDao(): ObservationDao

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
    }
}
