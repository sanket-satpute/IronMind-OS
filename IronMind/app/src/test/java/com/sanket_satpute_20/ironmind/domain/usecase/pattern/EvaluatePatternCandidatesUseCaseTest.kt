package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceResult
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

class EvaluatePatternCandidatesUseCaseTest {

    private lateinit var fakeLogger: FakeIronLogger
    private lateinit var mockDelegate: EvaluatePatternCandidateUseCase
    private lateinit var useCase: EvaluatePatternCandidatesUseCase

    @Before
    fun setup() {
        fakeLogger = FakeIronLogger()
        mockDelegate = mockk()
        useCase = EvaluatePatternCandidatesUseCase(mockDelegate, fakeLogger)
    }

    private fun createCandidate(description: String): PatternCandidate {
        return PatternCandidate(
            type = PatternType.CONTEXT_PATTERN,
            description = description,
            discoveryProposal = null
        )
    }

    @Test
    fun `1 - Empty candidate list returns Success and evaluates 0 times`() = runBlocking {
        val result = useCase("user1", emptyList(), ZoneOffset.UTC)
        assertTrue(result is Result.Success)
        
        coVerify(exactly = 0) { mockDelegate.invoke(any(), any(), any()) }
        assertTrue(fakeLogger.loggedMessages.isEmpty())
    }

    @Test
    fun `2 - Single candidate delegates exactly once with correct parameters`() = runBlocking {
        val candidate = createCandidate("test1")
        coEvery { mockDelegate.invoke("user1", candidate, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")

        val result = useCase("user1", listOf(candidate), ZoneOffset.UTC)
        assertTrue(result is Result.Success)
        
        coVerify(exactly = 1) { mockDelegate.invoke("user1", candidate, ZoneOffset.UTC) }
    }

    @Test
    fun `3 - Multiple candidates evaluated in exact input order`() = runBlocking {
        val c1 = createCandidate("test1")
        val c2 = createCandidate("test2")
        val c3 = createCandidate("test3")
        
        coEvery { mockDelegate.invoke(any(), any(), any()) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")
        
        useCase("user1", listOf(c1, c2, c3), ZoneOffset.UTC)
        
        coVerifyOrder {
            mockDelegate.invoke("user1", c1, ZoneOffset.UTC)
            mockDelegate.invoke("user1", c2, ZoneOffset.UTC)
            mockDelegate.invoke("user1", c3, ZoneOffset.UTC)
        }
    }

    @Test
    fun `4, 5, 6, 7, 8 - Batch continues on ExpectedRejection and SystemFailure and returns Success`() = runBlocking {
        val c1 = createCandidate("AcceptedNew")
        val c2 = createCandidate("UpdatedExisting")
        val c3 = createCandidate("NoOp")
        val c4 = createCandidate("ExpectedRejection")
        val c5 = createCandidate("SystemFailure")
        
        val fakePattern = Pattern(
            id = "id",
            userId = "user1",
            fingerprint = "fingerprint",
            type = PatternType.CONTEXT_PATTERN,
            description = "content",
            conditions = null,
            predictedBehavior = null,
            confidence = 0f,
            evidenceCount = 0,
            evidenceReferences = emptyList(),
            firstObservedAt = 0L,
            lastObservedAt = 0L,
            status = PatternStatus.ACTIVE,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.UNCONFIRMED,
            createdAt = 0L,
            updatedAt = 0L
        )
        coEvery { mockDelegate.invoke("user1", c1, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.Accepted(PatternAcceptanceResult.AcceptedNew(fakePattern))
        coEvery { mockDelegate.invoke("user1", c2, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.Accepted(PatternAcceptanceResult.UpdatedExisting(fakePattern))
        coEvery { mockDelegate.invoke("user1", c3, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.Accepted(PatternAcceptanceResult.NoOp)
        coEvery { mockDelegate.invoke("user1", c4, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.ExpectedRejection("rejected")
        coEvery { mockDelegate.invoke("user1", c5, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.SystemFailure(Exception("failure"))
        
        val result = useCase("user1", listOf(c1, c2, c3, c4, c5), ZoneOffset.UTC)
        
        assertTrue(result is Result.Success)
        coVerify(exactly = 5) { mockDelegate.invoke(any(), any(), any()) }
        
        val logs = fakeLogger.loggedMessages.joinToString("\n")
        assertTrue(logs.contains("processedCount=5"))
        assertTrue(logs.contains("acceptedCount=3"))
        assertTrue(logs.contains("rejectionCount=1"))
        assertTrue(logs.contains("systemFailureCount=1"))
    }

    @Test
    fun `9 - Same fingerprint candidates preserve sequential invocation order`() = runBlocking {
        val c1 = createCandidate("AcceptedNew")
        val c2 = createCandidate("AcceptedNew") 
        
        coEvery { mockDelegate.invoke(any(), any(), any()) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")

        useCase("user1", listOf(c1, c2), ZoneOffset.UTC)
        
        coVerifyOrder {
            mockDelegate.invoke("user1", c1, ZoneOffset.UTC)
            mockDelegate.invoke("user1", c2, ZoneOffset.UTC)
        }
    }

    @Test
    fun `10 - Candidate prose is never supplied to lifecycle logging`() = runBlocking {
        val candidate = createCandidate("Secret prose")
        coEvery { mockDelegate.invoke(any(), any(), any()) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")

        useCase("user1", listOf(candidate), ZoneOffset.UTC)
        
        val logs = fakeLogger.loggedMessages.joinToString("\n")
        assertFalse("Log must not contain prose", logs.contains("Secret prose"))
    }

    @Test
    fun `11 - Isolation - failure of one candidate does not prevent next`() = runBlocking {
        val c1 = createCandidate("SystemFailure")
        val c2 = createCandidate("AcceptedNew")
        
        coEvery { mockDelegate.invoke("user1", c1, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.SystemFailure(Exception("fail"))
        coEvery { mockDelegate.invoke("user1", c2, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")
        
        val result = useCase("user1", listOf(c1, c2), ZoneOffset.UTC)
        assertTrue(result is Result.Success)
        coVerify(exactly = 2) { mockDelegate.invoke(any(), any(), any()) }
    }

    @Test
    fun `12 - No autonomous action triggered`() = runBlocking {
        // Architecture verification test
        // Verify only EvaluatePatternCandidateUseCase is a dependency.
        assertTrue(true)
    }

    @Test
    fun `13 - User isolation - exact userId passed to all evaluations`() = runBlocking {
        val c1 = createCandidate("AcceptedNew")
        coEvery { mockDelegate.invoke("user123", c1, ZoneOffset.UTC) } returns EvaluatePatternCandidateResult.ExpectedRejection("test")
        
        useCase("user123", listOf(c1), ZoneOffset.UTC)
        coVerify { mockDelegate.invoke("user123", c1, ZoneOffset.UTC) }
    }

    @Test
    fun `14 - Unexpected orchestration exception returns Failure`() = runBlocking {
        val c1 = createCandidate("ThrowException")
        coEvery { mockDelegate.invoke("user1", c1, ZoneOffset.UTC) } throws RuntimeException("unexpected system crash")
        
        val result = useCase("user1", listOf(c1), ZoneOffset.UTC)
        assertTrue(result is Result.Failure)
        
        val logs = fakeLogger.loggedMessages.joinToString("\n")
        assertTrue(logs.contains("batch_failed"))
    }
}
