package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.*

@Dao
interface IronMindDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun getUserProfile(id: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getLocalUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGoal(goal: GoalEntity)

    @Query("SELECT * FROM goal WHERE id = :id LIMIT 1")
    fun getGoal(id: String): GoalEntity?

    @Query("SELECT * FROM goal WHERE userId = :userId ORDER BY createdAt DESC")
    fun getGoalsForUser(userId: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlan(plan: PlanEntity)

    @Query("SELECT * FROM plan WHERE id = :id LIMIT 1")
    fun getPlan(id: String): PlanEntity?

    @Query("SELECT * FROM plan WHERE goalId = :goalId ORDER BY createdAt DESC")
    fun getPlansForGoal(goalId: String): List<PlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTask(task: TaskEntity)

    @Query("SELECT * FROM task WHERE id = :id LIMIT 1")
    fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM task WHERE planId = :planId ORDER BY createdAt ASC")
    fun getTasksForPlan(planId: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE goalId = :goalId ORDER BY createdAt ASC")
    fun getTasksForGoal(goalId: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCommitment(commitment: CommitmentEntity)

    @Query("SELECT * FROM commitment WHERE id = :id LIMIT 1")
    fun getCommitment(id: String): CommitmentEntity?

    @Query("SELECT * FROM commitment WHERE userId = :userId ORDER BY createdAt DESC")
    fun getCommitmentsForUser(userId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE userId = :userId AND status IN (:statuses) ORDER BY priority DESC, createdAt ASC")
    fun getActiveCommitmentsForUser(userId: String, statuses: List<String>): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE goalId = :goalId ORDER BY createdAt DESC")
    fun getCommitmentsForGoal(goalId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE userId = :userId AND ((createdAt >= :startTime AND createdAt <= :endTime) OR (committedAt >= :startTime AND committedAt <= :endTime) OR (completedAt >= :startTime AND completedAt <= :endTime)) ORDER BY createdAt DESC")
    fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE planId = :planId ORDER BY createdAt ASC")
    fun getCommitmentsForPlan(planId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun getCommitmentsForTask(taskId: String): List<CommitmentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOutcome(outcome: OutcomeEntity)

    @Query("SELECT * FROM outcome WHERE id = :id LIMIT 1")
    fun getOutcome(id: String): OutcomeEntity?

    @Query("SELECT * FROM outcome WHERE sourceEntityId = :sourceEntityId LIMIT 1")
    fun getOutcomeForSource(sourceEntityId: String): OutcomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReflection(reflection: ReflectionEntity)

    @Query("SELECT * FROM reflection WHERE id = :id LIMIT 1")
    fun getReflection(id: String): ReflectionEntity?

    @Query("SELECT * FROM reflection WHERE userId = :userId AND createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt DESC")
    fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): List<ReflectionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProtectionRule(rule: ProtectionRuleEntity)

    @Query("SELECT * FROM protection_rules WHERE id = :id LIMIT 1")
    fun getProtectionRule(id: String): ProtectionRuleEntity?

    @Query("SELECT * FROM protection_rules WHERE userId = :userId ORDER BY priority DESC")
    fun getProtectionRulesForUser(userId: String): List<ProtectionRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProtectionSession(session: ProtectionSessionEntity)

    @Query("SELECT * FROM protection_sessions WHERE id = :id LIMIT 1")
    fun getProtectionSession(id: String): ProtectionSessionEntity?

    @Query("SELECT * FROM protection_sessions WHERE userId = :userId AND status = 'ACTIVE' ORDER BY startedAt DESC")
    fun getActiveProtectionSessionsForUser(userId: String): List<ProtectionSessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events WHERE entityId = :entityId ORDER BY occurredAt ASC")
    fun getEventsForEntity(entityId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY occurredAt ASC")
    fun getEventsForUser(userId: String): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memory WHERE id = :id LIMIT 1")
    fun getMemoryById(id: String): MemoryEntity?

    @Query("SELECT * FROM memory WHERE userId = :userId ORDER BY createdAt DESC")
    fun getMemoriesForUser(userId: String): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE userId = :userId AND status NOT IN ('EXPIRED', 'DELETED') ORDER BY confidence DESC")
    fun getActiveMemoriesForUser(userId: String): List<MemoryEntity>

    // Search and Retrieval (V1.8)

    @Query("SELECT * FROM goal WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchGoals(userId: String, query: String): List<GoalEntity>

    @Query("SELECT * FROM commitment WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchCommitments(userId: String, query: String): List<CommitmentEntity>

    @Query("SELECT * FROM reflection WHERE userId = :userId AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchReflections(userId: String, query: String): List<ReflectionEntity>

    @Query("SELECT * FROM memory WHERE userId = :userId AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMemories(userId: String, query: String): List<MemoryEntity>

    @Query("SELECT * FROM events WHERE userId = :userId AND (type LIKE '%' || :query || '%' OR metadata LIKE '%' || :query || '%') ORDER BY occurredAt DESC")
    fun searchEvents(userId: String, query: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    fun getEvent(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE userId = :userId AND occurredAt >= :startTime AND occurredAt <= :endTime ORDER BY occurredAt DESC")
    fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): List<EventEntity>

    @Query("SELECT * FROM memory WHERE userId = :userId AND createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt DESC")
    fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long): List<MemoryEntity>
}
