package com.sanket_satpute_20.ironmind.domain.model.action

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 3.5.1: Deterministic JSON codec for ProposedAction persistence in Room MemoryEntity.
 */
object ActionJsonCodec {

    private const val CURRENT_VERSION = 1

    fun encodeToJson(action: ProposedAction): String {
        val root = JSONObject()
        root.put("v", CURRENT_VERSION)
        root.put("category", action.category.name)
        root.put("title", action.title)
        root.put("explanation", action.explanation)
        action.sourceRecommendationId?.let { root.put("recId", it) }
        action.sourceInsightId?.let { root.put("insId", it) }
        action.targetEntityId?.let { root.put("targetId", it) }
        action.targetEntityType?.let { root.put("targetType", it) }
        root.put("priority", action.priority.name)
        root.put("urgency", action.urgency.name)
        root.put("evidenceIds", JSONArray(action.supportingEvidenceIds))
        root.put("conf", action.confidence.toDouble())
        root.put("auth", action.epistemicAuthority.name)
        root.put("provenance", JSONArray(action.sourceProvenance))
        root.put("freshness", action.sourceFreshness.name)
        root.put("reviewStatus", action.reviewStatus.name)
        root.put("createdAt", action.createdAt)
        action.expiresAt?.let { root.put("expiresAt", it) }
        action.reviewedAt?.let { root.put("reviewedAt", it) }
        action.deferredUntilMs?.let { root.put("deferredUntilMs", it) }
        action.executedAt?.let { root.put("executedAt", it) }
        action.lastAttemptedAt?.let { root.put("lastAttemptedAt", it) }
        action.outcomeId?.let { root.put("outcomeId", it) }
        root.put("suggestedSubTasks", JSONArray(action.suggestedSubTasks))
        action.suggestedNewDueAt?.let { root.put("suggestedNewDueAt", it) }

        action.executionResult?.let { res ->
            val execObj = JSONObject()
            execObj.put("executedAt", res.executedAt)
            execObj.put("success", res.success)
            res.affectedEntityId?.let { execObj.put("affectedId", it) }
            res.affectedEntityType?.let { execObj.put("affectedType", it) }
            execObj.put("message", res.message)
            execObj.put("subTaskIds", JSONArray(res.resultingSubTaskIds))
            res.previousDueAt?.let { execObj.put("prevDue", it) }
            res.newDueAt?.let { execObj.put("newDue", it) }
            root.put("executionResult", execObj)
        }

        return root.toString()
    }

