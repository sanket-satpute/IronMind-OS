package com.sanket_satpute_20.ironmind.data.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async

private class FirebaseException(message: String) : Exception(message)

class CloudflareWorkerIronMindAITest {

    private val sampleContextSnapshot = ContextSnapshot(
        timestamp = 1700000000000L,
        dayOfWeek = 2,
        activeCommitments = emptyList(),
        recentEvents = emptyList(),
        factualContextSnapshot = FactualContextSnapshot(
            userId = "test-user-123",
            startTimeMs = 1700000000000L,
            endTimeMs = 1700003600000L,
            appUsage = null,
            activity = null,
            calendar = null,
            location = null,
            notifications = null
        ),
        recentReflections = emptyList(),
        recentPatterns = emptyList(),
        activeProtectionSession = null,
        activeGoals = emptyList()
    )

    @Test
    fun `buildRequestBody serializes AIRequest fields correctly`() {
        val request = AIRequest(
            userId = "test-user-123",
            input = "Run 5km tomorrow morning",
            requestType = AIRequestType.INTENT_EXTRACTION,
            contextSnapshot = sampleContextSnapshot
        )

        val jsonStr = CloudflareWorkerIronMindAI.buildRequestBody(request)
        val json = JSONObject(jsonStr)

        assertEquals("test-user-123", json.getString("userId"))
        assertEquals("Run 5km tomorrow morning", json.getString("input"))
        assertEquals("INTENT_EXTRACTION", json.getString("requestType"))
        assertTrue(json.has("contextSnapshot"))
        val snapshotObj = json.getJSONObject("contextSnapshot")
        assertEquals(1700000000000L, snapshotObj.getLong("timestamp"))
        assertEquals(2, snapshotObj.getInt("dayOfWeek"))
    }

    @Test
    fun `buildRequestBody handles null contextSnapshot`() {
        val request = AIRequest(
            userId = "test-user-123",
            input = "Hello AI",
            requestType = AIRequestType.INTENT_EXTRACTION,
            contextSnapshot = null
        )

        val jsonStr = CloudflareWorkerIronMindAI.buildRequestBody(request)
        val json = JSONObject(jsonStr)

        assertEquals("test-user-123", json.getString("userId"))
        assertTrue(json.isNull("contextSnapshot"))
    }

    @Test
    fun `resolveEndpointUrl normalizes various URL formats to v1 process`() {
        assertEquals(
            "https://worker.subdomain.workers.dev/v1/process",
            CloudflareWorkerIronMindAI.resolveEndpointUrl("https://worker.subdomain.workers.dev")
        )
        assertEquals(
            "https://worker.subdomain.workers.dev/v1/process",
            CloudflareWorkerIronMindAI.resolveEndpointUrl("https://worker.subdomain.workers.dev/")
        )
        assertEquals(
            "https://worker.subdomain.workers.dev/v1/process",
            CloudflareWorkerIronMindAI.resolveEndpointUrl("https://worker.subdomain.workers.dev/v1/process")
        )
        assertEquals(
            "https://worker.subdomain.workers.dev/v1/process",
            CloudflareWorkerIronMindAI.resolveEndpointUrl("https://worker.subdomain.workers.dev/v1/process/")
        )
    }

    @Test
    fun `isLocalDevelopmentUrl identifies local and remote endpoints correctly`() {
        assertTrue(CloudflareWorkerIronMindAI.isLocalDevelopmentUrl("http://10.0.2.2:8787/v1/process"))
        assertTrue(CloudflareWorkerIronMindAI.isLocalDevelopmentUrl("http://localhost:8787/v1/process"))
        assertTrue(CloudflareWorkerIronMindAI.isLocalDevelopmentUrl("http://127.0.0.1:8787/v1/process"))
        assertFalse(CloudflareWorkerIronMindAI.isLocalDevelopmentUrl("https://ironmind-ai.workers.dev/v1/process"))
    }

    @Test
    fun `shouldEnforceAppCheck respects explicit flag and endpoint location`() {
        // Explicit overrides
        assertTrue(CloudflareWorkerIronMindAI.shouldEnforceAppCheck("http://10.0.2.2:8787", explicitRequireAppCheck = true))
        assertFalse(CloudflareWorkerIronMindAI.shouldEnforceAppCheck("https://worker.example.com", explicitRequireAppCheck = false))

        // Remote vs Local default resolution
        assertTrue(CloudflareWorkerIronMindAI.shouldEnforceAppCheck("https://worker.example.com"))
        assertFalse(CloudflareWorkerIronMindAI.shouldEnforceAppCheck("http://10.0.2.2:8787"))
    }

