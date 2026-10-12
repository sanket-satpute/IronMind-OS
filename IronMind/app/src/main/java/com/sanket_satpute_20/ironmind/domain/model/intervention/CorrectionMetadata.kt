package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Structured data representation for an intervention correction event.
 *
 * Invariant: LEARN ≠ ASSUME.
 * Unclassified or legacy corrections default strictly to INTERVENTION_CORRECTION
 * and must never be retroactively elevated to broader scopes without explicit user instruction.
 */
data class CorrectionMetadata(
    val scope: CorrectionScope = CorrectionScope.INTERVENTION_CORRECTION,
    val type: String? = null,
    val recommendation: String? = null,
    val correctedText: String? = null
)

/**
 * Safe serializer and parser for Event.metadata associated with EventType.INTERVENTION_OVERRIDDEN.
 *
 * Guarantees:
 * 1. Scope is explicitly persisted for newly created corrections.
 * 2. Unsupplied, missing, or malformed scopes safely default to INTERVENTION_CORRECTION.
 * 3. Exact user text preservation (commas, equal signs, quotes, Unicode, whitespace, newlines).
 * 4. Backward compatibility with legacy metadata strings lacking a scope field.
 * 5. Safe degradation without crashing or authorizing unauthorized broader learning.
 */
object CorrectionMetadataParser {

    const val KEY_SCOPE = "scope"
    const val KEY_TYPE = "type"
    const val KEY_RECOMMENDATION = "recommendation"
    const val KEY_CORRECTED_TEXT = "correctedText"

    /**
     * Serializes structured correction fields into an event metadata string.
     */
    fun serialize(
        scope: CorrectionScope = CorrectionScope.INTERVENTION_CORRECTION,
        type: String? = null,
        recommendation: String? = null,
        correctedText: String? = null
    ): String {
        return buildString {
            append("$KEY_SCOPE=${scope.name}")
            if (!type.isNullOrBlank()) {
                append(",$KEY_TYPE=$type")
            }
            if (!recommendation.isNullOrBlank()) {
                append(",$KEY_RECOMMENDATION=$recommendation")
            }
            if (correctedText != null) {
                append(",$KEY_CORRECTED_TEXT=$correctedText")
            }
        }
    }

