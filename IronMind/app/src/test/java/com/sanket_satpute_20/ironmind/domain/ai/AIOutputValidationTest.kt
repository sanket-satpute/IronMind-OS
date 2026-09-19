package com.sanket_satpute_20.ironmind.domain.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIOutputValidationTest {

    @Test
    fun `isValid returns true for perfectly valid output`() {
        val output = AIOutput.IntentOutput(
            intentDescription = "User wants to study",
            confidence = 0.8f,
            schemaVersion = 1
        )
        assertTrue(output.isValid())
    }

    @Test
    fun `isValid returns false if confidence is less than 0`() {
        val output = AIOutput.IntentOutput(
            intentDescription = "User wants to study",
            confidence = -0.1f,
            schemaVersion = 1
        )
        assertFalse(output.isValid())
    }

    @Test
    fun `isValid returns false if confidence is greater than 1`() {
        val output = AIOutput.IntentOutput(
            intentDescription = "User wants to study",
            confidence = 1.1f,
            schemaVersion = 1
        )
        assertFalse(output.isValid())
    }

    @Test
    fun `isValid returns false if schemaVersion is less than 1`() {
        val output = AIOutput.IntentOutput(
            intentDescription = "User wants to study",
            confidence = 0.8f,
            schemaVersion = 0
        )
        assertFalse(output.isValid())
    }

    @Test
    fun `StubIronMindAI output is valid`() {
        val output = AIOutput.NoAction(
            reason = "Test stub",
            confidence = 1.0f,
            schemaVersion = 1
        )
        assertTrue(output.isValid())
    }
}
