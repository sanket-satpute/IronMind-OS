package com.sanket_satpute_20.ironmind.domain.model

data class AuthUser(
    val id: String,
    val isAnonymous: Boolean,
    val email: String? = null,
    val displayName: String? = null
)
