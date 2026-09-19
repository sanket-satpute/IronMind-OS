package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAuthRepository : AuthRepository {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    
    override val currentUser: Flow<AuthUser?> = _currentUser

    override val authStatus: Flow<AuthStatus> = _currentUser.map { user ->
        if (user != null) AuthStatus.AUTHENTICATED else AuthStatus.UNAUTHENTICATED
    }

    var signInShouldFail = false

    override suspend fun signInAnonymously(): Result<AuthUser, Exception> {
        if (signInShouldFail) {
            return Result.Failure(Exception("Simulated sign in failure"))
        }
        val user = AuthUser(
            id = "fake-anonymous-id",
            isAnonymous = true,
            email = null,
            displayName = null
        )
        _currentUser.value = user
        return Result.Success(user)
    }

    override suspend fun signOut(): Result<Unit, Exception> {
        _currentUser.value = null
        return Result.Success(Unit)
    }

    override fun getCurrentUser(): AuthUser? {
        return _currentUser.value
    }
}
