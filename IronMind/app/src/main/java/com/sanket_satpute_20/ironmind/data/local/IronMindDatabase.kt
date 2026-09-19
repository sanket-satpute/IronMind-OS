package com.sanket_satpute_20.ironmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
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
        MemoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun ironMindDao(): IronMindDao

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
    }
}
