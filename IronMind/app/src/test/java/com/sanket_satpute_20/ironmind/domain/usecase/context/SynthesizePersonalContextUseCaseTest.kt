package com.sanket_satpute_20.ironmind.domain.usecase.context

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthesizePersonalContextUseCaseTest {

    private val contextEngine: ContextEngine = mockk()
    private val ironMindAI: IronMindAI = mockk()
    
    private val useCase = SynthesizePersonalContextUseCase(contextEngine, ironMindAI)
    private val userId = "user-123"

    @Test
    fun `AI hypothesis is mapped to non-authoritative inferredIntentHypothesis and leaves factual context unchanged`() = runBlocking {
        // Arrange
        val timestamp = 1000L
        val snapshot = mockk<ContextSnapshot> {
            io.mockk.every { this@mockk.timestamp } returns timestamp
        }
        
        coEvery { contextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)
        
        val aiOutput = AIOutput.ContextSynthesis(
            inferredIntentHypothesis = "I think the user wants to sleep",
            currentEnvironment = "Home",
            recentBehavior = "Scrolling",
            relevantPatterns = "Late night phone",
            synthesizedSummary = "User is tired",
            confidence = 0.8f,
            schemaVersion = 1
        )
        
        coEvery { ironMindAI.process(any()) } returns Result.Success(aiOutput)

        // Act
        val result = useCase(userId)

        // Assert
        assertTrue(result is Result.Success)
        val personalContext = (result as Result.Success).data
        
        // 1. AI hypothesis is mapped to the new non-authoritative field
        assertEquals("I think the user wants to sleep", personalContext.inferredIntentHypothesis)
        
        // 5. Factual context/timestamp remains unchanged
        assertEquals(timestamp, personalContext.timestamp)
        
        // Ensure other fields mapped correctly
        assertEquals("Home", personalContext.currentEnvironment)
        assertEquals("Scrolling", personalContext.recentBehavior)
        assertEquals("Late night phone", personalContext.relevantPatterns)
        assertEquals("User is tired", personalContext.synthesizedSummary)
        
        // 2 & 6: The data model for PersonalContext does not contain authoritative explicit intent fields to be overwritten.
    }

    @Test
    fun `Null AI hypothesis remains null`() = runBlocking {
        // Arrange
        val snapshot = mockk<ContextSnapshot> {
            io.mockk.every { timestamp } returns 1000L
        }
        coEvery { contextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)
        
        val aiOutput = AIOutput.ContextSynthesis(
            inferredIntentHypothesis = null,
            currentEnvironment = null,
            recentBehavior = null,
            relevantPatterns = null,
            synthesizedSummary = "Minimal synthesis",
            confidence = 0.8f,
            schemaVersion = 1
        )
        coEvery { ironMindAI.process(any()) } returns Result.Success(aiOutput)

        // Act
        val result = useCase(userId)

        // Assert
        assertTrue(result is Result.Success)
        val personalContext = (result as Result.Success).data
        
        // 3. Null AI hypothesis remains null
        assertNull(personalContext.inferredIntentHypothesis)
    }

    @Test
    fun `AI processing failure propagates correctly`() = runBlocking {
        // Arrange
        val snapshot = mockk<ContextSnapshot> {
            io.mockk.every { timestamp } returns 1000L
        }
        coEvery { contextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)
        
        val exception = RuntimeException("AI provider down")
        coEvery { ironMindAI.process(any()) } returns Result.Failure(exception)

        // Act
        val result = useCase(userId)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals(exception, (result as Result.Failure).error)
    }

    @Test
    fun `Context Engine failure propagates correctly`() = runBlocking {
        // Arrange
        val exception = RuntimeException("DB error")
        coEvery { contextEngine.getCurrentContext(userId) } returns Result.Failure(exception)

        // Act
        val result = useCase(userId)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals(exception, (result as Result.Failure).error)
    }

    @Test
    fun `Resulting model semantics remain deterministic for the same inputs`() = runBlocking {
        // Arrange
        val timestamp = 2000L
        val snapshot = mockk<ContextSnapshot> {
            io.mockk.every { this@mockk.timestamp } returns timestamp
        }
        
        coEvery { contextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)
        
        val aiOutput = AIOutput.ContextSynthesis(
            inferredIntentHypothesis = "Consistent hypothesis",
            currentEnvironment = "Consistent env",
            recentBehavior = "Consistent behavior",
            relevantPatterns = "Consistent pattern",
            synthesizedSummary = "Consistent summary",
            confidence = 0.9f,
            schemaVersion = 1
        )
        
        coEvery { ironMindAI.process(any()) } returns Result.Success(aiOutput)

        // Act
        val result1 = useCase(userId)
        val result2 = useCase(userId)

        // Assert
        assertTrue(result1 is Result.Success)
        assertTrue(result2 is Result.Success)
        
        val pc1 = (result1 as Result.Success).data
        val pc2 = (result2 as Result.Success).data
        
        // 7. Deterministic semantics
        assertEquals(pc1, pc2)
    }
}
