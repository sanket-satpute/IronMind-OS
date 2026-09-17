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

    @Query("SELECT * FROM commitment WHERE goalId = :goalId ORDER BY createdAt DESC")
    fun getCommitmentsForGoal(goalId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE planId = :planId ORDER BY createdAt ASC")
    fun getCommitmentsForPlan(planId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun getCommitmentsForTask(taskId: String): List<CommitmentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOutcome(outcome: OutcomeEntity)

    @Query("SELECT * FROM outcome WHERE id = :id LIMIT 1")
    fun getOutcome(id: String): OutcomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReflection(reflection: ReflectionEntity)

    @Query("SELECT * FROM reflection WHERE id = :id LIMIT 1")
    fun getReflection(id: String): ReflectionEntity?
}
