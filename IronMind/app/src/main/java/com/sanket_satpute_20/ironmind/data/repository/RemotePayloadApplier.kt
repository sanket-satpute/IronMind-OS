package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import org.json.JSONObject

object RemotePayloadApplier {

    fun apply(
        ironMindDao: IronMindDao,
        entityType: String,
        remotePayload: String,
        isDeleted: Boolean? = null
    ): Boolean {
        val json = JSONObject(remotePayload)
        val tombstone = isDeleted ?: json.optBoolean("isDeleted", false)
        val entityId = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing or blank 'id' in remote payload")
        val userId = json.optString("userId").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing or blank 'userId' in remote payload")

        if (tombstone) {
            when (entityType) {
                "Goal" -> ironMindDao.deleteGoal(userId, entityId)
                "Plan" -> ironMindDao.deletePlan(userId, entityId)
                "Task" -> ironMindDao.deleteTask(userId, entityId)
                "Commitment" -> ironMindDao.deleteCommitment(userId, entityId)
                "Reflection" -> ironMindDao.deleteReflection(userId, entityId)
                "Memory" -> ironMindDao.deleteMemory(userId, entityId)
                else -> throw IllegalArgumentException("Unknown entityType for tombstone deletion: $entityType")
            }
            return true
        }

        when (entityType) {
            "Goal" -> {
                val goal = GoalEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    ambitionId = if (json.has("ambitionId") && !json.isNull("ambitionId")) json.getString("ambitionId") else null,
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    why = json.optString("why", ""),
                    importance = json.optInt("importance", 1),
                    status = json.optString("status", "ACTIVE"),
                    targetAt = if (json.has("targetAt") && !json.isNull("targetAt")) json.getLong("targetAt") else null,
                    startedAt = if (json.has("startedAt") && !json.isNull("startedAt")) json.getLong("startedAt") else null,
                    completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.getLong("completedAt") else null,
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertGoal(goal)
            }
            "Plan" -> {
                val plan = PlanEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    goalId = if (json.has("goalId") && !json.isNull("goalId")) json.getString("goalId") else null,
                    ambitionId = if (json.has("ambitionId") && !json.isNull("ambitionId")) json.getString("ambitionId") else null,
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    status = json.optString("status", "ACTIVE"),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    startedAt = if (json.has("startedAt") && !json.isNull("startedAt")) json.getLong("startedAt") else null,
                    completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.getLong("completedAt") else null,
                    source = json.optString("source", "USER"),
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertPlan(plan)
            }
            "Task" -> {
                val task = TaskEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    goalId = if (json.has("goalId") && !json.isNull("goalId")) json.getString("goalId") else null,
                    planId = if (json.has("planId") && !json.isNull("planId")) json.getString("planId") else null,
                    parentTaskId = if (json.has("parentTaskId") && !json.isNull("parentTaskId")) json.getString("parentTaskId") else null,
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    status = json.optString("status", "PENDING"),
                    priority = json.optInt("priority", 1),
                    estimatedDurationMinutes = if (json.has("estimatedDurationMinutes") && !json.isNull("estimatedDurationMinutes")) json.getInt("estimatedDurationMinutes") else null,
                    scheduledAt = if (json.has("scheduledAt") && !json.isNull("scheduledAt")) json.getLong("scheduledAt") else null,
                    dueAt = if (json.has("dueAt") && !json.isNull("dueAt")) json.getLong("dueAt") else null,
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.getLong("completedAt") else null,
                    postponedAt = if (json.has("postponedAt") && !json.isNull("postponedAt")) json.getLong("postponedAt") else null,
                    source = json.optString("source", "USER"),
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertTask(task)
            }
            "Commitment" -> {
                val commitment = CommitmentEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    goalId = if (json.has("goalId") && !json.isNull("goalId")) json.getString("goalId") else null,
                    planId = if (json.has("planId") && !json.isNull("planId")) json.getString("planId") else null,
                    taskId = if (json.has("taskId") && !json.isNull("taskId")) json.getString("taskId") else null,
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    committedAt = json.optLong("committedAt", System.currentTimeMillis()),
                    scheduledStartAt = if (json.has("scheduledStartAt") && !json.isNull("scheduledStartAt")) json.getLong("scheduledStartAt") else null,
                    scheduledEndAt = if (json.has("scheduledEndAt") && !json.isNull("scheduledEndAt")) json.getLong("scheduledEndAt") else null,
                    status = json.optString("status", "COMMITTED"),
                    priority = json.optInt("priority", 1),
                    source = json.optString("source", "USER"),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    startedAt = if (json.has("startedAt") && !json.isNull("startedAt")) json.getLong("startedAt") else null,
                    completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.getLong("completedAt") else null,
                    postponedAt = if (json.has("postponedAt") && !json.isNull("postponedAt")) json.getLong("postponedAt") else null,
                    missedAt = if (json.has("missedAt") && !json.isNull("missedAt")) json.getLong("missedAt") else null,
                    recoveredAt = if (json.has("recoveredAt") && !json.isNull("recoveredAt")) json.getLong("recoveredAt") else null,
                    parentCommitmentId = if (json.has("parentCommitmentId") && !json.isNull("parentCommitmentId")) json.getString("parentCommitmentId") else null,
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertCommitment(commitment)
            }
            "Reflection" -> {
                val reflection = ReflectionEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    targetEntityId = if (json.has("targetEntityId") && !json.isNull("targetEntityId")) json.getString("targetEntityId") else null,
                    targetEntityType = if (json.has("targetEntityType") && !json.isNull("targetEntityType")) json.getString("targetEntityType") else null,
                    content = json.optString("content", json.optString("response", "")),
                    sentiment = if (json.has("sentiment") && !json.isNull("sentiment")) json.getString("sentiment") else null,
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertReflection(reflection)
            }
            "Memory" -> {
                val memory = MemoryEntity(
                    id = json.getString("id"),
                    userId = json.getString("userId"),
                    type = json.optString("type", "INSIGHT"),
                    content = json.getString("content"),
                    source = json.optString("source", "SYSTEM"),
                    confidence = json.optDouble("confidence", 1.0).toFloat(),
                    evidenceCount = json.optInt("evidenceCount", 1),
                    firstObservedAt = json.optLong("firstObservedAt", System.currentTimeMillis()),
                    lastObservedAt = json.optLong("lastObservedAt", System.currentTimeMillis()),
                    confirmationState = json.optString("confirmationState", "CONFIRMED"),
                    status = json.optString("status", "ACTIVE"),
                    expiresAt = if (json.has("expiresAt") && !json.isNull("expiresAt")) json.getLong("expiresAt") else null,
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    schemaVersion = json.optInt("schemaVersion", 1)
                )
                ironMindDao.insertMemory(memory)
            }
            else -> throw IllegalArgumentException("Unknown entityType for remote convergence: $entityType")
        }
        return true
    }
}
