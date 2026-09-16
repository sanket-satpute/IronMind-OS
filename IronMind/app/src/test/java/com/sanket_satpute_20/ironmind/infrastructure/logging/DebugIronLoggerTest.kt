package com.sanket_satpute_20.ironmind.infrastructure.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugIronLoggerTest {

    private val logger = DebugIronLogger()

    @Test
    fun `formatMessage produces correct output without parameters`() {
        val result = logger.formatMessage("App", "STARTED", emptyMap())
        assertEquals("[App] [STARTED]", result)
    }

    @Test
    fun `formatMessage produces correct output with parameters`() {
        val result = logger.formatMessage(
            "Navigation", 
            "DESTINATION_CHANGED", 
            mapOf("destination" to "Today")
        )
        assertEquals("[Navigation] [DESTINATION_CHANGED] destination=Today", result)
    }

    @Test
    fun `formatMessage produces correct output with multiple parameters`() {
        val result = logger.formatMessage(
            "Database", 
            "MIGRATION_COMPLETED", 
            mapOf("version" to 2, "timeMs" to 150)
        )
        assertEquals("[Database] [MIGRATION_COMPLETED] version=2 timeMs=150", result)
    }

    @Test
    fun `formatMessage masks sensitive keys`() {
        val result = logger.formatMessage(
            "Auth", 
            "LOGIN_ATTEMPT", 
            mapOf(
                "email" to "user@example.com",
                "password" to "supersecret123",
                "userId" to "abc-123",
                "sessionToken" to "jwt-token-string"
            )
        )
        
        // Order of map keys is preserved in Kotlin's default mapOf (LinkedHashMap)
        assertEquals(
            "[Auth] [LOGIN_ATTEMPT] email=*** password=*** userId=abc-123 sessionToken=***",
            result
        )
    }
}