    fun decodeFromJson(
        id: String,
        userId: String,
        jsonString: String,
        fallbackCreatedAt: Long = System.currentTimeMillis()
    ): ProposedAction? {
        return try {
            val root = JSONObject(jsonString)

            val category = try {
                ActionCategory.valueOf(root.getString("category"))
            } catch (e: Exception) {
                ActionCategory.TASK_RESCHEDULE
            }

            val title = root.optString("title", "Proposed Action")
            val explanation = root.optString("explanation", "")
            val recId = root.optString("recId").takeIf { it.isNotBlank() }
            val insId = root.optString("insId").takeIf { it.isNotBlank() }
            val targetId = root.optString("targetId").takeIf { it.isNotBlank() }
            val targetType = root.optString("targetType").takeIf { it.isNotBlank() }

            val priority = try {
                ActionPriority.valueOf(root.optString("priority", ActionPriority.MEDIUM.name))
            } catch (e: Exception) {
                ActionPriority.MEDIUM
            }

            val urgency = try {
                ActionUrgency.valueOf(root.optString("urgency", ActionUrgency.TODAY.name))
            } catch (e: Exception) {
                ActionUrgency.TODAY
            }

            val evidenceIds = mutableListOf<String>()
            val evArray = root.optJSONArray("evidenceIds")
            if (evArray != null) {
                for (i in 0 until evArray.length()) {
                    evidenceIds.add(evArray.getString(i))
                }
            }

            val confidence = root.optDouble("conf", 0.5).toFloat()

            val authority = try {
                EpistemicAuthority.valueOf(root.optString("auth", EpistemicAuthority.AI_HYPOTHESIS.name))
            } catch (e: Exception) {
                EpistemicAuthority.AI_HYPOTHESIS
            }

            val provenance = mutableListOf<String>()
            val provArray = root.optJSONArray("provenance")
            if (provArray != null) {
                for (i in 0 until provArray.length()) {
                    provenance.add(provArray.getString(i))
                }
            }

            val freshness = try {
                SourceFreshness.valueOf(root.optString("freshness", SourceFreshness.CURRENT.name))
            } catch (e: Exception) {
                SourceFreshness.CURRENT
            }

            val reviewStatus = try {
                ActionReviewStatus.valueOf(root.optString("reviewStatus", ActionReviewStatus.PROPOSED.name))
            } catch (e: Exception) {
                ActionReviewStatus.PROPOSED
            }

            val createdAt = root.optLong("createdAt", fallbackCreatedAt)
            val expiresAt = if (root.has("expiresAt")) root.getLong("expiresAt") else null
            val reviewedAt = if (root.has("reviewedAt")) root.getLong("reviewedAt") else null
            val deferredUntilMs = if (root.has("deferredUntilMs")) root.getLong("deferredUntilMs") else null
            val executedAt = if (root.has("executedAt")) root.getLong("executedAt") else null
            val lastAttemptedAt = if (root.has("lastAttemptedAt")) root.getLong("lastAttemptedAt") else null
            val outcomeId = root.optString("outcomeId").takeIf { it.isNotBlank() }

            val subTasks = mutableListOf<String>()
            val subArray = root.optJSONArray("suggestedSubTasks")
            if (subArray != null) {
                for (i in 0 until subArray.length()) {
                    subTasks.add(subArray.getString(i))
                }
            }

            val suggestedNewDueAt = if (root.has("suggestedNewDueAt")) root.getLong("suggestedNewDueAt") else null

            val execObj = root.optJSONObject("executionResult")
            val executionResult = if (execObj != null) {
                val resultingSubTaskIds = mutableListOf<String>()
                val resSubArray = execObj.optJSONArray("subTaskIds")
                if (resSubArray != null) {
                    for (i in 0 until resSubArray.length()) {
                        resultingSubTaskIds.add(resSubArray.getString(i))
                    }
                }

                ActionExecutionResult(
                    executedAt = execObj.optLong("executedAt", executedAt ?: createdAt),
                    success = execObj.optBoolean("success", false),
                    affectedEntityId = execObj.optString("affectedId").takeIf { it.isNotBlank() },
                    affectedEntityType = execObj.optString("affectedType").takeIf { it.isNotBlank() },
                    message = execObj.optString("message", ""),
                    resultingSubTaskIds = resultingSubTaskIds,
                    previousDueAt = if (execObj.has("prevDue")) execObj.getLong("prevDue") else null,
                    newDueAt = if (execObj.has("newDue")) execObj.getLong("newDue") else null
                )
            } else {
                null
            }

            ProposedAction(
                id = id,
                userId = userId,
                category = category,
                title = title,
                explanation = explanation,
                sourceRecommendationId = recId,
                sourceInsightId = insId,
                targetEntityId = targetId,
                targetEntityType = targetType,
                priority = priority,
                urgency = urgency,
                supportingEvidenceIds = evidenceIds,
                confidence = confidence,
                epistemicAuthority = authority,
                sourceProvenance = provenance,
                sourceFreshness = freshness,
                reviewStatus = reviewStatus,
                createdAt = createdAt,
                expiresAt = expiresAt,
                reviewedAt = reviewedAt,
                deferredUntilMs = deferredUntilMs,
                executedAt = executedAt,
                lastAttemptedAt = lastAttemptedAt,
                executionResult = executionResult,
                outcomeId = outcomeId,
                suggestedSubTasks = subTasks,
                suggestedNewDueAt = suggestedNewDueAt
            )
        } catch (e: Exception) {
            null
        }
    }
}
