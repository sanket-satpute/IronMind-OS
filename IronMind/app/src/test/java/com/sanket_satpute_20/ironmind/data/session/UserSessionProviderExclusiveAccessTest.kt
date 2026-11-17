package com.sanket_satpute_20.ironmind.data.session

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithExclusiveAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSessionProviderExclusiveAccessTest {

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
    }

    // 1. Reader drainage: An active read operation holds the barrier. A writer requests exclusive access.
    // Verify the writer cannot enter until the reader completes.
    @Test(timeout = 10_000L)
    fun `reader drainage - writer cannot enter until active readers complete`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()
        val writerEntered = CompletableDeferred<Unit>()

        try {
            val readerJob = launch {
                provider.withUserSession { userId ->
                    assertEquals("test-user", userId)
                    readerEntered.complete(Unit)
                    releaseReader.await()
                }
            }
            readerEntered.await()

            val writerJob = launch {
                provider.withExclusiveAccess { userId ->
                    assertEquals("test-user", userId)
                    writerEntered.complete(Unit)
                }
            }

            while (provider.barrier.isGateOpen()) yield()
            assertFalse("Writer must not enter while reader is active", writerEntered.isCompleted)

            releaseReader.complete(Unit)
            readerJob.join()

            writerEntered.await()
            writerJob.join()
            assertTrue("Writer must complete after readers drain", writerEntered.isCompleted)
        } finally {
            providerJob.cancel()
            releaseReader.complete(Unit)
        }
    }

    // 2. Reader exclusion: While a writer is active, a new reader must remain blocked.
    @Test(timeout = 10_000L)
    fun `reader exclusion - while writer is active new readers remain blocked`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val readerEntered = CompletableDeferred<Unit>()

        try {
            val writerJob = launch {
                provider.withExclusiveAccess {
                    writerEntered.complete(Unit)
                    releaseWriter.await()
                }
            }
            writerEntered.await()

            val readerJob = launch {
                provider.withUserSession {
                    readerEntered.complete(Unit)
                }
            }

            repeat(5) { yield() }
            assertFalse("New reader must remain blocked while writer owns exclusive access", readerEntered.isCompleted)

            releaseWriter.complete(Unit)
            writerJob.join()

            readerEntered.await()
            readerJob.join()
            assertTrue("Reader must complete after writer finishes", readerEntered.isCompleted)
        } finally {
            providerJob.cancel()
            releaseWriter.complete(Unit)
        }
    }

    // 3. Gate reopening: After the writer completes normally, new readers can proceed.
    @Test(timeout = 10_000L)
    fun `gate reopening - after writer completes normally new readers proceed`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)

        try {
            val writeResult = provider.withExclusiveAccess { userId ->
                "written-for-$userId"
            }
            assertEquals("written-for-test-user", writeResult)
            assertTrue("Barrier gate must be open after normal writer completion", provider.barrier.isGateOpen())
            assertEquals(0, provider.barrier.getActiveReadersCount())

            val readResult = provider.withUserSession { userId ->
                "read-for-$userId"
            }
            assertEquals("read-for-test-user", readResult)
        } finally {
            providerJob.cancel()
        }
    }

    // 4. Exception safety: If the writer throws, the barrier gate is reopened and waiting readers can proceed.
    @Test(timeout = 10_000L)
    fun `exception safety - if writer throws barrier gate is reopened and waiting readers proceed`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)
        val writerEntered = CompletableDeferred<Unit>()
        val triggerException = CompletableDeferred<Unit>()
        val readerEntered = CompletableDeferred<Unit>()

        try {
            var writerExceptionThrown = false
            val writerJob = launch {
                try {
                    provider.withExclusiveAccess {
                        writerEntered.complete(Unit)
                        triggerException.await()
                        throw IllegalStateException("Simulated writer failure")
                    }
                } catch (e: IllegalStateException) {
                    if (e.message == "Simulated writer failure") {
                        writerExceptionThrown = true
                    } else {
                        throw e
                    }
                }
            }
            writerEntered.await()

            val readerJob = launch {
                provider.withUserSession {
                    readerEntered.complete(Unit)
                }
            }

            repeat(5) { yield() }
            assertFalse("Reader must be blocked while writer is active", readerEntered.isCompleted)

            triggerException.complete(Unit)
            writerJob.join()

            assertTrue("Writer must have caught its thrown exception", writerExceptionThrown)
            assertTrue("Gate must reopen despite writer exception", provider.barrier.isGateOpen())

            readerEntered.await()
            readerJob.join()
            assertTrue("Waiting reader must complete after writer failed", readerEntered.isCompleted)
        } finally {
            providerJob.cancel()
            triggerException.complete(Unit)
        }
    }

    // 5. Cancellation safety: If the writer is cancelled, the barrier does not remain permanently closed.
    @Test(timeout = 10_000L)
    fun `cancellation safety - canceling writer waiting for readers reopens gate for waiting readers`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val fakeAuth = FakeAuthRepository(AuthUser("test-user", true, null, null))
        val provider = UserSessionProviderImpl(fakeAuth, providerScope)

        var firstReaderJob: Job? = null
        var writerJob: Job? = null
        var secondReaderJob: Job? = null
        val releaseReader = CompletableDeferred<Unit>()

        try {
            withTimeout(5_000L) {
                // Await provider initialization reconciliation to settle so it does not race with the test
                provider.sessionState.first { it is SessionState.Authenticated }

                val readerEntered = CompletableDeferred<Unit>()
                val writerEntered = CompletableDeferred<Unit>()
                val secondReaderEntered = CompletableDeferred<Unit>()

                firstReaderJob = launch {
                    provider.withUserSession {
                        readerEntered.complete(Unit)
                        releaseReader.await()
                    }
                }
                readerEntered.await()

                writerJob = launch {
                    provider.withExclusiveAccess {
                        writerEntered.complete(Unit)
                    }
                }
                while (provider.barrier.isGateOpen()) yield()
                assertFalse(writerEntered.isCompleted)

                secondReaderJob = launch {
                    provider.withUserSession {
                        secondReaderEntered.complete(Unit)
                    }
                }
                repeat(5) { yield() }
                assertFalse("Second reader must be blocked by writer's closed gate", secondReaderEntered.isCompleted)

                writerJob.cancelAndJoin()
                assertTrue("Canceled writer must reopen gate", provider.barrier.isGateOpen())

                releaseReader.complete(Unit)
                firstReaderJob.join()

                secondReaderEntered.await()
                secondReaderJob.join()
                assertTrue("Second reader must proceed after writer cancellation and reader release", secondReaderEntered.isCompleted)
            }
        } finally {
            // Unconditionally cancel providerJob first (non-suspending) to terminate infinite auth flow collection
            providerJob.cancel()
            releaseReader.complete(Unit)
            withContext(NonCancellable) {
                writerJob?.cancelAndJoin()
                firstReaderJob?.cancelAndJoin()
                secondReaderJob?.cancelAndJoin()
            }
        }
    }

    @Test(timeout = 10_000L)
    fun `cancellation safety - canceling writer actively holding exclusive access reopens gate`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)
        val writerEntered = CompletableDeferred<Unit>()
        val readerEntered = CompletableDeferred<Unit>()
        var writerJob: Job? = null
        var readerJob: Job? = null

        try {
            writerJob = launch {
                provider.withExclusiveAccess {
                    writerEntered.complete(Unit)
                    awaitCancellation()
                }
            }
            writerEntered.await()
            assertFalse(provider.barrier.isGateOpen())

            readerJob = launch {
                provider.withUserSession {
                    readerEntered.complete(Unit)
                }
            }
            repeat(5) { yield() }
            assertFalse(readerEntered.isCompleted)

            writerJob.cancelAndJoin()
            assertTrue("Canceling active writer must reopen gate", provider.barrier.isGateOpen())

            readerEntered.await()
            readerJob.join()
            assertTrue(readerEntered.isCompleted)
        } finally {
            providerJob.cancel()
            withContext(NonCancellable) {
                writerJob?.cancelAndJoin()
                readerJob?.cancelAndJoin()
            }
        }
    }

    // 6. Nested-access semantics: Verify nested readers and writers reentrancy, and read-to-write upgrade fails fast.
    @Test(timeout = 10_000L)
    fun `nested access - nested reads, nested writes, and write-to-read are reentrant, read-to-write fails fast`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        val provider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("test-user", true, null, null)), providerScope)

        try {
            // 1. Nested reads are reentrant
            val nestedReadResult = provider.withUserSession { u1 ->
                provider.withUserSession { u2 ->
                    "$u1:$u2"
                }
            }
            assertEquals("test-user:test-user", nestedReadResult)

            // 2. Nested writes are reentrant
            val nestedWriteResult = provider.withExclusiveAccess { u1 ->
                provider.withExclusiveAccess { u2 ->
                    "$u1:$u2"
                }
            }
            assertEquals("test-user:test-user", nestedWriteResult)

            // 3. Reader nested inside writer is reentrant
            val writeThenReadResult = provider.withExclusiveAccess { u1 ->
                provider.withUserSession { u2 ->
                    "$u1:$u2"
                }
            }
            assertEquals("test-user:test-user", writeThenReadResult)

            // 4. Read-to-write upgrade fails fast without deadlocking
            var upgradeFailedFast = false
            provider.withUserSession {
                try {
                    provider.withExclusiveAccess { Unit }
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("Cannot upgrade a session read operation to exclusive reconciliation access") == true) {
                        upgradeFailedFast = true
                    }
                }
            }
            assertTrue("Read-to-write upgrade must fail fast with IllegalStateException", upgradeFailedFast)
            assertTrue(provider.barrier.isGateOpen())
            assertEquals(0, provider.barrier.getActiveReadersCount())
        } finally {
            providerJob.cancel()
        }
    }

    // 7. Session identity: withExclusiveAccess provides current user identity and validates session state.
    @Test(timeout = 10_000L)
    fun `session identity - withExclusiveAccess provides correct userId for authenticated and fallback sessions`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)

        try {
            // Authenticated session
            val authProvider = UserSessionProviderImpl(FakeAuthRepository(AuthUser("firebase-uid-789", false, null, null)), providerScope)
            val authId = authProvider.withExclusiveAccess { userId -> userId }
            assertEquals("firebase-uid-789", authId)

            // OfflineFallback session
            val fallbackProvider = UserSessionProviderImpl(
                authRepository = FakeAuthRepository(null),
                scope = providerScope,
                autoPermitOfflineFallback = true
            )
            val fallbackId = fallbackProvider.withExclusiveAccess { userId -> userId }
            assertEquals(SessionState.DEFAULT_LEGACY_USER_ID, fallbackId)
        } finally {
            providerJob.cancel()
        }
    }

    @Test(timeout = 10_000L)
    fun `session identity - withExclusiveAccess throws when session reconciliation failed`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)

        try {
            val fakeAuth = FakeAuthRepository(AuthUser("target-uid-456", true, null, null))
            val failingReconciler = object : UserIdentityReconciler {
                override suspend fun reconcile(legacyUserId: String, authenticatedUserId: String): Result<ReconciliationResult, Exception> {
                    return Result.Failure(IllegalStateException("Reconciliation network timeout"))
                }
            }
            val provider = UserSessionProviderImpl(
                authRepository = fakeAuth,
                reconciler = failingReconciler,
                scope = providerScope
            )

            // Wait for reconciliation failure to settle
            provider.sessionState.first { it is SessionState.ReconciliationFailed }

            var exceptionCaught = false
            try {
                provider.withExclusiveAccess { userId ->
                    userId
                }
            } catch (e: IllegalStateException) {
                exceptionCaught = true
                assertTrue(e.message?.contains("target-uid-456") == true)
                assertEquals("Reconciliation network timeout", e.cause?.message)
            }
            assertTrue("withExclusiveAccess must throw when session reconciliation failed", exceptionCaught)
        } finally {
            providerJob.cancel()
        }
    }

    @Test(timeout = 10_000L)
    fun `session identity - withExclusiveAccess awaits initialization before executing`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)
        var accessJob: Job? = null

        try {
            val fakeAuth = FakeAuthRepository(null)
            val provider = UserSessionProviderImpl(
                authRepository = fakeAuth,
                scope = providerScope,
                autoPermitOfflineFallback = false
            )
            assertTrue(provider.sessionState.value is SessionState.Initializing)

            val accessStarted = CompletableDeferred<String>()
            accessJob = launch {
                val id = provider.withExclusiveAccess { userId -> userId }
                accessStarted.complete(id)
            }

            repeat(5) { yield() }
            assertFalse("withExclusiveAccess must suspend while Initializing", accessStarted.isCompleted)

            provider.permitOfflineFallback()

            val userId = accessStarted.await()
            assertEquals(SessionState.DEFAULT_LEGACY_USER_ID, userId)
            accessJob.join()
        } finally {
            providerJob.cancel()
            withContext(NonCancellable) {
                accessJob?.cancelAndJoin()
            }
        }
    }

    @Test(timeout = 10_000L)
    fun `extension runWithExclusiveAccess safely coordinates or defaults when provider is null`() = runBlocking {
        val providerJob = SupervisorJob()
        val providerScope = CoroutineScope(coroutineContext + providerJob)

        try {
            val nullProvider: UserSessionProvider? = null
            val nullResult = nullProvider.runWithExclusiveAccess { userId ->
                "fallback-$userId"
            }
            assertEquals("fallback-user-1", nullResult)

            val liveProvider: UserSessionProvider = UserSessionProviderImpl(
                FakeAuthRepository(AuthUser("real-user-id", true, null, null)),
                providerScope
            )
            val liveResult = liveProvider.runWithExclusiveAccess { userId ->
                "live-$userId"
            }
            assertEquals("live-real-user-id", liveResult)
        } finally {
            providerJob.cancel()
        }
    }
}
