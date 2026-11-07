package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3.5.1: Durable repository interface for ProposedAction entities.
 */
interface ActionRepository {
    suspend fun saveAction(action: ProposedAction): Result<ProposedAction, Exception>
    suspend fun getActionById(id: String): Result<ProposedAction?, Exception>
    suspend fun getActionsForUser(userId: String): Result<List<ProposedAction>, Exception>
    fun observeActionsForUser(userId: String): Flow<List<ProposedAction>>
    suspend fun updateActionStatus(id: String, userId: String, status: ActionReviewStatus, timestamp: Long): Result<Unit, Exception>
    suspend fun deleteAction(id: String, userId: String): Result<Unit, Exception>
}
