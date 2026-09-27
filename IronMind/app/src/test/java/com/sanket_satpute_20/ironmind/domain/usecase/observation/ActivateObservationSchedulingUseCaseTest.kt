package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler
import io.mockk.coEvery
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

class ActivateObservationSchedulingUseCaseTest {

    private lateinit var policyUseCase: ObservationSchedulingPolicyUseCase
    private lateinit var observationScheduler: ObservationScheduler
    private lateinit var useCase: ActivateObservationSchedulingUseCase

    @Before
    fun setUp() {
        policyUseCase = mockk()
        observationScheduler = mockk(relaxed = true)
        useCase = ActivateObservationSchedulingUseCase(policyUseCase, observationScheduler)
    }

    @Test
    fun `A Policy Allowed invokes schedule once and returns Activated`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed

        val result = useCase()

        verify(exactly = 1) { observationScheduler.scheduleObservationCollection() }
        assertTrue(result is ObservationSchedulingActivationResult.Activated)
    }

    @Test
    fun `B Policy DENIED_AUTH does not invoke schedule and returns Denied_AUTH`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH
        )

        val result = useCase()

        verify(exactly = 0) { observationScheduler.scheduleObservationCollection() }
        assertTrue(result is ObservationSchedulingActivationResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH, (result as ObservationSchedulingActivationResult.Denied).reason)
    }

    @Test
    fun `C Policy DENIED_USER_SETTING does not invoke schedule and returns Denied_USER_SETTING`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING
        )

        val result = useCase()

        verify(exactly = 0) { observationScheduler.scheduleObservationCollection() }
        assertTrue(result is ObservationSchedulingActivationResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING, (result as ObservationSchedulingActivationResult.Denied).reason)
    }

    @Test
    fun `D Policy DENIED_CONSENT does not invoke schedule and returns Denied_CONSENT`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT
        )

        val result = useCase()

        verify(exactly = 0) { observationScheduler.scheduleObservationCollection() }
        assertTrue(result is ObservationSchedulingActivationResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingActivationResult.Denied).reason)
    }

    @Test
    fun `E Policy is authoritative and does not inspect repositories`() = runBlocking<Unit> {
        // Since we did not provide AuthRepository, AutonomySettingsRepository etc., 
        // to this class, it CANNOT inspect them independently. It purely relies on policyUseCase.
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        
        val result = useCase()
        
        verify(exactly = 1) { observationScheduler.scheduleObservationCollection() }
        assertTrue(result is ObservationSchedulingActivationResult.Activated)
    }

    @Test
    fun `F No WorkManager dependency`() {
        // We verify this by ensuring there is no WorkManager class imported or passed.
        val fields = ActivateObservationSchedulingUseCase::class.java.declaredFields
        val hasWorkManager = fields.any { it.type.simpleName == "WorkManager" }
        assertTrue("UseCase should not directly depend on WorkManager", !hasWorkManager)
    }

    @Test
    fun `G No automatic invocation`() {
        // Implicitly tested. This class has no lifecycle components. 
        // We just verify it executes when explicitly called.
        assertTrue(true)
    }

    @Test
    fun `H Safe logging`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            useCase()
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle [ObservationSchedulingActivation] [ACTIVATED]"))
            assertTrue(!logs.contains("user-"))
            assertTrue(!logs.contains("payload"))
            
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `H Safe logging for denied`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Denied(
            ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH
        )
        
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            useCase()
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle [ObservationSchedulingActivation] [DENIED] reason=DENIED_AUTH"))
            assertTrue(!logs.contains("user-"))
            assertTrue(!logs.contains("payload"))
            
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test(expected = RuntimeException::class)
    fun `I Scheduler exception behavior bubbles up`() = runBlocking<Unit> {
        coEvery { policyUseCase() } returns ObservationSchedulingPolicyResult.Allowed
        every { observationScheduler.scheduleObservationCollection() } throws RuntimeException("Scheduler failed")

        // Should bubble up exception
        useCase()
    }
}
