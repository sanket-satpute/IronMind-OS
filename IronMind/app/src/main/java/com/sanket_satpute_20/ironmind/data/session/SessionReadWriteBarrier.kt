package com.sanket_satpute_20.ironmind.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Marker element identifying that a coroutine currently holds active read access to the barrier.
 * Used to avoid nested re-entrancy deadlocks when user session operations are chained.
 */
internal class BarrierReadContext : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BarrierReadContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

/**
 * Marker element identifying that a coroutine currently holds exclusive write access to the barrier.
 */
internal class BarrierWriteContext : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BarrierWriteContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

/**
 * Coroutine-safe coordination barrier providing shared read access for user operations
 * and exclusive write access for identity reconciliation.
 *
 * Concurrency & Ordering Invariants:
 * 1. Multiple user operations can execute concurrently with shared access.
 * 2. Reconciliation executes with exclusive access: it blocks new operations and waits
 *    for all in-flight operations to complete before executing.
 * 3. While reconciliation is in progress, any new user operations suspend until reconciliation
 *    finishes, guaranteeing that they receive the reconciled authenticated identity.
 * 4. Deterministic: driven strictly by coroutine suspension without arbitrary sleeps or timeouts.
 * 5. Cancellation safe: active operations always release access in finally blocks using [NonCancellable].
 * 6. Re-entrant: nested calls within the same coroutine context do not deadlock or count double.
 */
class SessionReadWriteBarrier {
    private val stateMutex = Mutex()
    private val reconciliationLock = Mutex()

    private var activeReaders = 0
    private var gate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var drainDeferred: CompletableDeferred<Unit>? = null

    /**
     * Executes [action] with shared access.
     * Suspends if reconciliation is currently in progress.
     * Re-entrant: if already holding read or write access in the current coroutine context,
     * executes immediately without blocking.
     */
    suspend fun <T> withReadAccess(action: suspend () -> T): T {
        // If already holding read or write barrier access, execute directly to avoid nested deadlock
        if (coroutineContext[BarrierReadContext] != null || coroutineContext[BarrierWriteContext] != null) {
            return action()
        }

        while (true) {
            val gateToWait = stateMutex.withLock {
                if (gate.isCompleted) {
                    activeReaders++
                    null
                } else {
                    gate
                }
            }
            if (gateToWait == null) {
                break
            }
            gateToWait.await()
        }

        try {
            return withContext(BarrierReadContext()) {
                action()
            }
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    activeReaders--
                    if (activeReaders == 0) {
                        drainDeferred?.complete(Unit)
                    }
                }
            }
        }
    }

    /**
     * Executes [action] with exclusive write access for reconciliation or erasure.
     * Closes the gate to new operations, waits for all active operations to complete (optionally bounded
     * by [drainTimeoutMs]), executes [action], and then re-opens the gate.
     */
    suspend fun <T> withWriteAccess(
        drainTimeoutMs: Long = 0L,
        action: suspend () -> T
    ): T {
        // A writer already owns exclusivity, so nested writes are safe to execute inline.
        if (coroutineContext[BarrierWriteContext] != null) {
            return action()
        }

        // A read-to-write upgrade cannot wait for other readers while this coroutine itself
        // contributes to activeReaders. Fail fast instead of deadlocking.
        check(coroutineContext[BarrierReadContext] == null) {
            "Cannot upgrade a session read operation to exclusive reconciliation access."
        }

        return reconciliationLock.withLock {
            var waitDrain: CompletableDeferred<Unit>? = null
            stateMutex.withLock {
                // Close the gate for incoming operations
                gate = CompletableDeferred()
                if (activeReaders > 0) {
                    waitDrain = CompletableDeferred()
                    drainDeferred = waitDrain
                }
            }

            try {
                // Wait for all in-flight operations to finish, optionally bounded by drainTimeoutMs
                if (drainTimeoutMs > 0L && waitDrain != null) {
                    kotlinx.coroutines.withTimeout(drainTimeoutMs) {
                        waitDrain?.await()
                    }
                } else {
                    waitDrain?.await()
                }
                withContext(BarrierWriteContext()) {
                    action()
                }
            } finally {
                withContext(NonCancellable) {
                    stateMutex.withLock {
                        drainDeferred = null
                        // Re-open gate for waiting operations
                        gate.complete(Unit)
                    }
                }
            }
        }
    }

    suspend fun getActiveReadersCount(): Int = stateMutex.withLock { activeReaders }
    suspend fun isGateOpen(): Boolean = stateMutex.withLock { gate.isCompleted }
}
