package com.sanket_satpute_20.ironmind.data.session

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.auth.SignInAnonymouslyUseCase
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Default implementation of [UserSessionProvider] backed by [AuthRepository] and coordinated
 * via [SessionReadWriteBarrier].
 *
 * Responsibilities:
 * - Distinguishes Initializing, Authenticated, and OfflineFallback lifecycle states.
 * - Coordinates identity-sensitive writes with reconciliation via [withUserSession].
 * - Immediately resolves cached authentication if present on cold start and reconciles legacy data.
 * - Defaults safely to [SessionState.OfflineFallback] when unauthenticated or offline.
 * - Automatically triggers silent background anonymous authentication if no cached user exists.
 * - Never treats offline fallback or network timeout as permanent: reactively transitions
 *   to [SessionState.Authenticated] as soon as Firebase credentials become available.
 * - Provides retryable, idempotent reconciliation with atomic rollback on failure.
 * - Schedules background sync only after identity reconciliation successfully commits.
 */
class UserSessionProviderImpl(
    private val authRepository: AuthRepository,
    private val reconciler: UserIdentityReconciler? = null,
    internal val scope: CoroutineScope,
    private val autoPermitOfflineFallback: Boolean = false,
    internal val barrier: SessionReadWriteBarrier = SessionReadWriteBarrier(),
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase? = null,
    private val backgroundExecutor: BackgroundExecutor? = null,
    private val appSessionStateDao: com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao? = null,
    private val syncWorkerLeaseDao: com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao? = null
) : UserSessionProvider {

    constructor(
        authRepository: AuthRepository,
        scope: CoroutineScope
    ) : this(
        authRepository = authRepository,
        reconciler = null,
        scope = scope,
        autoPermitOfflineFallback = true,
        barrier = SessionReadWriteBarrier(),
        signInAnonymouslyUseCase = null,
        backgroundExecutor = null,
        appSessionStateDao = null,
        syncWorkerLeaseDao = null
    )

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val isSigningIn = AtomicBoolean(false)
    private val currentSessionGeneration = java.util.concurrent.atomic.AtomicLong(1L)
    private val deadSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<String, Long>>()
    private val erasingSession = java.util.concurrent.atomic.AtomicReference<Pair<String, Long>?>(null)

    override fun getSessionGeneration(): Long = currentSessionGeneration.get()

    override fun markUserErasing(userId: String, generation: Long) {
        erasingSession.set(Pair(userId, generation))
    }

    override fun markUserErased(userId: String, generation: Long) {
        erasingSession.compareAndSet(Pair(userId, generation), null)
        deadSessions.add(Pair(userId, generation))
    }

    override fun clearUserErasing(userId: String, generation: Long) {
        erasingSession.compareAndSet(Pair(userId, generation), null)
    }

    override fun isUserErased(userId: String, generation: Long): Boolean {
        return deadSessions.contains(Pair(userId, generation)) ||
               erasingSession.get() == Pair(userId, generation)
    }

    private fun recordSessionAdvance(newUserId: String) {
        val nextGen = currentSessionGeneration.incrementAndGet()
        val now = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                appSessionStateDao?.setSessionState(
                    com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity(
                        id = 1,
                        activeUserId = newUserId,
                        currentGeneration = nextGen,
                        updatedAt = now
                    )
                )
                syncWorkerLeaseDao?.revokeLeasesForOlderGenerations(nextGen, now)
            } catch (e: Exception) {
                println("IronMindLifecycle [SessionState] [ADVANCE_PERSIST_FAILED] ${e.message}")
            }
        }
    }

    init {
        // Step 0: Restore authoritative persisted generation if present, or seed initial row
        scope.launch(Dispatchers.IO) {
            try {
                val persistedGen = appSessionStateDao?.getCurrentGeneration()
                if (persistedGen != null && persistedGen > 0L) {
                    currentSessionGeneration.set(persistedGen)
                } else {
                    val initialUid = authRepository.getCurrentUser()?.id ?: SessionState.DEFAULT_LEGACY_USER_ID
                    appSessionStateDao?.setSessionState(
                        com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity(
                            id = 1,
                            activeUserId = initialUid,
                            currentGeneration = currentSessionGeneration.get(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                println("IronMindLifecycle [SessionState] [INIT_PERSIST_FAILED] ${e.message}")
            }
        }

        // Step 1: Synchronous check for cached user on cold start
        val current = authRepository.getCurrentUser()
        if (current != null && current.id.isNotBlank()) {
            scope.launch {
                executeReconciliation(current.id, current.isAnonymous)
            }
        } else {
            if (autoPermitOfflineFallback) {
                _sessionState.value = SessionState.OfflineFallback()
            }
            bootstrapAuthentication()
        }

        // Step 2: Continuous reactive stream subscription
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null && user.id.isNotBlank()) {
                    executeReconciliation(user.id, user.isAnonymous)
                } else {
                    barrier.withWriteAccess {
                        val state = _sessionState.value
                        if (state is SessionState.Initializing) {
                            if (autoPermitOfflineFallback) {
                                _sessionState.value = SessionState.OfflineFallback()
                            }
                        } else if (state !is SessionState.OfflineFallback) {
                            _sessionState.value = SessionState.OfflineFallback()
                            recordSessionAdvance(SessionState.DEFAULT_LEGACY_USER_ID)
                        }
                    }
                }
            }
        }
    }

    /**
     * Silently triggers anonymous authentication in the background if no user is authenticated.
     * Idempotent and thread-safe: guarantees exactly one in-flight sign-in attempt.
     */
    internal fun bootstrapAuthentication(): Job? {
        val signInUseCase = signInAnonymouslyUseCase ?: return null
        val current = authRepository.getCurrentUser()
        if (current != null && current.id.isNotBlank()) {
            return null
        }
        if (!isSigningIn.compareAndSet(false, true)) {
            return null
        }

        return scope.launch {
            try {
                val result = signInUseCase()
                when (result) {
                    is Result.Success -> {
                        println("IronMindLifecycle [Session] [SILENT_AUTH_SUCCESS] uid=${result.data.id}")
                        executeReconciliation(result.data.id, result.data.isAnonymous)
                    }
                    is Result.Failure -> {
                        println("IronMindLifecycle [Session] [SILENT_AUTH_FAILED] reason=${result.error.javaClass.simpleName}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Session] [SILENT_AUTH_EXCEPTION] reason=${e.javaClass.simpleName}")
            } finally {
                isSigningIn.set(false)
            }
        }
    }

    override fun getActiveUserId(): String = _sessionState.value.currentUserId

    override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T {
        return withUserSession(expectedGeneration = null, action = action)
    }

    override suspend fun <T> withUserSession(
        expectedGeneration: Long,
        action: suspend (userId: String) -> T
    ): T {
        return withUserSession(expectedGeneration = expectedGeneration as Long?, action = action)
    }

    private suspend fun <T> withUserSession(
        expectedGeneration: Long?,
        action: suspend (userId: String) -> T
    ): T {
        // Await resolution if currently in Initializing state before entering barrier
        if (_sessionState.value is SessionState.Initializing) {
            _sessionState.first { it !is SessionState.Initializing }
        }

        val currentState = _sessionState.value
        if (currentState is SessionState.ReconciliationFailed) {
            throw IllegalStateException(
                "Cannot perform user session operation: identity reconciliation failed for user ${currentState.targetUserId}.",
                currentState.error
            )
        }

        val currentGen = currentSessionGeneration.get()
        if (expectedGeneration != null && (expectedGeneration <= 0L || expectedGeneration != currentGen)) {
            throw IllegalStateException(
                "Cannot perform user session operation: session generation $expectedGeneration has expired or is invalid (current generation: $currentGen)."
            )
        }

        val checkGen = expectedGeneration ?: currentGen
        if (isUserErased(currentState.currentUserId, checkGen)) {
            throw IllegalStateException(
                "Cannot perform user session operation: user ${currentState.currentUserId} at generation $checkGen has been erased or is undergoing erasure."
            )
        }

        return barrier.withReadAccess {
            val state = _sessionState.value
            if (state is SessionState.ReconciliationFailed) {
                throw IllegalStateException(
                    "Cannot perform user session operation: identity reconciliation failed for user ${state.targetUserId}.",
                    state.error
                )
            }
            val lockedGen = currentSessionGeneration.get()
            if (expectedGeneration != null && (expectedGeneration <= 0L || expectedGeneration != lockedGen)) {
                throw IllegalStateException(
                    "Cannot perform user session operation: session generation $expectedGeneration has expired or is invalid (current generation: $lockedGen)."
                )
            }
            val lockedCheckGen = expectedGeneration ?: lockedGen
            if (isUserErased(state.currentUserId, lockedCheckGen)) {
                throw IllegalStateException(
                    "Cannot perform user session operation: user ${state.currentUserId} at generation $lockedCheckGen has been erased or is undergoing erasure."
                )
            }
            action(state.currentUserId)
        }
    }

    override suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T {
        return withExclusiveAccess(drainTimeoutMs = 0L, action = action)
    }

    override suspend fun <T> withExclusiveAccess(
        drainTimeoutMs: Long,
        action: suspend (userId: String) -> T
    ): T {
        // Await resolution if currently in Initializing state before entering barrier
        if (_sessionState.value is SessionState.Initializing) {
            _sessionState.first { it !is SessionState.Initializing }
        }

        val currentState = _sessionState.value
        if (currentState is SessionState.ReconciliationFailed) {
            throw IllegalStateException(
                "Cannot perform user session operation: identity reconciliation failed for user ${currentState.targetUserId}.",
                currentState.error
            )
        }

        return barrier.withWriteAccess(drainTimeoutMs = drainTimeoutMs) {
            val state = _sessionState.value
            if (state is SessionState.ReconciliationFailed) {
                throw IllegalStateException(
                    "Cannot perform user session operation: identity reconciliation failed for user ${state.targetUserId}.",
                    state.error
                )
            }
            action(state.currentUserId)
        }
    }

    override fun permitOfflineFallback() {
        if (_sessionState.value is SessionState.Initializing) {
            scope.launch {
                barrier.withWriteAccess {
                    if (_sessionState.value is SessionState.Initializing) {
                        _sessionState.value = SessionState.OfflineFallback()
                    }
                }
            }
        }
    }

    override suspend fun retryReconciliation(): Result<ReconciliationResult, Exception> {
        var reconciliationSucceeded = false
        val finalResult = barrier.withWriteAccess {
            val state = _sessionState.value
            val targetUserId = when (state) {
                is SessionState.ReconciliationFailed -> state.targetUserId
                is SessionState.Authenticated -> state.userId
                else -> return@withWriteAccess Result.Failure(
                    IllegalStateException("No pending reconciliation to retry for state $state")
                )
            }
            val result = try {
                reconciler?.reconcile(
                    legacyUserId = SessionState.DEFAULT_LEGACY_USER_ID,
                    authenticatedUserId = targetUserId
                ) ?: Result.Success(ReconciliationResult(true, 0, "No reconciler configured"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure(e)
            }
            when (result) {
                is Result.Success -> {
                    val anonymous = (state as? SessionState.Authenticated)?.isAnonymous ?: true
                    _sessionState.value = SessionState.Authenticated(targetUserId, isAnonymous = anonymous)
                    recordSessionAdvance(targetUserId)
                    reconciliationSucceeded = true
                    result
                }
                is Result.Failure -> {
                    _sessionState.value = SessionState.ReconciliationFailed(result.error, targetUserId)
                    result
                }
            }
        }

        if (reconciliationSucceeded) {
            try {
                backgroundExecutor?.scheduleSync()
                backgroundExecutor?.scheduleEventReconciliation()
                backgroundExecutor?.scheduleObservationReconciliation()
                backgroundExecutor?.schedulePatternMaintenance()
                backgroundExecutor?.schedulePeriodicInterventionEvaluation()
                backgroundExecutor?.scheduleInterventionEvaluation()
                println("IronMindLifecycle [Session] [POST_RECONCILIATION_WORK_SCHEDULED]")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Session] [POST_RECONCILIATION_WORK_SCHEDULE_FAILED] error=${e.message}")
            }
        }
        return finalResult
    }

    private suspend fun executeReconciliation(targetUserId: String, isAnonymous: Boolean) {
        val currentState = _sessionState.value
        if (currentState is SessionState.Authenticated && currentState.userId == targetUserId) {
            return
        }

        var reconciliationSucceeded = false
        barrier.withWriteAccess {
            val lockedState = _sessionState.value
            if (lockedState is SessionState.Authenticated && lockedState.userId == targetUserId) {
                return@withWriteAccess
            }
            val result = try {
                reconciler?.reconcile(
                    legacyUserId = SessionState.DEFAULT_LEGACY_USER_ID,
                    authenticatedUserId = targetUserId
                ) ?: Result.Success(ReconciliationResult(true, 0, "No reconciler configured"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure(e)
            }
            when (result) {
                is Result.Success -> {
                    _sessionState.value = SessionState.Authenticated(targetUserId, isAnonymous)
                    recordSessionAdvance(targetUserId)
                    reconciliationSucceeded = true
                }
                is Result.Failure -> {
                    _sessionState.value = SessionState.ReconciliationFailed(result.error, targetUserId)
                }
            }
        }

        if (reconciliationSucceeded) {
            try {
                backgroundExecutor?.scheduleSync()
                backgroundExecutor?.scheduleEventReconciliation()
                backgroundExecutor?.scheduleObservationReconciliation()
                backgroundExecutor?.schedulePatternMaintenance()
                backgroundExecutor?.schedulePeriodicInterventionEvaluation()
                backgroundExecutor?.scheduleInterventionEvaluation()
                println("IronMindLifecycle [Session] [POST_RECONCILIATION_WORK_SCHEDULED]")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Session] [POST_RECONCILIATION_WORK_SCHEDULE_FAILED] error=${e.message}")
            }
        }
    }
}
