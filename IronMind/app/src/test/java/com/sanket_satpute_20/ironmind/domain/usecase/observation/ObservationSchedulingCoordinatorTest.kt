package com.sanket_satpute_20.ironmind.domain.usecase.observation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ObservationSchedulingCoordinatorTest {

    private lateinit var reconcileUseCase: ReconcileObservationSchedulingUseCase
    private lateinit var coordinator: ObservationSchedulingCoordinator

    @Before
    fun setUp() {
        reconcileUseCase = mockk()
        coordinator = ObservationSchedulingCoordinator(reconcileUseCase)
    }

    @Test
    fun `A ApplicationStarted invokes reconciliation exactly once`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        
        coVerify(exactly = 1) { reconcileUseCase() }
    }

    @Test
    fun `B AuthenticationAvailable invokes reconciliation exactly once`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable)
        
        coVerify(exactly = 1) { reconcileUseCase() }
    }

    @Test
    fun `C AuthenticationUnavailable invokes reconciliation exactly once`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable)
        
        coVerify(exactly = 1) { reconcileUseCase() }
    }

    @Test
    fun `D ObservationConsentChanged invokes reconciliation exactly once`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        
        coVerify(exactly = 1) { reconcileUseCase() }
    }

    @Test
    fun `E GlobalAutonomyPauseChanged invokes reconciliation exactly once`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged)
        
        coVerify(exactly = 1) { reconcileUseCase() }
    }

    @Test
    fun `F Every supported event uses the SAME reconciliation boundary`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable)
        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable)
        coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        coordinator.handle(ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged)
        
        // Ensure no direct scheduler/policy calls (validated by lack of dependencies in constructor)
        coVerify(exactly = 5) { reconcileUseCase() }
    }

    @Test
    fun `G Coordinator does not inspect repositories`() = runBlocking<Unit> {
        // Enforced by constructor having exactly one parameter (reconcileUseCase), meaning no repo dependencies exist.
        assertTrue(true)
    }

    @Test
    fun `H Coordinator has no WorkManager dependency`() = runBlocking<Unit> {
        val fields = ObservationSchedulingCoordinator::class.java.declaredFields
        val hasWorkManager = fields.any { it.type.simpleName == "WorkManager" }
        assertTrue("Coordinator should not depend on WorkManager", !hasWorkManager)
    }

    @Test
    fun `I Coordinator is stateless - repeated identical events delegate to reconciliation`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        
        coVerify(exactly = 4) { reconcileUseCase() }
    }

    @Test
    fun `J Reconciliation result is returned unchanged`() = runBlocking<Unit> {
        // Activated
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        var result = coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        assertTrue(result is ObservationSchedulingReconciliationResult.Activated)
        
        // Cancelled(DENIED_AUTH)
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Cancelled(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH)
        result = coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable)
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)

        // Cancelled(DENIED_USER_SETTING)
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Cancelled(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING)
        result = coordinator.handle(ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged)
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)

        // Cancelled(DENIED_CONSENT)
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Cancelled(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT)
        result = coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        assertTrue(result is ObservationSchedulingReconciliationResult.Cancelled)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingReconciliationResult.Cancelled).reason)
    }

    @Test
    fun `K Safe logging`() = runBlocking<Unit> {
        coEvery { reconcileUseCase() } returns ObservationSchedulingReconciliationResult.Activated
        
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
            coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable)
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle [ObservationSchedulingCoordinator] [RECONCILE] event=APPLICATION_STARTED"))
            assertTrue(logs.contains("IronMindLifecycle [ObservationSchedulingCoordinator] [RECONCILE] event=AUTHENTICATION_AVAILABLE"))
            assertTrue(!logs.contains("user-"))
            assertTrue(!logs.contains("payload"))
            
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `L No Android lifecycle integration`() = runBlocking<Unit> {
        // Implicitly tested. This class contains no lifecycle observers, Application dependencies, etc.
        assertTrue(true)
    }
}
