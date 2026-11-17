package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteUserDataUseCaseTest {

    @Test
    fun `invoke rejects blank userId`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val useCase = DeleteUserDataUseCase(repo)

        val result = useCase("")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `invoke rejects legacy user when session is authenticated`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))
        val useCase = DeleteUserDataUseCase(repo, sessionProvider)

        val result = useCase(SessionState.DEFAULT_LEGACY_USER_ID)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message?.contains("Cannot erase default_legacy_user when active session is authenticated") == true)
    }

    @Test
    fun `invoke rejects mismatched userId against active authenticated session`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))
        val useCase = DeleteUserDataUseCase(repo, sessionProvider)

        val result = useCase("wrong-user-456")
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message?.contains("active session belongs to user-123") == true)
    }

    @Test
    fun `invoke rejects authenticated userId when session is unauthenticated offline fallback`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.OfflineFallback())
        val useCase = DeleteUserDataUseCase(repo, sessionProvider)

        val result = useCase("auth-user-123")
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message?.contains("active session is unauthenticated offline fallback") == true)
    }

    @Test
    fun `invoke rejects when session is initializing`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.Initializing)
        val useCase = DeleteUserDataUseCase(repo, sessionProvider)

        val result = useCase("user-123")
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalStateException)
        assertTrue(error.message?.contains("while session is initializing") == true)
    }

    @Test
    fun `invoke rejects when session reconciliation failed`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.ReconciliationFailed(Exception("Network timeout"), "user-123"))
        val useCase = DeleteUserDataUseCase(repo, sessionProvider)

        val result = useCase("user-123")
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalStateException)
        assertTrue(error.message?.contains("reconciliation is in failed state") == true)
    }

    @Test
    fun `invoke executes successfully when authenticated userId matches active session`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        coEvery { repo.deleteAllUserData("user-123") } returns Result.Success(Unit)
        val authRepo = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepo.signOut() } returns Result.Success(Unit)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))

        val useCase = DeleteUserDataUseCase(repo, sessionProvider, authRepo)
        val result = useCase("user-123")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { repo.deleteAllUserData("user-123") }
        coVerify(exactly = 1) { authRepo.signOut() }
        verify(exactly = 2) { repo.cancelAllUserWorkers() } // Pre-cancellation and final sweep
        assertTrue("user-123 must be marked erased in admission fence", sessionProvider.isUserErased("user-123"))
    }

    @Test
    fun `invoke executes successfully for default_legacy_user when session is offline fallback and skips signOut`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        coEvery { repo.deleteAllUserData(SessionState.DEFAULT_LEGACY_USER_ID) } returns Result.Success(Unit)
        val authRepo = mockk<AuthRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.OfflineFallback())

        val useCase = DeleteUserDataUseCase(repo, sessionProvider, authRepo)
        val result = useCase(SessionState.DEFAULT_LEGACY_USER_ID)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { repo.deleteAllUserData(SessionState.DEFAULT_LEGACY_USER_ID) }
        coVerify(exactly = 0) { authRepo.signOut() } // Never signOut for legacy user
    }

    @Test
    fun `invoke enforces single-flight and rejects concurrent invocation`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        val authRepo = mockk<AuthRepository>(relaxed = true)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))

        val pauseErasure = CompletableDeferred<Unit>()
        val erasureStarted = CompletableDeferred<Unit>()

        coEvery { repo.deleteAllUserData("user-123") } coAnswers {
            erasureStarted.complete(Unit)
            pauseErasure.await()
            Result.Success(Unit)
        }

        val useCase = DeleteUserDataUseCase(repo, sessionProvider, authRepo)

        val firstJob = launch(Dispatchers.IO) {
            useCase("user-123")
        }

        erasureStarted.await()

        // Concurrent second call must fail fast
        val secondResult = useCase("user-123")
        assertTrue(secondResult is Result.Failure)
        val error = (secondResult as Result.Failure).error
        assertTrue(error is IllegalStateException)
        assertTrue(error.message?.contains("already in progress") == true)

        pauseErasure.complete(Unit)
        firstJob.join()
    }

    @Test
    fun `subsequent withUserSession calls throw for erased user after erasure`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        coEvery { repo.deleteAllUserData("user-123") } returns Result.Success(Unit)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))

        val useCase = DeleteUserDataUseCase(repo, sessionProvider)
        val result = useCase("user-123")
        assertTrue(result is Result.Success)

        try {
            sessionProvider.withUserSession { "ok" }
            fail("Expected withUserSession to throw for erased user")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("has been erased") == true)
        }
    }

    @Test
    fun `same-UID reauthentication after erasure permits fresh operations in new generation`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        coEvery { repo.deleteAllUserData("user-123") } returns Result.Success(Unit)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))
        val erasedGen = sessionProvider.getSessionGeneration()

        val useCase = DeleteUserDataUseCase(repo, sessionProvider)
        val result = useCase("user-123")
        assertTrue(result is Result.Success)

        // Session was erased for gen 1
        assertTrue(sessionProvider.isUserErased("user-123", erasedGen))

        // Legitimate re-authentication with SAME UID transitions state (increments generation in fake)
        sessionProvider.setState(SessionState.Authenticated("user-123", false))
        val newGen = sessionProvider.getSessionGeneration()
        assertTrue("Generation must advance", newGen > erasedGen)

        // Fresh session is NOT erased
        assertFalse(sessionProvider.isUserErased("user-123", newGen))
        val freshResult = sessionProvider.withUserSession { "reauthenticated: $it" }
        assertEquals("reauthenticated: user-123", freshResult)

        // Meanwhile, a stale worker checking or passing the erased generation remains blocked
        try {
            sessionProvider.withUserSession(expectedGeneration = erasedGen) { "should fail" }
            fail("Expected stale generation operation to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("has expired") == true)
        }
    }

    @Test
    fun `anonymous bootstrap operates cleanly in fresh generation after erasure`() = runBlocking {
        val repo = mockk<DataManagementRepository>(relaxed = true)
        coEvery { repo.deleteAllUserData("user-123") } returns Result.Success(Unit)
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", false))

        val useCase = DeleteUserDataUseCase(repo, sessionProvider)
        val result = useCase("user-123")
        assertTrue(result is Result.Success)

        // Fresh anonymous user bootstraps
        sessionProvider.setState(SessionState.Authenticated("anon-fresh-uid-456", isAnonymous = true))
        val activeResult = sessionProvider.withUserSession { it }
        assertEquals("anon-fresh-uid-456", activeResult)
    }
}