    /**
     * Parses an event metadata string into structured CorrectionMetadata.
     * Safely handles legacy, malformed, delimiter-rich, and null metadata.
     */
    fun parse(metadata: String?): CorrectionMetadata {
        if (metadata.isNullOrBlank()) {
            return CorrectionMetadata(
                scope = CorrectionScope.INTERVENTION_CORRECTION,
                type = null,
                recommendation = null,
                correctedText = null
            )
        }

        val commaToken = ",$KEY_CORRECTED_TEXT="
        val exactStartToken = "$KEY_CORRECTED_TEXT="

        // Determine where prefix keys end to avoid matching occurrences inside recommendation or type values
        val scopeIndex = metadata.indexOf("$KEY_SCOPE=")
        val typeIndex = metadata.indexOf("$KEY_TYPE=")
        val recIndex = metadata.indexOf("$KEY_RECOMMENDATION=")

        val searchStart = maxOf(
            if (recIndex != -1) recIndex + KEY_RECOMMENDATION.length + 1 else 0,
            if (typeIndex != -1) typeIndex + KEY_TYPE.length + 1 else 0,
            if (scopeIndex != -1) scopeIndex + KEY_SCOPE.length + 1 else 0
        )

        val delimiterIndex = metadata.indexOf(commaToken, searchStart)
        val isAtStart = metadata.startsWith(exactStartToken)

        val (prefix, rawPayload) = when {
            isAtStart -> {
                "" to metadata.substring(exactStartToken.length)
            }
            delimiterIndex != -1 -> {
                metadata.substring(0, delimiterIndex) to metadata.substring(delimiterIndex + commaToken.length)
            }
            else -> {
                metadata to null
            }
        }

        var parsedScope: CorrectionScope? = null
        var parsedType: String? = null
        var parsedRecommendation: String? = null

        // Locate known keys within the prefix to avoid breaking on values containing commas
        val scopeToken = "$KEY_SCOPE="
        val typeToken = "$KEY_TYPE="
        val recToken = "$KEY_RECOMMENDATION="

        val scopeIndexInPrefix = prefix.indexOf(scopeToken)
        val typeIndexInPrefix = prefix.indexOf(typeToken)
        val recIndexInPrefix = prefix.indexOf(recToken)

        val keyPositions = mutableListOf<Triple<Int, Int, String>>()
        if (scopeIndexInPrefix != -1) keyPositions.add(Triple(scopeIndexInPrefix, scopeToken.length, KEY_SCOPE))
        if (typeIndexInPrefix != -1) keyPositions.add(Triple(typeIndexInPrefix, typeToken.length, KEY_TYPE))
        if (recIndexInPrefix != -1) keyPositions.add(Triple(recIndexInPrefix, recToken.length, KEY_RECOMMENDATION))

        keyPositions.sortBy { it.first }

        if (keyPositions.isNotEmpty()) {
            for (i in keyPositions.indices) {
                val current = keyPositions[i]
                val valueStart = current.first + current.second
                val valueEnd = if (i + 1 < keyPositions.size) {
                    val nextStart = keyPositions[i + 1].first
                    // Strip trailing comma before the next key
                    if (nextStart > 0 && prefix[nextStart - 1] == ',') nextStart - 1 else nextStart
                } else {
                    // Strip trailing comma at the end of prefix
                    if (prefix.isNotEmpty() && prefix.endsWith(",")) prefix.length - 1 else prefix.length
                }

                if (valueStart <= valueEnd && valueStart <= prefix.length) {
                    val rawVal = prefix.substring(valueStart, minOf(valueEnd, prefix.length)).trim()
                    when (current.third) {
                        KEY_SCOPE -> {
                            parsedScope = try {
                                CorrectionScope.valueOf(rawVal)
                            } catch (_: IllegalArgumentException) {
                                CorrectionScope.INTERVENTION_CORRECTION
                            }
                        }
                        KEY_TYPE -> parsedType = rawVal.ifEmpty { null }
                        KEY_RECOMMENDATION -> parsedRecommendation = rawVal.ifEmpty { null }
                    }
                }
            }
        } else {
            // Fallback for metadata that doesn't use the primary known keys or is malformed
            val pairs = prefix.split(",")
            for (pair in pairs) {
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        KEY_SCOPE -> {
                            parsedScope = try {
                                CorrectionScope.valueOf(value)
                            } catch (_: IllegalArgumentException) {
                                CorrectionScope.INTERVENTION_CORRECTION
                            }
                        }
                        KEY_TYPE -> if (parsedType == null) parsedType = value.ifEmpty { null }
                        KEY_RECOMMENDATION -> if (parsedRecommendation == null) parsedRecommendation = value.ifEmpty { null }
                    }
                }
            }
        }

        // Parse correctedText payload
        val parsedCorrectedText: String? = if (rawPayload != null) {
            if (parsedScope != null) {
                // Newly classified event with explicit scope: payload is verbatim remainder
                rawPayload
            } else {
                // Legacy event without scope: check if there are legacy trailing keys like ",other=xyz"
                val commaIndex = rawPayload.indexOf(",")
                if (commaIndex != -1) {
                    val afterComma = rawPayload.substring(commaIndex + 1)
                    if (afterComma.contains("=")) {
                        rawPayload.substring(0, commaIndex)
                    } else {
                        rawPayload
                    }
                } else {
                    rawPayload
                }
            }
        } else {
            null
        }

        return CorrectionMetadata(
            scope = parsedScope ?: CorrectionScope.INTERVENTION_CORRECTION,
            type = parsedType,
            recommendation = parsedRecommendation,
            correctedText = parsedCorrectedText
        )
    }
}
