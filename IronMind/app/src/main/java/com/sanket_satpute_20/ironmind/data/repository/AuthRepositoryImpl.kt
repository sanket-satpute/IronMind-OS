package com.sanket_satpute_20.ironmind.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser?.let { firebaseUser ->
                AuthUser(
                    id = firebaseUser.uid,
                    isAnonymous = firebaseUser.isAnonymous,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName
                )
            }
            trySend(user)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    override val authStatus: Flow<AuthStatus> = currentUser.map { user ->
        if (user != null) AuthStatus.AUTHENTICATED else AuthStatus.UNAUTHENTICATED
    }

    override suspend fun signInAnonymously(): Result<AuthUser, Exception> {
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            val user = result.user
            if (user != null) {
                Result.Success(
                    AuthUser(
                        id = user.uid,
                        isAnonymous = user.isAnonymous,
                        email = user.email,
                        displayName = user.displayName
                    )
                )
            } else {
                Result.Failure(Exception("Sign in succeeded but user is null"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit, Exception> {
        return try {
            firebaseAuth.signOut()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun getCurrentUser(): AuthUser? {
        return firebaseAuth.currentUser?.let { firebaseUser ->
            AuthUser(
                id = firebaseUser.uid,
                isAnonymous = firebaseUser.isAnonymous,
                email = firebaseUser.email,
                displayName = firebaseUser.displayName
            )
        }
    }
}