    @Test
    fun `process sends correct Authorization and App Check headers on HTTP 200`() = runTest {
        var recordedHeaders: Map<String, String>? = null
        var recordedBody: String? = null

        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "fake-firebase-id-token" },
            appCheckTokenProvider = { "fake-app-check-token" },
            httpTransport = { _, headers, body ->
                recordedHeaders = headers
                recordedBody = body
                Pair(
                    200,
                    """
                    {
                        "type": "INTENT",
                        "confidence": 0.95,
                        "reasoning": "Clear fitness intent detected.",
                        "schemaVersion": 1,
                        "intentDescription": "Go for a morning jog"
                    }
                    """.trimIndent()
                )
            }
        )

        val request = AIRequest(
            userId = "user-abc",
            input = "Jog tomorrow morning",
            requestType = AIRequestType.INTENT_EXTRACTION
        )

        val result = client.process(request)
        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertTrue(output is AIOutput.IntentOutput)
        val intentOutput = output as AIOutput.IntentOutput
        assertEquals("Go for a morning jog", intentOutput.intentDescription)
        assertEquals(0.95f, intentOutput.confidence)

        assertNotNull(recordedHeaders)
        assertEquals("Bearer fake-firebase-id-token", recordedHeaders?.get("Authorization"))
        assertEquals("fake-app-check-token", recordedHeaders?.get("X-Firebase-AppCheck"))
        assertEquals("application/json", recordedHeaders?.get("Content-Type"))
    }

    @Test
    fun `process succeeds without App Check header in local development mode when App Check token is null`() = runTest {
        var recordedHeaders: Map<String, String>? = null

        val client = CloudflareWorkerIronMindAI(
            endpointUrl = CloudflareWorkerIronMindAI.DEFAULT_LOCAL_DEV_URL,
            requireAppCheck = false,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = { null },
            httpTransport = { _, headers, _ ->
                recordedHeaders = headers
                Pair(
                    200,
                    """
                    {
                        "type": "SUMMARY",
                        "confidence": 0.9,
                        "reasoning": "Summary generated.",
                        "schemaVersion": 1,
                        "summaryText": "All tasks done"
                    }
                    """.trimIndent()
                )
            }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Summarize today",
            requestType = AIRequestType.SUMMARIZE
        )

        val result = client.process(request)
        assertTrue(result is Result.Success)
        assertNotNull(recordedHeaders)
        assertEquals("Bearer id-token-xyz", recordedHeaders?.get("Authorization"))
        assertNull(recordedHeaders?.get("X-Firebase-AppCheck"))
    }

    @Test
    fun `process returns Failure when App Check token is missing in enforced mode without fabricating data`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = { null }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Summarize today",
            requestType = AIRequestType.SUMMARIZE
        )

        val result = client.process(request)
        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(403, workerEx.statusCode)
        assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
    }

    @Test
    fun `process returns Failure with detailed diagnostic info when App Check token acquisition throws exception`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = { throw IllegalStateException("Device attestation failed: secret 12345678-1234-1234-1234-123456789abc") }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Summarize today",
            requestType = AIRequestType.SUMMARIZE
        )

        val result = client.process(request)
        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(403, workerEx.statusCode)
        assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
        assertTrue("Contains exception type", workerEx.message.contains("exception=IllegalStateException"))
        assertTrue("Contains provider type", workerEx.message.contains("provider=DebugAppCheckProvider"))
        assertTrue("Redacts secret UUID", workerEx.message.contains("[REDACTED_UUID]"))
        assertFalse("Never contains raw UUID", workerEx.message.contains("12345678-1234-1234-1234-123456789abc"))
        assertTrue("Contains tokenStatus", workerEx.message.contains("tokenStatus=null (no token returned)"))
    }

    @Test
    fun `process returns Failure with detailed diagnostic info when App Check returns empty blank token`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = { "" }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Summarize today",
            requestType = AIRequestType.SUMMARIZE
        )

        val result = client.process(request)
        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(403, workerEx.statusCode)
        assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
        assertTrue("Reports empty token length", workerEx.message.contains("returned empty/blank token (length=0)"))
    }

    @Test
    fun `sanitizeErrorMessage redacts JWTs, bearer tokens, UUIDs, and API keys`() {
        val raw = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeak with secret 87654321-4321-4321-4321-cba987654321 and key AIzaSyBsbRClXvXN3InxHEZ8xbI621ZxMzZl_ZU"
        val sanitized = CloudflareWorkerIronMindAI.sanitizeErrorMessage(raw)
        assertNotNull(sanitized)
        assertFalse("No JWT", sanitized!!.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse("No UUID", sanitized.contains("87654321-4321-4321-4321-cba987654321"))
        assertFalse("No API key", sanitized.contains("AIzaSyBsbRClXvXN3InxHEZ8xbI621ZxMzZl_ZU"))
        assertTrue("Contains redacted token tag", sanitized.contains("[REDACTED"))
    }

    @Test
    fun `process retries with refreshed ID token on HTTP 401`() = runTest {
        var attempts = 0
        var forceRefreshCalled = false

        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { forceRefresh ->
                if (forceRefresh) forceRefreshCalled = true
                if (forceRefresh) "refreshed-id-token" else "expired-id-token"
            },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, headers, _ ->
                attempts++
                if (headers["Authorization"] == "Bearer expired-id-token") {
                    Pair(
                        401,
                        """{"error":{"code":"INVALID_FIREBASE_ID_TOKEN","message":"Token expired"}}"""
                    )
                } else {
                    Pair(
                        200,
                        """
                        {
                            "type": "INTENT",
                            "confidence": 0.8,
                            "intentDescription": "Read a chapter",
                            "schemaVersion": 1
                        }
                        """.trimIndent()
                    )
                }
            }
        )

        val request = AIRequest("u-1", "Read book", AIRequestType.INTENT_EXTRACTION)
        val result = client.process(request)

        assertTrue(result is Result.Success)
        assertEquals(2, attempts)
        assertTrue(forceRefreshCalled)
    }

    @Test
    fun `process retries with refreshed App Check token on HTTP 403`() = runTest {
        var attempts = 0
        var forceRefreshCalled = false

        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-id-token" },
            appCheckTokenProvider = { forceRefresh ->
                if (forceRefresh) forceRefreshCalled = true
                if (forceRefresh) "fresh-appcheck-token" else "expired-appcheck-token"
            },
            httpTransport = { _, headers, _ ->
                attempts++
                if (headers["X-Firebase-AppCheck"] == "expired-appcheck-token") {
                    Pair(
                        403,
                        """{"error":{"code":"INVALID_APP_CHECK_TOKEN","message":"App check expired"}}"""
                    )
                } else {
                    Pair(
                        200,
                        """
                        {
                            "type": "INTENT",
                            "confidence": 0.8,
                            "intentDescription": "Drink water",
                            "schemaVersion": 1
                        }
                        """.trimIndent()
                    )
                }
            }
        )

        val request = AIRequest("u-1", "Drink water", AIRequestType.INTENT_EXTRACTION)
        val result = client.process(request)

        assertTrue(result is Result.Success)
        assertEquals(2, attempts)
        assertTrue(forceRefreshCalled)
    }

    @Test
    fun `process returns Failure on persistent HTTP 401 without fabricating data`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "bad-id-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    401,
                    """{"error":{"code":"INVALID_FIREBASE_ID_TOKEN","message":"Invalid token signature"}}"""
                )
            }
        )

        val request = AIRequest("u-1", "Plan day", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(401, workerEx.statusCode)
        assertEquals("INVALID_FIREBASE_ID_TOKEN", workerEx.code)
    }

    @Test
    fun `process returns Failure on HTTP 429 quota exhaustion without fabricating data`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    429,
                    """{"error":{"code":"DAILY_QUOTA_EXHAUSTED","message":"Free neuron allocation reached. Resets at 00:00 UTC."}}"""
                )
            }
        )

        val request = AIRequest("u-1", "Any input", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(429, workerEx.statusCode)
        assertEquals("DAILY_QUOTA_EXHAUSTED", workerEx.code)
    }

    @Test
    fun `process returns Failure on HTTP 502 malformed model output without fabricating data`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    502,
                    """{"error":{"code":"INVALID_JSON_OUTPUT","message":"Model output was not valid JSON."}}"""
                )
            }
        )

        val request = AIRequest("u-1", "Any input", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(502, workerEx.statusCode)
        assertEquals("INVALID_JSON_OUTPUT", workerEx.code)
    }

    @Test
    fun `process returns Failure on HTTP 504 worker inference timeout`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    504,
                    """{"error":{"code":"AI_TIMEOUT","message":"Workers AI execution timed out after 15000ms."}}"""
                )
            }
        )

        val request = AIRequest("u-1", "Any input", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(504, workerEx.statusCode)
        assertEquals("AI_TIMEOUT", workerEx.code)
    }

    @Test
    fun `process returns Failure on network I-O failure`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                throw IOException("Connection timed out")
            }
        )

        val request = AIRequest("u-1", "Any input", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is IOException)
    }

    @Test
    fun `process parses valid PLAN output preserving proposed tasks`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    200,
                    """
                    {
                        "type": "PLAN",
                        "confidence": 0.9,
                        "reasoning": "3-step actionable breakdown",
                        "schemaVersion": 1,
                        "goalId": "goal-456",
                        "proposedTasks": [
                            {
                                "title": "Buy running shoes",
                                "description": "Visit sports store",
                                "estimatedDurationMinutes": 30,
                                "isNextAction": true
                            },
                            {
                                "title": "Run 2km",
                                "description": "Jog in park",
                                "estimatedDurationMinutes": 20,
                                "isNextAction": false
                            }
                        ]
                    }
                    """.trimIndent()
                )
            }
        )

        val request = AIRequest("u-1", "Prepare for 5k", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertTrue(output is AIOutput.PlanOutput)
        val plan = output as AIOutput.PlanOutput
        assertEquals("goal-456", plan.goalId)
        assertEquals(2, plan.proposedTasks.size)
        assertEquals("Buy running shoes", plan.proposedTasks[0].title)
        assertTrue(plan.proposedTasks[0].isNextAction)
        assertEquals("Run 2km", plan.proposedTasks[1].title)
    }

    @Test
    fun `process parses valid BARRIER output preserving barrier hypotheses`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            idTokenProvider = { "valid-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                Pair(
                    200,
                    """
                    {
                        "type": "BARRIER",
                        "confidence": 0.85,
                        "reasoning": "Uncertainty hypothesis identified",
                        "schemaVersion": 1,
                        "proposedBarriers": [
                            {
                                "category": "UNCERTAINTY",
                                "description": "Could not knowing where to start be causing hesitation?"
                            }
                        ]
                    }
                    """.trimIndent()
                )
            }
        )

        val request = AIRequest("u-1", "I can't seem to start", AIRequestType.BARRIER_UNDERSTANDING)
        val result = client.process(request)

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertTrue(output is AIOutput.BarrierOutput)
        val barrier = output as AIOutput.BarrierOutput
        assertEquals(1, barrier.proposedBarriers.size)
        assertEquals("Could not knowing where to start be causing hesitation?", barrier.proposedBarriers[0].description)
    }

    @Test
    fun `default socket read timeout is set to 30000ms to allow worker timeout response`() {
        assertEquals(15000, CloudflareWorkerIronMindAI.DEFAULT_CONNECT_TIMEOUT_MS)
        assertEquals(30000, CloudflareWorkerIronMindAI.DEFAULT_READ_TIMEOUT_MS)
        assertEquals(30000, CloudflareWorkerIronMindAI.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun `process does not retry on SocketTimeoutException and never sends duplicate POST`() = runTest {
        var attempts = 0
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://ironmind-ai.workers.dev/v1/process",
            requireAppCheck = false,
            maxRetries = 2,
            idTokenProvider = { "valid-id-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                attempts++
                throw SocketTimeoutException("Read timed out")
            }
        )

        val request = AIRequest("u-1", "Plan my day", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertEquals(1, attempts)
        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure).error
        assertTrue(failure is SocketTimeoutException)
        assertEquals("Read timed out", failure.message)
    }

    @Test
    fun `process does not retry on read timeout transport error`() = runTest {
        var attempts = 0
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://ironmind-ai.workers.dev/v1/process",
            requireAppCheck = false,
            maxRetries = 2,
            idTokenProvider = { "valid-id-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                attempts++
                throw IOException("SSL peer shut down incorrectly or read timed out")
            }
        )

        val request = AIRequest("u-1", "Plan my day", AIRequestType.PLAN_GENERATION)
        val result = client.process(request)

        assertEquals(1, attempts)
        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure).error
        assertTrue(failure is IOException)
    }

    @Test(expected = CancellationException::class)
    fun `process rethrows CancellationException when coroutine is cancelled`() = runTest {
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://ironmind-ai.workers.dev/v1/process",
            requireAppCheck = false,
            maxRetries = 2,
            idTokenProvider = { "valid-id-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                throw CancellationException("Scope was cancelled")
            }
        )

        val request = AIRequest("u-1", "Plan my day", AIRequestType.PLAN_GENERATION)
        client.process(request)
    }

    @Test
    fun `process maps HTTP 400 UNSUPPORTED_REQUEST_TYPE to CloudflareWorkerException without fabricating output`() = runTest {
        var attempts = 0
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://ironmind-ai.workers.dev/v1/process",
            requireAppCheck = false,
            maxRetries = 2,
            idTokenProvider = { "valid-id-token" },
            appCheckTokenProvider = { "valid-app-check-token" },
            httpTransport = { _, _, _ ->
                attempts++
                Pair(
                    400,
                    """
                    {
                        "error": {
                            "code": "UNSUPPORTED_REQUEST_TYPE",
                            "message": "Request type SUMMARIZE is not supported by IronMind AI worker"
                        }
                    }
                    """.trimIndent()
                )
            }
        )

        val request = AIRequest("u-1", "Summarize my day", AIRequestType.SUMMARIZE)
        val result = client.process(request)

        assertEquals(1, attempts)
        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure).error
        assertTrue(failure is CloudflareWorkerException)
        val workerEx = failure as CloudflareWorkerException
        assertEquals("UNSUPPORTED_REQUEST_TYPE", workerEx.code)
        assertEquals(400, workerEx.statusCode)
        assertTrue(workerEx.message.contains("SUMMARIZE is not supported"))
    }

    @Test
    fun `process returns Failure with detailed diagnostic info when App Check throws FirebaseException Too many attempts`() = runTest {
        var transportCalled = false
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = { throw FirebaseException("Too many attempts.") },
            httpTransport = { _, _, _ ->
                transportCalled = true
                Pair(200, "{}")
            }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Improve daily consistency",
            requestType = AIRequestType.INTENT_EXTRACTION
        )

        val result = client.process(request)
        assertFalse(transportCalled)
        assertTrue(result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex is CloudflareWorkerException)
        val workerEx = ex as CloudflareWorkerException
        assertEquals(403, workerEx.statusCode)
        assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
        assertTrue("Contains provider type", workerEx.message.contains("provider=DebugAppCheckProvider"))
        assertTrue("Contains exception type", workerEx.message.contains("exception=FirebaseException"))
        assertTrue("Contains message", workerEx.message.contains("message=Too many attempts."))
        assertTrue("Contains tokenStatus", workerEx.message.contains("tokenStatus=null (no token returned)"))
    }

    @Test
    fun `process handles concurrent requests safely failing closed when App Check throws Too many attempts`() = runTest {
        var transportCalls = 0
        var providerCalls = 0
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = {
                providerCalls++
                throw FirebaseException("Too many attempts.")
            },
            httpTransport = { _, _, _ ->
                transportCalls++
                Pair(200, "{}")
            }
        )

        val deferreds = (1..10).map { i ->
            async {
                val request = AIRequest(
                    userId = "user-$i",
                    input = "Input $i",
                    requestType = AIRequestType.INTENT_EXTRACTION
                )
                client.process(request)
            }
        }

        val results = deferreds.map { it.await() }
        assertEquals(0, transportCalls)
        assertEquals(10, results.size)
        results.forEach { result ->
            assertTrue(result is Result.Failure)
            val workerEx = (result as Result.Failure).error as CloudflareWorkerException
            assertEquals(403, workerEx.statusCode)
            assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
            assertTrue(workerEx.message.contains("message=Too many attempts."))
        }
    }

    @Test
    fun `process does not retry when App Check token acquisition fails upfront with Too many attempts`() = runTest {
        var appCheckAttempts = 0
        val client = CloudflareWorkerIronMindAI(
            endpointUrl = "https://worker.example.com",
            requireAppCheck = true,
            maxRetries = 3, // Even with maxRetries = 3, upfront App Check failure must NOT retry
            idTokenProvider = { "id-token-xyz" },
            appCheckTokenProvider = {
                appCheckAttempts++
                throw FirebaseException("Too many attempts.")
            }
        )

        val request = AIRequest(
            userId = "user-1",
            input = "Improve daily consistency",
            requestType = AIRequestType.INTENT_EXTRACTION
        )

        val result = client.process(request)
        assertEquals(1, appCheckAttempts) // Exactly 1 attempt, no looping or retry storm
        assertTrue(result is Result.Failure)
        val workerEx = (result as Result.Failure).error as CloudflareWorkerException
        assertEquals(403, workerEx.statusCode)
        assertEquals("APP_CHECK_TOKEN_MISSING", workerEx.code)
    }

    @Test
    fun `buildRequestBody serializes activeMemories with confirmation states and excludes rejected or foreign memories`() {
        val userId = "user-123"

        val memConf = com.sanket_satpute_20.ironmind.domain.model.Memory(
            id = "mem-1", userId = userId, type = "fact", content = "Likes early morning workouts",
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            confidence = 1.0f, evidenceCount = 1, firstObservedAt = 100L, lastObservedAt = 200L,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_CONFIRMED,
            status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.ACTIVE,
            expiresAt = null, createdAt = 100L, updatedAt = 200L
        )
        val memUnconf = com.sanket_satpute_20.ironmind.domain.model.Memory(
            id = "mem-2", userId = userId, type = "hypothesis", content = "Drinks matcha",
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.AI,
            confidence = 0.7f, evidenceCount = 1, firstObservedAt = 100L, lastObservedAt = 200L,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.UNCONFIRMED,
            status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.ACTIVE,
            expiresAt = null, createdAt = 100L, updatedAt = 200L
        )
        val memRej = com.sanket_satpute_20.ironmind.domain.model.Memory(
            id = "mem-3", userId = userId, type = "hypothesis", content = "Refuted habit",
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.AI,
            confidence = 0.5f, evidenceCount = 1, firstObservedAt = 100L, lastObservedAt = 200L,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_REJECTED,
            status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.INACTIVE,
            expiresAt = null, createdAt = 100L, updatedAt = 200L
        )
        val memForeign = com.sanket_satpute_20.ironmind.domain.model.Memory(
            id = "mem-foreign", userId = "other-user", type = "fact", content = "Other data",
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            confidence = 1.0f, evidenceCount = 1, firstObservedAt = 100L, lastObservedAt = 200L,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_CONFIRMED,
            status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.ACTIVE,
            expiresAt = null, createdAt = 100L, updatedAt = 200L
        )

        val patConf = com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern(
            id = "pat-1", userId = userId, fingerprint = "fp1",
            type = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType.TIME_PATTERN,
            description = "High energy mornings", conditions = null, predictedBehavior = null,
            confidence = 0.85f, evidenceCount = 4, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L,
            status = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus.ACTIVE,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 100L, updatedAt = 200L
        )
        val patRej = com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern(
            id = "pat-2", userId = userId, fingerprint = "fp2",
            type = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType.DISTRACTION_PATTERN,
            description = "Rejected pattern", conditions = null, predictedBehavior = null,
            confidence = 0.5f, evidenceCount = 1, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L,
            status = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus.ACTIVE,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_REJECTED,
            createdAt = 100L, updatedAt = 200L
        )

        val snapshot = com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot(
            timestamp = 1000L,
            dayOfWeek = 3,
            activeCommitments = emptyList(),
            recentEvents = emptyList(),
            factualContextSnapshot = com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                appUsage = null, activity = null, calendar = null, location = null, notifications = null
            ),
            recentReflections = emptyList(),
            recentPatterns = listOf(patConf, patRej),
            activeProtectionSession = null,
            activeGoals = emptyList(),
            activeMemories = listOf(memConf, memUnconf, memRej, memForeign)
        )

        val request = AIRequest(
            userId = userId,
            input = "Test input",
            requestType = AIRequestType.PLAN_GENERATION,
            contextSnapshot = snapshot
        )

        val bodyJson = CloudflareWorkerIronMindAI.buildRequestBody(request)
        val obj = org.json.JSONObject(bodyJson)
        val snapshotObj = obj.getJSONObject("contextSnapshot")
        val memoriesArr = snapshotObj.getJSONArray("activeMemories")

        assertEquals(2, memoriesArr.length())

        val m1 = memoriesArr.getJSONObject(0)
        assertEquals("mem-1", m1.getString("id"))
        assertEquals("Likes early morning workouts", m1.getString("content"))
        assertEquals("USER_CONFIRMED", m1.getString("confirmationState"))

        val m2 = memoriesArr.getJSONObject(1)
        assertEquals("mem-2", m2.getString("id"))
        assertEquals("Drinks matcha", m2.getString("content"))
        assertEquals("UNCONFIRMED", m2.getString("confirmationState"))

        val patternsArr = snapshotObj.getJSONArray("recentPatterns")
        assertEquals(1, patternsArr.length())
        val p1 = patternsArr.getJSONObject(0)
        assertEquals("pat-1", p1.getString("id"))
        assertEquals("TIME_PATTERN", p1.getString("type"))
        assertEquals("High energy mornings", p1.getString("description"))
        assertEquals("USER_CONFIRMED", p1.getString("confirmationState"))
    }
}

