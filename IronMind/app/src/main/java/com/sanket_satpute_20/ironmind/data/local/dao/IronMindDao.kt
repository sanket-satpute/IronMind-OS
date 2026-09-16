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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGoal(goal: GoalEntity)

    @Query("SELECT * FROM goal WHERE id = :id LIMIT 1")
    fun getGoal(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlan(plan: PlanEntity)

    @Query("SELECT * FROM plan WHERE id = :id LIMIT 1")
    fun getPlan(id: String): PlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTask(task: TaskEntity)

    @Query("SELECT * FROM task WHERE id = :id LIMIT 1")
    fun getTask(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCommitment(commitment: CommitmentEntity)

    @Query("SELECT * FROM commitment WHERE id = :id LIMIT 1")
    fun getCommitment(id: String): CommitmentEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOutcome(outcome: OutcomeEntity)

    @Query("SELECT * FROM outcome WHERE id = :id LIMIT 1")
    fun getOutcome(id: String): OutcomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReflection(reflection: ReflectionEntity)

    @Query("SELECT * FROM reflection WHERE id = :id LIMIT 1")
    fun getReflection(id: String): ReflectionEntity?
}
