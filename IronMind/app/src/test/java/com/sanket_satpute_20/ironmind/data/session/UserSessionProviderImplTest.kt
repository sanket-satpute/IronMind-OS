package com.sanket_satpute_20.ironmind.data.session

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionProviderImplTest {

    private class FakeAuthRepository(
        initialUser: AuthUser? = null
    ) : AuthRepository {
        val userFlow = MutableStateFlow(initialUser)
        private var currentUserRef = initialUser

        override val currentUser: Flow<AuthUser?> = userFlow
        override val authStatus: Flow<AuthStatus> = MutableStateFlow(
            if (initialUser != null) AuthStatus.AUTHENTICATED else AuthStatus.UNAUTHENTICATED
        )

        override fun getCurrentUser(): AuthUser? = currentUserRef

        override suspend fun signInAnonymously(): Result<AuthUser, Exception> {
            val user = AuthUser("anon-123", true, null, null)
            currentUserRef = user
            userFlow.value = user
            return Result.Success(user)
        }

        override suspend fun signOut(): Result<Unit, Exception> {
            currentUserRef = null
            userFlow.value = null
            return Result.Success(Unit)
        }

        fun emitUser(user: AuthUser?) {
            currentUserRef = user
            userFlow.value = user
        }
    }

    @Test
    fun `initial state defaults to OfflineFallback when auth repository has no user`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        val state = provider.sessionState.value
        assertTrue(state is SessionState.OfflineFallback)
        assertEquals("user-1", provider.getActiveUserId())
        assertFalse(state.isAuthenticated)
    }

    @Test
    fun `initial state is Authenticated when auth repository has cached user`() = runTest(UnconfinedTestDispatcher()) {
        val cachedUser = AuthUser("cached-firebase-uid", true, null, null)
        val fakeAuth = FakeAuthRepository(cachedUser)
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        val state = provider.sessionState.value
        assertTrue(state is SessionState.Authenticated)
        assertEquals("cached-firebase-uid", provider.getActiveUserId())
        assertTrue(state.isAuthenticated)
    }

    @Test
    fun `sessionState transitions from OfflineFallback to Authenticated on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        assertEquals("user-1", provider.getActiveUserId())

        fakeAuth.emitUser(AuthUser("new-firebase-uid", true, null, null))

        val state = provider.sessionState.value
        assertTrue("Expected Authenticated but was $state", state is SessionState.Authenticated)
        assertEquals("new-firebase-uid", provider.getActiveUserId())
        assertTrue(state.isAuthenticated)
    }

    @Test
    fun `sessionState transitions to OfflineFallback on sign-out`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(AuthUser("initial-uid", false, "user@test.com", "Test User"))
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        assertTrue(provider.sessionState.value is SessionState.Authenticated)

        fakeAuth.emitUser(null)

        val state = provider.sessionState.value
        assertTrue("Expected OfflineFallback but was $state", state is SessionState.OfflineFallback)
        assertEquals("user-1", provider.getActiveUserId())
        assertFalse(state.isAuthenticated)
    }

    @Test
    fun `erased session generation blocks stale operations but permits subsequent reauthentication to same UID`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(AuthUser("alice-uid", false, "alice@test.com", "Alice"))
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        assertTrue(provider.sessionState.value is SessionState.Authenticated)
        assertEquals("alice-uid", provider.getActiveUserId())

        val gen1 = provider.getSessionGeneration()

        // Step 1: User initiates erasure on generation 1
        provider.markUserErasing("alice-uid", gen1)
        assertTrue(provider.isUserErased("alice-uid", gen1))

        // withUserSession under erasing generation 1 throws
        var threwErasing = false
        try {
            provider.withUserSession(expectedGeneration = gen1) { it }
        } catch (e: IllegalStateException) {
            threwErasing = true
            assertTrue(e.message?.contains("has been erased or is undergoing erasure") == true)
        }
        assertTrue("Expected operation under erasing generation to fail", threwErasing)

        // Step 2: Complete erasure
        provider.markUserErased("alice-uid", gen1)
        assertTrue("Generation 1 must be marked erased", provider.isUserErased("alice-uid", gen1))

        // Step 3: Sign out
        fakeAuth.emitUser(null)
        assertTrue(provider.sessionState.value is SessionState.OfflineFallback)

        // Step 4: Legitimate re-authentication with the SAME UID
        fakeAuth.emitUser(AuthUser("alice-uid", false, "alice@test.com", "Alice"))

        val stateAfterReauth = provider.sessionState.value
        assertTrue("State must transition to Authenticated on re-login", stateAfterReauth is SessionState.Authenticated)
        assertEquals("alice-uid", provider.getActiveUserId())

        val gen2 = provider.getSessionGeneration()
        assertTrue("Generation must advance after re-authentication", gen2 > gen1)

        // Fresh session for alice-uid under gen2 must NOT be erased
        assertFalse("Fresh session under gen2 must not be erased", provider.isUserErased("alice-uid", gen2))

        val resultFromFreshSession = provider.withUserSession { it }
        assertEquals("alice-uid", resultFromFreshSession)

        // Step 5: Verify that a stale worker captured under gen1 remains permanently rejected
        var threwStaleGeneration = false
        try {
            provider.withUserSession(expectedGeneration = gen1) { it }
        } catch (e: IllegalStateException) {
            threwStaleGeneration = true
            assertTrue(e.message?.contains("has expired") == true)
        }
        assertTrue("Stale worker from gen1 must be rejected under gen2", threwStaleGeneration)
    }

    @Test
    fun `anonymous bootstrap operates cleanly in fresh generation after erasure`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(AuthUser("old-user-uid", false, null, null))
        val provider = UserSessionProviderImpl(fakeAuth, backgroundScope)

        val gen1 = provider.getSessionGeneration()
        provider.markUserErasing("old-user-uid", gen1)
        provider.markUserErased("old-user-uid", gen1)

        fakeAuth.emitUser(null)
        assertTrue(provider.sessionState.value is SessionState.OfflineFallback)

        // Anonymous bootstrap signs in
        fakeAuth.emitUser(AuthUser("anon-fresh-999", true, null, null))

        val state = provider.sessionState.value
        assertTrue(state is SessionState.Authenticated)
        assertEquals("anon-fresh-999", provider.getActiveUserId())

        val activeResult = provider.withUserSession { it }
        assertEquals("anon-fresh-999", activeResult)
    }
}
