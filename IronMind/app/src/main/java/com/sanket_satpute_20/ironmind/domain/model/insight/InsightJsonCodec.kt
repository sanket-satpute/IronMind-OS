package com.sanket_satpute_20.ironmind.domain.model.insight

import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic JSON serialization and deserialization for PersonalInsight records.
 * Uses Android/JVM standard org.json.JSONObject to ensure robust handling of strings,
 * punctuation, and nested explainability blocks without delimiter collisions.
 */
object InsightJsonCodec {

    private const val CURRENT_VERSION = 1

    fun encodeToJson(insight: PersonalInsight): String {
        val root = JSONObject()
        root.put("v", CURRENT_VERSION)
        root.put("category", insight.category.name)
        root.put("title", insight.title)
        root.put("explanation", insight.explanation)

        // Explainability Block
        val exp = JSONObject()
        exp.put("observed", insight.explainabilityBlock.whatWasObserved)
        exp.put("period", insight.explainabilityBlock.observationPeriod)
        exp.put("evidence", insight.explainabilityBlock.supportingEvidenceDescription)
        exp.put("relevance", insight.explainabilityBlock.whyItMatters)
        exp.put("uncertainty", insight.explainabilityBlock.uncertaintyOrLimitations)
        exp.put("nextSteps", insight.explainabilityBlock.suggestedNextSteps)
        root.put("explainability", exp)

        // References
        root.put("patterns", JSONArray(insight.supportingPatternIds))
        root.put("barriers", JSONArray(insight.supportingBarrierIds))
        root.put("evidenceIds", JSONArray(insight.supportingEvidenceIds))
        insight.linkedRecommendationId?.let { root.put("recId", it) }

        // Window & Timestamps
        root.put("winStart", insight.observationWindowStartMs)
        root.put("winEnd", insight.observationWindowEndMs)
        root.put("firstObserved", insight.firstObservedAt)
        root.put("lastObserved", insight.lastObservedAt)
        root.put("conf", insight.confidence.toDouble())

        // Epistemic authority & provenance
        root.put("auth", insight.epistemicAuthority.name)
        root.put("provenance", JSONArray(insight.sourceProvenance))
        root.put("freshness", insight.sourceFreshness.name)

        // Review state
        root.put("reviewStatus", insight.reviewStatus.name)
        root.put("confirmationState", insight.confirmationState.name)
        insight.userFeedback?.let { root.put("feedback", it) }
        insight.reviewedAt?.let { root.put("reviewedAt", it) }
        root.put("createdAt", insight.createdAt)
        root.put("updatedAt", insight.updatedAt)

        return root.toString()
    }

    fun decodeFromJson(
        id: String,
        userId: String,
        jsonString: String,
        fallbackCreatedAt: Long = System.currentTimeMillis()
    ): PersonalInsight? {
        return try {
            val root = JSONObject(jsonString)

            val category = try {
                InsightCategory.valueOf(root.getString("category"))
            } catch (e: Exception) {
                InsightCategory.PRODUCTIVITY
            }

            val title = root.optString("title", "Insight")
            val explanation = root.optString("explanation", "")

            val expObj = root.optJSONObject("explainability")
            val explainabilityBlock = if (expObj != null) {
                ExplainabilityBlock(
                    whatWasObserved = expObj.optString("observed", ""),
                    observationPeriod = expObj.optString("period", ""),
                    supportingEvidenceDescription = expObj.optString("evidence", ""),
                    whyItMatters = expObj.optString("relevance", ""),
                    uncertaintyOrLimitations = expObj.optString("uncertainty", ""),
                    suggestedNextSteps = expObj.optString("nextSteps", "")
                )
            } else {
                ExplainabilityBlock(
                    whatWasObserved = explanation,
                    observationPeriod = "Recorded period",
                    supportingEvidenceDescription = "Synthesized observation",
                    whyItMatters = "Personal awareness",
                    uncertaintyOrLimitations = "Historical sample",
                    suggestedNextSteps = "Review when convenient"
                )
            }

            val supportingPatternIds = jsonArrayToList(root.optJSONArray("patterns"))
            val supportingBarrierIds = jsonArrayToList(root.optJSONArray("barriers"))
            val supportingEvidenceIds = jsonArrayToList(root.optJSONArray("evidenceIds"))
            val linkedRecommendationId = if (root.has("recId") && !root.isNull("recId")) root.getString("recId") else null

            val winStart = root.optLong("winStart", 0L)
            val winEnd = root.optLong("winEnd", fallbackCreatedAt)
            val firstObserved = root.optLong("firstObserved", winStart)
            val lastObserved = root.optLong("lastObserved", winEnd)
            val conf = root.optDouble("conf", 0.5).toFloat()

            val authority = try {
                EpistemicAuthority.valueOf(root.optString("auth", EpistemicAuthority.DERIVED_CALCULATION.name))
            } catch (e: Exception) {
                EpistemicAuthority.DERIVED_CALCULATION
            }

            val provenance = jsonArrayToList(root.optJSONArray("provenance"))
            val freshness = try {
                SourceFreshness.valueOf(root.optString("freshness", SourceFreshness.CURRENT.name))
            } catch (e: Exception) {
                SourceFreshness.CURRENT
            }

            val reviewStatus = try {
                InsightReviewStatus.valueOf(root.optString("reviewStatus", InsightReviewStatus.UNREVIEWED.name))
            } catch (e: Exception) {
                InsightReviewStatus.UNREVIEWED
            }

            val confirmationState = try {
                if (root.has("confirmationState") && !root.isNull("confirmationState")) {
                    MemoryConfirmationState.valueOf(root.getString("confirmationState"))
                } else {
                    if (authority == EpistemicAuthority.USER_CONFIRMED_FACT) {
                        MemoryConfirmationState.USER_CONFIRMED
                    } else {
                        MemoryConfirmationState.UNCONFIRMED
                    }
                }
            } catch (e: Exception) {
                MemoryConfirmationState.UNCONFIRMED
            }

            val feedback = if (root.has("feedback") && !root.isNull("feedback")) root.getString("feedback") else null
            val reviewedAt = if (root.has("reviewedAt") && !root.isNull("reviewedAt")) root.getLong("reviewedAt") else null
            val createdAt = root.optLong("createdAt", fallbackCreatedAt)
            val updatedAt = root.optLong("updatedAt", createdAt)

            PersonalInsight(
                id = id,
                userId = userId,
                category = category,
                title = title,
                explanation = explanation,
                explainabilityBlock = explainabilityBlock,
                supportingPatternIds = supportingPatternIds,
                supportingBarrierIds = supportingBarrierIds,
                supportingEvidenceIds = supportingEvidenceIds,
                linkedRecommendationId = linkedRecommendationId,
                observationWindowStartMs = winStart,
                observationWindowEndMs = winEnd,
                firstObservedAt = firstObserved,
                lastObservedAt = lastObserved,
                confidence = conf,
                epistemicAuthority = authority,
                sourceProvenance = provenance,
                sourceFreshness = freshness,
                reviewStatus = reviewStatus,
                confirmationState = confirmationState,
                userFeedback = feedback,
                reviewedAt = reviewedAt,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            list.add(arr.optString(i))
        }
        return list
    }
}
