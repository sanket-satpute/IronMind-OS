package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ReconcileObservationSchedulingUseCaseTest {

    private lateinit var activateUseCase: ActivateObservationSchedulingUseCase
    private lateinit var observationScheduler: ObservationScheduler
    private lateinit var useCase: ReconcileObservationSchedulingUseCase

    @Before
    fun setUp() {
        activateUseCase = mockk()
        observationScheduler = mockk(relaxed = true)

        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Activated
        every { observationScheduler.cancelObservationCollection() } returns com.sanket_satpute_20.ironmind.domain.common.Result.Success(Unit)

        useCase = ReconcileObservationSchedulingUseCase(activateUseCase, observationScheduler)
    }

    @Test
    fun `A Activation Activated returns Activated`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Activated

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 0) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Activated)
    }

    @Test
    fun `B Activation Denied_AUTH invokes cancel and returns Cancelled_AUTH`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH
        )

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `C Activation Denied_USER_SETTING invokes cancel and returns Cancelled_USER_SETTING`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING
        )

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `D Activation Denied_CONSENT invokes cancel and returns Cancelled_CONSENT`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `F Activation ScheduleFailed returns Failed`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.ScheduleFailed

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 0) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Failed)
    }

    @Test
    fun `G No WorkManager dependency`() {
        val fields = ReconcileObservationSchedulingUseCase::class.java.declaredFields
        val hasWorkManager = fields.any { it.type.simpleName == "WorkManager" }
        assertTrue("UseCase should not directly depend on WorkManager", !hasWorkManager)
    }

    @Test
    fun `H Safe logging for Cancelled`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING
        )

        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            useCase()

            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle [ObservationSchedulingReconciliation] [CANCELLED] reason=DENIED_USER_SETTING"))
            assertTrue(!logs.contains("user-"))
            assertTrue(!logs.contains("payload"))

        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `I Cancellation exception behavior returns Failed`() = runBlocking<Unit> {
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )
        every { observationScheduler.cancelObservationCollection() } returns com.sanket_satpute_20.ironmind.domain.common.Result.Failure(com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationSchedulingError.CancelFailed)

        val result = useCase()

        assertTrue(result is ObservationSchedulingReconciliationResult.Failed)
    }
}
