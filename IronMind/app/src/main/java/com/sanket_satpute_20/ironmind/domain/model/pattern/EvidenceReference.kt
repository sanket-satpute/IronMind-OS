package com.sanket_satpute_20.ironmind.domain.model.pattern

/**
 * A typed boundary pointing to an authoritative source record.
 * 
 * It intentionally contains only identifiers to prevent sensitive source content 
 * (like reflection text or raw coordinates) from leaking into pattern descriptions.
 */
data class EvidenceReference(
    val sourceId: String,
    val sourceType: EvidenceSourceType
) {
    /**
     * Serializes this reference into a compact string format for legacy DB compatibility.
     * Format: TYPE|ID
     */
    fun serialize(): String = "${sourceType.name}|$sourceId"

    companion object {
        /**
         * Deserializes a string back into an EvidenceReference.
         * Falls back to treating the whole string as an EVENT ID if no separator is found
         * to prevent crashing on legacy string-only lists, though in this sprint, legacy
         * strings aren't fully migrated. We'll default to EVENT since it's the safest assumption
         * or just drop invalid ones.
         */
        fun deserialize(value: String): EvidenceReference? {
            val parts = value.split("|")
            if (parts.size == 2) {
                val typeStr = parts[0]
                val id = parts[1]
                val type = runCatching { EvidenceSourceType.valueOf(typeStr) }.getOrNull()
                if (type != null) {
                    return EvidenceReference(sourceId = id, sourceType = type)
                }
            }
            // For legacy migration where we only had IDs, assume it's an OBSERVATION or drop it.
            // Returning null drops it, which is safer if we don't know what it is.
            return null
        }
    }
}
