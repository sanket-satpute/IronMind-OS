package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val authStatus: Flow<AuthStatus>
    val currentUser: Flow<AuthUser?>

    suspend fun signInAnonymously(): Result<AuthUser, Exception>

    suspend fun signOut(): Result<Unit, Exception>

    fun getCurrentUser(): AuthUser?

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<AuthUser, Exception> =
        Result.Failure(UnsupportedOperationException("Email/password sign-in not supported"))

    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<AuthUser, Exception> =
        Result.Failure(UnsupportedOperationException("User creation not supported"))

    suspend fun signInOrCreateWithEmailAndPassword(email: String, password: String): Result<AuthUser, Exception> =
        Result.Failure(UnsupportedOperationException("Staging sign-in not supported"))
}
