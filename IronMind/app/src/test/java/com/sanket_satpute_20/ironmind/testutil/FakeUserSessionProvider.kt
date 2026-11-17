package com.sanket_satpute_20.ironmind.testutil

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeUserSessionProvider(initialState: SessionState) : UserSessionProvider {
    private val mutableSessionState = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = mutableSessionState

    private var generation: Long = 1L
    private val deadSessions = mutableSetOf<Pair<String, Long>>()
    private var erasingSession: Pair<String, Long>? = null

    fun setState(state: SessionState) {
        generation++
        mutableSessionState.value = state
    }

    override fun getSessionGeneration(): Long = generation

    override fun getActiveUserId(): String = mutableSessionState.value.currentUserId

    override fun markUserErasing(userId: String, generation: Long) {
        erasingSession = Pair(userId, generation)
    }

    override fun markUserErased(userId: String, generation: Long) {
        erasingSession = null
        deadSessions.add(Pair(userId, generation))
    }

    override fun clearUserErasing(userId: String, generation: Long) {
        if (erasingSession == Pair(userId, generation)) erasingSession = null
    }

    override fun isUserErased(userId: String, generation: Long): Boolean =
        deadSessions.contains(Pair(userId, generation)) || erasingSession == Pair(userId, generation)

    override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T =
        withUserSession(expectedGeneration = generation, action = action)

    override suspend fun <T> withUserSession(
        expectedGeneration: Long,
        action: suspend (userId: String) -> T
    ): T {
        val state = mutableSessionState.value
        check(state !is SessionState.Initializing) { "Session has not initialized" }
        check(state !is SessionState.ReconciliationFailed) { "Session reconciliation failed" }
        check(expectedGeneration > 0L && expectedGeneration == generation) { "Session generation $expectedGeneration has expired (current: $generation)" }
        check(!isUserErased(state.currentUserId, expectedGeneration)) { "Session for user ${state.currentUserId} at generation $expectedGeneration has been erased" }
        return action(state.currentUserId)
    }

    override suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T {
        return withExclusiveAccess(drainTimeoutMs = 0L, action = action)
    }

    override suspend fun <T> withExclusiveAccess(
        drainTimeoutMs: Long,
        action: suspend (userId: String) -> T
    ): T {
        val state = mutableSessionState.value
        check(state !is SessionState.Initializing) { "Session has not initialized" }
        check(state !is SessionState.ReconciliationFailed) { "Session reconciliation failed" }
        return action(state.currentUserId)
    }

    override fun permitOfflineFallback() {
        if (mutableSessionState.value is SessionState.Initializing) {
            mutableSessionState.value = SessionState.OfflineFallback()
        }
    }

    override suspend fun retryReconciliation(): Result<ReconciliationResult, Exception> =
        Result.Failure(IllegalStateException("No reconciliation is pending in this fake"))
}
