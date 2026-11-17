package com.sanket_satpute_20.ironmind.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReadWriteBarrierTest {

    @Test
    fun `nested reads and nested writes are reentrant and read to write upgrade fails fast`() = runBlocking {
        val barrier = SessionReadWriteBarrier()

        val nestedRead = barrier.withReadAccess {
            barrier.withReadAccess { "read" }
        }
        assertEquals("read", nestedRead)

        val nestedWrite = barrier.withWriteAccess {
            barrier.withWriteAccess { "write" }
        }
        assertEquals("write", nestedWrite)

        var rejectedUpgrade = false
        barrier.withReadAccess {
            try {
                barrier.withWriteAccess { Unit }
            } catch (expected: IllegalStateException) {
                rejectedUpgrade = true
            }
        }
        assertTrue("An upgrade must not wait for the reader that it owns", rejectedUpgrade)
        assertTrue(barrier.isGateOpen())
        assertEquals(0, barrier.getActiveReadersCount())
    }

    @Test
    fun `canceling writer while it waits for active readers reopens gate`() = runBlocking {
        val barrier = SessionReadWriteBarrier()
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()
        val reader = launch {
            barrier.withReadAccess {
                readerEntered.complete(Unit)
                releaseReader.await()
            }
        }
        readerEntered.await()

        val writerEntered = CompletableDeferred<Unit>()
        val writer: Job = launch {
            barrier.withWriteAccess { writerEntered.complete(Unit) }
        }
        while (barrier.isGateOpen()) yield()
        assertFalse(writerEntered.isCompleted)

        writer.cancelAndJoin()
        assertTrue("Canceled writer must release the closed gate", barrier.isGateOpen())
        assertEquals("Reader still owns its access until released", 1, barrier.getActiveReadersCount())

        releaseReader.complete(Unit)
        reader.join()
        assertEquals(0, barrier.getActiveReadersCount())
    }

    @Test
    fun `canceling while holding read or write access releases barrier state`() = runBlocking {
        val barrier = SessionReadWriteBarrier()
        val readEntered = CompletableDeferred<Unit>()
        val readJob = launch {
            barrier.withReadAccess {
                readEntered.complete(Unit)
                awaitCancellation()
            }
        }
        readEntered.await()
        assertEquals(1, barrier.getActiveReadersCount())
        readJob.cancelAndJoin()
        assertEquals(0, barrier.getActiveReadersCount())

        val writeEntered = CompletableDeferred<Unit>()
        val writeJob = launch {
            barrier.withWriteAccess {
                writeEntered.complete(Unit)
                awaitCancellation()
            }
        }
        writeEntered.await()
        assertFalse(barrier.isGateOpen())
        writeJob.cancelAndJoin()
        assertTrue("Canceled writer must reopen gate", barrier.isGateOpen())
        assertEquals(0, barrier.getActiveReadersCount())
    }

    @Test
    fun `exceptions release reader count and reopen writer gate`() = runBlocking {
        val barrier = SessionReadWriteBarrier()

        try {
            barrier.withReadAccess<Unit> { error("read failure") }
        } catch (_: IllegalStateException) {
            // Expected: the operation failed after taking read access.
        }
        assertEquals(0, barrier.getActiveReadersCount())

        try {
            barrier.withWriteAccess<Unit> { error("write failure") }
        } catch (_: IllegalStateException) {
            // Expected: the operation failed after closing the writer gate.
        }
        assertTrue(barrier.isGateOpen())
    }

    @Test
    fun `drain timeout in withWriteAccess fails closed and reopens gate`() = runBlocking {
        val barrier = SessionReadWriteBarrier()
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()

        val reader = launch {
            barrier.withReadAccess {
                readerEntered.complete(Unit)
                releaseReader.await()
            }
        }
        readerEntered.await()
        assertEquals(1, barrier.getActiveReadersCount())

        var timedOut = false
        var executedAction = false
        try {
            barrier.withWriteAccess(drainTimeoutMs = 100L) {
                executedAction = true
            }
        } catch (expected: kotlinx.coroutines.TimeoutCancellationException) {
            timedOut = true
        }

        assertTrue("Writer must throw TimeoutCancellationException when drain times out", timedOut)
        assertFalse("Writer action must NOT execute when drain times out", executedAction)
        assertTrue("Gate must reopen after timeout", barrier.isGateOpen())

        // Clean up reader
        releaseReader.complete(Unit)
        reader.join()
        assertEquals(0, barrier.getActiveReadersCount())
    }
}
