package com.sanket_satpute_20.ironmind.domain.model

data class Reflection(
    val id: String,
    val userId: String,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val content: String,
    val sentiment: String?,
    val createdAt: Long,
    val schemaVersion: Int = 1
)
