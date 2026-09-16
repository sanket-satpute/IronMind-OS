package com.sanket_satpute_20.ironmind.common

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonInfrastructureTest {

    @Test
    fun `TestClock provides deterministic time and can be manipulated`() {
        val clock = TestClock(1000L)
        assertEquals(1000L, clock.currentTimeMillis())

        clock.advanceBy(500L)
        assertEquals(1500L, clock.currentTimeMillis())

        clock.setTime(5000L)
        assertEquals(5000L, clock.currentTimeMillis())
    }

    @Test
    fun `UuidGenerator creates unique UUIDs`() {
        val generator = UuidGenerator()
        val id1 = generator.generateId()
        val id2 = generator.generateId()

        assertNotNull(id1)
        assertTrue(id1.isNotEmpty())
        assertNotEquals(id1, id2)
    }

    @Test
    fun `Result maps Success and Failure correctly`() {
        val successResult: Result<String, Exception> = Result.Success("Data")
        val failureResult: Result<String, Exception> = Result.Failure(IllegalStateException("Error"))

        // Test state
        assertTrue(successResult.isSuccess)
        assertTrue(failureResult.isFailure)
        
        assertEquals("Data", successResult.getOrNull())
        assertEquals(null, failureResult.getOrNull())

        // Test map
        val mappedSuccess = successResult.map { it.length }
        assertEquals(4, mappedSuccess.getOrNull())

        // Test fold
        val successFolded = successResult.fold(
            onSuccess = { it.length },
            onFailure = { -1 }
        )
        assertEquals(4, successFolded)

        val failureFolded = failureResult.fold(
            onSuccess = { it.length },
            onFailure = { -1 }
        )
        assertEquals(-1, failureFolded)
    }
}
