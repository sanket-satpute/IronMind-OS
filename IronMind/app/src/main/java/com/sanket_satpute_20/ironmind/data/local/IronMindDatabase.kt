package com.sanket_satpute_20.ironmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
        EventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun ironMindDao(): IronMindDao
}
