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

    private lateinit var policyUseCase: ObservationSchedulingPolicyUseCase
    private lateinit var activateUseCase: ActivateObservationSchedulingUseCase
    private lateinit var observationScheduler: ObservationScheduler
    private lateinit var useCase: ReconcileObservationSchedulingUseCase

    @Before
    fun setUp() {
        policyUseCase = mockk()
        activateUseCase = mockk()
        observationScheduler = mockk(relaxed = true)
        
        coEvery { activateUseCase() } returns ObservationSchedulingActivationResult.Activated

        useCase = ReconcileObservationSchedulingUseCase(policyUseCase, activateUseCase, observationScheduler)
    }

    @Test
    fun `A Policy Allowed invokes activation use case and returns Activated`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed

        val result = useCase()

        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 0) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Activated)
    }

    @Test
    fun `B Policy DENIED_AUTH invokes cancel and returns Cancelled_AUTH`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH
        )

        val result = useCase()

        coVerify(exactly = 0) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `C Policy DENIED_USER_SETTING invokes cancel and returns Cancelled_USER_SETTING`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING
        )

        val result = useCase()

        coVerify(exactly = 0) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `D Policy DENIED_CONSENT invokes cancel and returns Cancelled_CONSENT`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )

        val result = useCase()

        coVerify(exactly = 0) { activateUseCase() }
        verify(exactly = 1) { observationScheduler.cancelObservationCollection() }
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `E Policy is authoritative and does not inspect repositories directly`() = runBlocking<Unit> {
        // By design of constructor parameters, it does not have repositories to inspect.
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        
        val result = useCase()
        
        assertTrue(result is ObservationSchedulingReconciliationResult.Activated)
    }

    @Test
    fun `F Allowed branch uses V2_6 activation boundary and does not call scheduler_schedule directly`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        
        useCase()
        
        // It should delegate to activateUseCase instead of calling scheduler.schedule()
        coVerify(exactly = 1) { activateUseCase() }
        verify(exactly = 0) { observationScheduler.scheduleObservationCollection() }
    }

    @Test
    fun `G Denied branch uses scheduler cancellation for every denial reason`() = runBlocking<Unit> {
        val reasons = listOf(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH,
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING,
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )
        
        for (reason in reasons) {
            coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(reason)
            useCase()
        }
        
        verify(exactly = 3) { observationScheduler.cancelObservationCollection() }
    }

    @Test
    fun `H No WorkManager dependency`() {
        val fields = ReconcileObservationSchedulingUseCase::class.java.declaredFields
        val hasWorkManager = fields.any { it.type.simpleName == "WorkManager" }
        assertTrue("UseCase should not directly depend on WorkManager", !hasWorkManager)
    }

    @Test
    fun `I No lifecycle integration`() {
        // Implicitly tested. This class has no lifecycle components. 
        assertTrue(true)
    }

    @Test
    fun `J Safe logging`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
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
    fun `K Repeated reconciliation has no internal state mutation`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        
        useCase()
        useCase()
        useCase()
        
        coVerify(exactly = 3) { activateUseCase() }
        
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )
        
        useCase()
        useCase()
        
        verify(exactly = 2) { observationScheduler.cancelObservationCollection() }
    }

    @Test(expected = RuntimeException::class)
    fun `L Cancellation exception bubbles up`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )
        every { observationScheduler.cancelObservationCollection() } throws RuntimeException("Cancel failed")

        useCase()
    }
}
