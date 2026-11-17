package com.sanket_satpute_20.ironmind.data.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudflareWorkerDiagnosticTest {

    @Test
    fun `runDiagnostic returns allPassed true when all steps succeed`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            healthUrl = "http://localhost:8787/health",
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "mock-uid-123") },
            idTokenCheck = { Pair(true, 850) },
            appCheckTokenCheck = { Pair(true, 280) },
            httpGetExecutor = { url ->
                assertEquals("http://localhost:8787/health", url)
                Pair(200, """{"status":"healthy","model":"@cf/google/gemma-4-26b-a4b-it"}""")
            }
        )

        val report = diagnostic.runDiagnostic()

        assertTrue(report.allPassed)
        assertTrue(report.firebaseInitStep.isSuccess)
        assertTrue(report.anonymousAuthStep.isSuccess)
        assertTrue(report.idTokenStep.isSuccess)
        assertTrue(report.appCheckStep.isSuccess)
        assertTrue(report.workerHealthStep.isSuccess)
        assertTrue(report.workerHealthStep.message.contains("healthy"))
    }

    @Test
    fun `runDiagnostic reports failure when Firebase is uninitialized`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { false },
            httpGetExecutor = { Pair(200, """{"status":"healthy"}""") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertFalse(report.firebaseInitStep.isSuccess)
        assertFalse(report.anonymousAuthStep.isSuccess)
        assertFalse(report.idTokenStep.isSuccess)
        assertFalse(report.appCheckStep.isSuccess)
    }

    @Test
    fun `runDiagnostic reports failure when anonymous authentication fails`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(false, null) },
            appCheckTokenCheck = { Pair(true, 200) },
            httpGetExecutor = { Pair(200, """{"status":"healthy"}""") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertTrue(report.firebaseInitStep.isSuccess)
        assertFalse(report.anonymousAuthStep.isSuccess)
        assertFalse(report.idTokenStep.isSuccess)
        assertTrue(report.appCheckStep.isSuccess)
    }

    @Test
    fun `runDiagnostic reports failure when ID token retrieval fails`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-456") },
            idTokenCheck = { Pair(false, null) },
            appCheckTokenCheck = { Pair(true, 200) },
            httpGetExecutor = { Pair(200, """{"status":"healthy"}""") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertTrue(report.firebaseInitStep.isSuccess)
        assertTrue(report.anonymousAuthStep.isSuccess)
        assertFalse(report.idTokenStep.isSuccess)
    }

    @Test
    fun `runDiagnostic reports failure when App Check token retrieval fails`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-456") },
            idTokenCheck = { Pair(true, 800) },
            appCheckTokenCheck = { Pair(false, null) },
            httpGetExecutor = { Pair(200, """{"status":"healthy"}""") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertTrue(report.firebaseInitStep.isSuccess)
        assertTrue(report.anonymousAuthStep.isSuccess)
        assertTrue(report.idTokenStep.isSuccess)
        assertFalse(report.appCheckStep.isSuccess)
    }

    @Test
    fun `runDiagnostic reports failure when Worker health returns non-200 code`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-456") },
            idTokenCheck = { Pair(true, 800) },
            appCheckTokenCheck = { Pair(true, 200) },
            httpGetExecutor = { Pair(500, "Internal Server Error") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertFalse(report.workerHealthStep.isSuccess)
        assertTrue(report.workerHealthStep.message.contains("500"))
    }

    @Test
    fun `runDiagnostic reports failure when Worker health connection throws IOException`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-456") },
            idTokenCheck = { Pair(true, 800) },
            appCheckTokenCheck = { Pair(true, 200) },
            httpGetExecutor = { throw IOException("Connection refused: localhost:8787") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertFalse(report.workerHealthStep.isSuccess)
        assertTrue(report.workerHealthStep.message.contains("Connection refused"))
    }

    @Test
    fun `runDiagnostic is completely disabled in release builds`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = false, // Release build simulation
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-123") }
        )

        val report = diagnostic.runDiagnostic()

        assertFalse(report.allPassed)
        assertFalse(report.firebaseInitStep.isSuccess)
        assertTrue(report.firebaseInitStep.message.contains("strictly disabled in release"))
    }

    @Test
    fun `runDiagnostic never exposes raw token values in step messages`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "user-secret-test") },
            idTokenCheck = { Pair(true, 920) },
            appCheckTokenCheck = { Pair(true, 310) },
            httpGetExecutor = { Pair(200, """{"status":"healthy"}""") }
        )

        val report = diagnostic.runDiagnostic()

        // Verify tokens or substrings resembling JWTs are not included
        val allMessages = listOf(
            report.firebaseInitStep.message,
            report.anonymousAuthStep.message,
            report.idTokenStep.message,
            report.appCheckStep.message,
            report.workerHealthStep.message
        ).joinToString(" ")

        assertFalse(allMessages.contains("eyJh")) // JWT header prefix
        assertFalse(allMessages.contains("Bearer"))
        assertTrue(report.idTokenStep.message.contains("redacted"))
        assertTrue(report.appCheckStep.message.contains("redacted"))
    }

    // ── Live Auth Verification Tests (POST /v1/auth/verify) ────────────────────

    @Test
    fun `verifyWorkerAuth returns success HTTP 200 when credentials are valid and headers sent correctly`() = runTest {
        var capturedUrl: String? = null
        var capturedHeaders: Map<String, String>? = null

        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            authVerifyUrl = "http://localhost:8787/v1/auth/verify",
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "eyJh-mock-valid-id-token-12345" },
            rawAppCheckTokenProvider = { "eyJh-mock-valid-app-check-token-67890" },
            httpPostExecutor = { url, headers ->
                capturedUrl = url
                capturedHeaders = headers
                Pair(200, """{"authenticated":true}""")
            }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertTrue(result.isSuccess)
        assertEquals(200, result.statusCode)
        assertTrue(result.message.contains("HTTP 200"))
        assertEquals("http://localhost:8787/v1/auth/verify", capturedUrl)
        assertNotNull(capturedHeaders)
        assertEquals("Bearer eyJh-mock-valid-id-token-12345", capturedHeaders?.get("Authorization"))
        assertEquals("eyJh-mock-valid-app-check-token-67890", capturedHeaders?.get("X-Firebase-AppCheck"))
        assertEquals("application/json", capturedHeaders?.get("Content-Type"))
        assertEquals("application/json", capturedHeaders?.get("Accept"))
    }

    @Test
    fun `verifyWorkerAuth returns failure when worker rejects credentials with HTTP 401 or HTTP 403`() = runTest {
        // 401 scenario
        val diagnostic401 = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "mock-token" },
            rawAppCheckTokenProvider = { "mock-app-check" },
            httpPostExecutor = { _, _ ->
                Pair(401, """{"error":{"code":"INVALID_FIREBASE_ID_TOKEN","message":"Token expired"}}""")
            }
        )
        val result401 = diagnostic401.verifyWorkerAuth()
        assertFalse(result401.isSuccess)
        assertEquals(401, result401.statusCode)
        assertTrue(result401.message.contains("401"))
        assertTrue(result401.message.contains("INVALID_FIREBASE_ID_TOKEN"))

        // 403 scenario
        val diagnostic403 = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "mock-token" },
            rawAppCheckTokenProvider = { "mock-app-check" },
            httpPostExecutor = { _, _ ->
                Pair(403, """{"error":{"code":"INVALID_APP_CHECK_APP_ID","message":"Mismatched App ID"}}""")
            }
        )
        val result403 = diagnostic403.verifyWorkerAuth()
        assertFalse(result403.isSuccess)
        assertEquals(403, result403.statusCode)
        assertTrue(result403.message.contains("403"))
        assertTrue(result403.message.contains("INVALID_APP_CHECK_APP_ID"))
    }

    @Test
    fun `verifyWorkerAuth returns failure on network connection failure without crashing`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "mock-token" },
            rawAppCheckTokenProvider = { "mock-app-check" },
            httpPostExecutor = { _, _ ->
                throw IOException("Failed to connect to localhost:8787")
            }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertTrue(result.message.contains("Could not reach Worker"))
        assertTrue(result.message.contains("Failed to connect to localhost:8787"))
    }

    @Test
    fun `verifyWorkerAuth returns failure when ID token retrieval returns null or empty`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { null },
            rawAppCheckTokenProvider = { "valid-app-check" }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertTrue(result.message.contains("Firebase ID token was null or empty"))
    }

    @Test
    fun `verifyWorkerAuth returns failure when App Check token retrieval returns null or empty`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "valid-id-token" },
            rawAppCheckTokenProvider = { "" }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertTrue(result.message.contains("Firebase App Check token was null or empty"))
    }

    @Test
    fun `verifyWorkerAuth is strictly disabled in release builds`() = runTest {
        var httpInvoked = false
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = false, // Release build simulation
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "valid-token" },
            rawAppCheckTokenProvider = { "valid-token" },
            httpPostExecutor = { _, _ ->
                httpInvoked = true
                Pair(200, "{}")
            }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertTrue(result.message.contains("strictly disabled in release"))
        assertFalse("HTTP request must NEVER be sent in release builds", httpInvoked)
    }

    @Test
    fun `verifyWorkerAuth strictly redacts tokens and never leaks raw token strings in result message`() = runTest {
        val rawSecretToken = "super-confidential-raw-jwt-content-xyz999"
        val rawAppCheckSecret = "super-confidential-raw-appcheck-abc123"

        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { rawSecretToken },
            rawAppCheckTokenProvider = { rawAppCheckSecret },
            httpPostExecutor = { _, _ ->
                Pair(200, """{"authenticated":true}""")
            }
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse(result.message.contains(rawSecretToken))
        assertFalse(result.message.contains(rawAppCheckSecret))
        assertFalse(result.message.contains("Bearer"))
    }

    @Test
    fun `testSingleProductionInference returns success and parsed intent when Worker responds with 200`() = runTest {
        var requestedUrl = ""
        var requestedBody = ""
        val mockIntentResponse = """
            {
                "type": "INTENT",
                "intentDescription": "Improve daily consistency",
                "confidence": 0.95,
                "reasoning": "Clear user statement",
                "schemaVersion": 1
            }
        """.trimIndent()

        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            productionUrl = "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev",
            rawIdTokenProvider = { "valid-test-id-token" },
            rawAppCheckTokenProvider = { "valid-test-app-check-token" },
            productionInferenceAuthUidProvider = { "test-user-uid-123" },
            productionInferenceTransport = { url, headers, body ->
                requestedUrl = url
                requestedBody = body
                Pair(200, mockIntentResponse)
            }
        )

        val result = diagnostic.testSingleProductionInference()

        assertTrue(result.isSuccess)
        assertEquals(200, result.statusCode)
        assertEquals("Improve daily consistency", result.intentDescription)
        assertEquals(0.95f, result.confidence ?: 0f, 0.001f)
        assertEquals("Reasoning should match", "Clear user statement", result.reasoning)
        assertTrue(requestedUrl.contains("/v1/process"))
        assertTrue(requestedBody.contains("test-user-uid-123"))
        assertTrue(requestedBody.contains("INTENT_EXTRACTION"))
        assertTrue(requestedBody.contains("I want to improve my daily consistency."))
    }

    @Test
    fun `testSingleProductionInference is strictly disabled in release builds`() = runTest {
        var transportInvoked = false
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = false, // Release mode
            rawIdTokenProvider = { "token" },
            rawAppCheckTokenProvider = { "token" },
            productionInferenceAuthUidProvider = { "uid" },
            productionInferenceTransport = { _, _, _ ->
                transportInvoked = true
                Pair(200, "{}")
            }
        )

        val result = diagnostic.testSingleProductionInference()

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertTrue(result.errorMessage?.contains("strictly disabled in release") == true)
        assertFalse("Transport must NEVER be called in release builds", transportInvoked)
    }

    @Test
    fun `testSingleProductionInference handles Worker HTTP error and parses failure correctly`() = runTest {
        val diagnostic = CloudflareWorkerDiagnostic(
            isDebug = true,
            rawIdTokenProvider = { "valid-token" },
            rawAppCheckTokenProvider = { "valid-token" },
            productionInferenceAuthUidProvider = { "uid" },
            productionInferenceTransport = { _, _, _ ->
                Pair(429, """{"error":{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests"}}""")
            }
        )

        val result = diagnostic.testSingleProductionInference()

        assertFalse(result.isSuccess)
        assertEquals(429, result.statusCode)
        assertTrue(result.errorMessage?.contains("Too many requests") == true)
    }

    @Test
    fun `DEFAULT_HEALTH_URL targets deployed production worker health endpoint`() {
        assertEquals(
            "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev/health",
            CloudflareWorkerDiagnostic.DEFAULT_HEALTH_URL
        )
    }
}
