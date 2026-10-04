package com.sanket_satpute_20.ironmind.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class AutonomyCapabilityTest {

    @Test
    fun `INTERVENTION_GENERATION exists and retains correct semantics`() {
        val capability = AutonomyCapability.valueOf("INTERVENTION_GENERATION")
        assertEquals(AutonomyCapability.INTERVENTION_GENERATION, capability)
    }

    @Test
    fun `Existing capabilities remain unchanged`() {
        val expectedCapabilities = listOf(
            "PLANNING",
            "SCHEDULING",
            "PROTECTION",
            "PROACTIVE_NOTIFICATIONS",
            "BACKGROUND_LEARNING",
            "GOAL_RESURFACING",
            "REFLECTION_PROCESSING",
            "MEMORY_PATTERN_PROCESSING",
            "EXPERIMENTATION",
            "INTERVENTION_GENERATION"
        )
        
        val actualCapabilities = AutonomyCapability.values().map { it.name }
        
        // Assert all expected capabilities exist
        assertTrue(actualCapabilities.containsAll(expectedCapabilities))
        
        // Assert exact count to ensure no unintended capabilities were added or removed
        assertEquals(10, actualCapabilities.size)
    }

    @Test
    fun `Existing unknown capability behavior throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            AutonomyCapability.valueOf("UNKNOWN_CAPABILITY_XYZ")
        }
    }
    
    @Test
    fun `V2_12 autonomy levels are represented correctly`() {
        // Just verify the enum values exist as expected by the contract
        assertEquals(AutonomyLevel.OFF, AutonomyLevel.valueOf("OFF"))
        assertEquals(AutonomyLevel.SUGGEST_ONLY, AutonomyLevel.valueOf("SUGGEST_ONLY"))
        assertEquals(AutonomyLevel.ASK_BEFORE_ACTION, AutonomyLevel.valueOf("ASK_BEFORE_ACTION"))
        assertEquals(AutonomyLevel.FULL_AUTO, AutonomyLevel.valueOf("FULL_AUTO"))
    }
}
