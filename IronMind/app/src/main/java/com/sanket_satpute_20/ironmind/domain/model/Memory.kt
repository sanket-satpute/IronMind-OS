package com.sanket_satpute_20.ironmind.domain.model

data class Memory(
    val id: String,
    val userId: String,

    /** A short label describing the type/category of this memory (e.g. "preference", "barrier", "habit"). */
    val type: String,

    /** Human-readable description of what this memory represents. */
    val content: String,

    /** Where this memory originated. */
    val source: EntitySource,

    /** 0.0 to 1.0 confidence that this memory is accurate. */
    val confidence: Float,

    /** Number of distinct evidence observations that support this memory. */
    val evidenceCount: Int,

    val firstObservedAt: Long,
    val lastObservedAt: Long,

    val confirmationState: MemoryConfirmationState,

    val status: MemoryStatus,

    /** Optional expiry epoch millis. null means no expiry. */
    val expiresAt: Long?,

    val createdAt: Long,
    val updatedAt: Long,

    val schemaVersion: Int = 1
)
