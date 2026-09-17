package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment

interface CommitmentRepository : Repository {
    suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception>
    suspend fun getCommitment(id: String): Result<Commitment?, Exception>
    suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception>
    suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception>
    suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception>
    suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception>
}
