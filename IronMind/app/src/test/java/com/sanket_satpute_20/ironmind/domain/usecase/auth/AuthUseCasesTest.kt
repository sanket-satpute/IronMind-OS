package com.sanket_satpute_20.ironmind.domain.usecase.auth

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthUseCasesTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var signInAnonymouslyUseCase: SignInAnonymouslyUseCase
    private lateinit var observeAuthUserUseCase: ObserveAuthUserUseCase
    private lateinit var signOutUseCase: SignOutUseCase

    @Before
    fun setup() {
        authRepository = FakeAuthRepository()
        signInAnonymouslyUseCase = SignInAnonymouslyUseCase(authRepository)
        observeAuthUserUseCase = ObserveAuthUserUseCase(authRepository)
        signOutUseCase = SignOutUseCase(authRepository)
    }

    @Test
    fun `initial state is unauthenticated`() = runTest {
        val user = observeAuthUserUseCase().first()
        val status = authRepository.authStatus.first()

        assertNull(user)
        assertEquals(AuthStatus.UNAUTHENTICATED, status)
    }

    @Test
    fun `signInAnonymously updates auth state`() = runTest {
        val result = signInAnonymouslyUseCase()
        
        assertTrue(result is Result.Success)
        val user = (result as Result.Success).data
        assertTrue(user.isAnonymous)
        assertEquals("fake-anonymous-id", user.id)

        val observedUser = observeAuthUserUseCase().first()
        assertEquals(user, observedUser)
        
        val status = authRepository.authStatus.first()
        assertEquals(AuthStatus.AUTHENTICATED, status)
    }

    @Test
    fun `signOut clears auth state`() = runTest {
        signInAnonymouslyUseCase()
        
        var user = observeAuthUserUseCase().first()
        assertTrue(user != null)
        
        val result = signOutUseCase()
        assertTrue(result is Result.Success)

        user = observeAuthUserUseCase().first()
        assertNull(user)
        
        val status = authRepository.authStatus.first()
        assertEquals(AuthStatus.UNAUTHENTICATED, status)
    }
}
