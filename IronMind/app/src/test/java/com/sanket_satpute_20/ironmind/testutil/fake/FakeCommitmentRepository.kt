package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class FakeCommitmentRepository : CommitmentRepository {
    private val commitments = mutableMapOf<String, Commitment>()
    var shouldFail = false

    override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        commitments[commitment.id] = commitment
        return Result.Success(Unit)
    }

    override suspend fun getCommitment(id: String): Result<Commitment?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(commitments[id])
    }

    override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(commitments.values.filter { it.userId == userId }.sortedByDescending { it.createdAt })
    }

    override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(commitments.values.filter { it.goalId == goalId }.sortedByDescending { it.createdAt })
    }

    override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(commitments.values.filter { it.planId == planId }.sortedBy { it.createdAt })
    }

    override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(commitments.values.filter { it.taskId == taskId }.sortedBy { it.createdAt })
    }

    fun clear() {
        commitments.clear()
    }
}
