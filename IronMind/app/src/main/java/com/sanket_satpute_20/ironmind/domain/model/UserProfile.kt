package com.sanket_satpute_20.ironmind.domain.model

data class UserProfile(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val displayName: String,
    val timezone: String,
    val createdFrom: String,
    val status: String
)
