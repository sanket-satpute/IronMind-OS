package com.sanket_satpute_20.ironmind.data.ai

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.sanket_satpute_20.ironmind.BuildConfig
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Diagnostic step result representation.
 * Explicitly excludes raw token strings to prevent security leaks.
 */
data class DiagnosticStep(
    val name: String,
    val isSuccess: Boolean,
    val message: String
)

/**
 * Comprehensive diagnostic report covering all verification stages.
 */
data class CloudflareWorkerDiagnosticReport(
    val firebaseInitStep: DiagnosticStep,
    val anonymousAuthStep: DiagnosticStep,
    val idTokenStep: DiagnosticStep,
    val appCheckStep: DiagnosticStep,
    val workerHealthStep: DiagnosticStep,
    val allPassed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of direct authentication verification against the local Worker's /v1/auth/verify endpoint.
 * Explicitly excludes raw token strings to prevent security leaks.
 */
data class AuthVerificationResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val message: String
)

/**
 * Result of a single controlled production inference test against /v1/process.
 * Redacts tokens and credentials; captures HTTP status, parsed intent, and rate-limit headers.
 */
data class ProductionInferenceResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val intentDescription: String? = null,
    val confidence: Float? = null,
    val reasoning: String? = null,
    val remainingMinute: String? = null,
    val remainingDaily: String? = null,
    val cfRay: String? = null,
    val errorMessage: String? = null
)

/**
 * Safe, debug-only diagnostic utility for verifying Firebase setup, authentication,
 * App Check attestation, and Cloudflare Worker health and inference connectivity.
 *
 * Strict Constraints:
 * - Available ONLY in debug builds (BuildConfig.DEBUG == true).
 * - Never prints, persists, displays, or exposes raw Firebase Auth ID tokens or App Check tokens.
 */
class CloudflareWorkerDiagnostic(
    private val context: Context? = null,
    private val healthUrl: String = DEFAULT_HEALTH_URL,
    private val authVerifyUrl: String = DEFAULT_AUTH_VERIFY_URL,
    private val productionUrl: String = DEFAULT_PRODUCTION_URL,
    private val isDebug: Boolean = BuildConfig.DEBUG,
    private val firebaseInitCheck: (() -> Boolean)? = null,
    private val authUserProvider: (suspend () -> Pair<Boolean, String?>)? = null,
    private val idTokenCheck: (suspend () -> Pair<Boolean, Int?>)? = null,
    private val appCheckTokenCheck: (suspend () -> Pair<Boolean, Int?>)? = null,
    private val rawIdTokenProvider: (suspend () -> String?)? = null,
    private val rawAppCheckTokenProvider: (suspend () -> String?)? = null,
    private val httpGetExecutor: (suspend (String) -> Pair<Int, String>)? = null,
    private val httpPostExecutor: (suspend (String, Map<String, String>) -> Pair<Int, String>)? = null,
    private val productionInferenceTransport: (suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null,
    private val productionInferenceAuthUidProvider: (suspend () -> String?)? = null
) {

    suspend fun runDiagnostic(): CloudflareWorkerDiagnosticReport = withContext(Dispatchers.IO) {
        if (!isDebug) {
            val disabledStep = DiagnosticStep(
                name = "Environment Check",
                isSuccess = false,
                message = "Diagnostics are strictly disabled in release builds."
            )
            return@withContext CloudflareWorkerDiagnosticReport(
                firebaseInitStep = disabledStep,
                anonymousAuthStep = disabledStep,
                idTokenStep = disabledStep,
                appCheckStep = disabledStep,
                workerHealthStep = disabledStep,
                allPassed = false
            )
        }

        println("CloudflareWorkerDiagnostic [START]: Beginning connectivity and auth verification...")

        // Step 1: Firebase Initialization
        val firebaseInitStep = checkFirebaseInitialization()
        println("CloudflareWorkerDiagnostic [STEP 1 - Firebase Init]: success=${firebaseInitStep.isSuccess} (${firebaseInitStep.message})")

        // Step 2: Anonymous Sign-In (check existing or authenticate)
        val anonymousAuthStep = if (firebaseInitStep.isSuccess) {
            checkAnonymousAuth()
        } else {
            DiagnosticStep("Anonymous Sign-In", false, "Skipped due to uninitialized Firebase.")
        }
        println("CloudflareWorkerDiagnostic [STEP 2 - Anonymous Auth]: success=${anonymousAuthStep.isSuccess} (${anonymousAuthStep.message})")

        // Step 3: Firebase ID-Token Acquisition
        val idTokenStep = if (anonymousAuthStep.isSuccess) {
            checkIdTokenAcquisition()
        } else {
            DiagnosticStep("ID-Token Acquisition", false, "Skipped due to missing authenticated Firebase user.")
        }
        println("CloudflareWorkerDiagnostic [STEP 3 - ID Token]: success=${idTokenStep.isSuccess} (${idTokenStep.message})")

        // Step 4: Firebase App Check Token Acquisition
        val appCheckStep = if (firebaseInitStep.isSuccess) {
            checkAppCheckTokenAcquisition()
        } else {
            DiagnosticStep("App Check Token Acquisition", false, "Skipped due to uninitialized Firebase.")
        }
        println("CloudflareWorkerDiagnostic [STEP 4 - App Check Token]: success=${appCheckStep.isSuccess} (${appCheckStep.message})")

        // Step 5: Worker Health Connectivity (GET /health only, never inference)
        val workerHealthStep = checkWorkerHealthConnectivity()
        println("CloudflareWorkerDiagnostic [STEP 5 - Worker Health]: success=${workerHealthStep.isSuccess} (${workerHealthStep.message})")

        val allPassed = firebaseInitStep.isSuccess &&
                anonymousAuthStep.isSuccess &&
                idTokenStep.isSuccess &&
                appCheckStep.isSuccess &&
                workerHealthStep.isSuccess

        println("CloudflareWorkerDiagnostic [FINISHED]: allPassed=$allPassed")

        CloudflareWorkerDiagnosticReport(
            firebaseInitStep = firebaseInitStep,
            anonymousAuthStep = anonymousAuthStep,
            idTokenStep = idTokenStep,
            appCheckStep = appCheckStep,
            workerHealthStep = workerHealthStep,
            allPassed = allPassed
        )
    }

    private fun checkFirebaseInitialization(): DiagnosticStep {
        return try {
            val isInit = if (firebaseInitCheck != null) {
                firebaseInitCheck.invoke()
            } else {
                context?.let { FirebaseApp.getApps(it).isNotEmpty() } ?: FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()
            }
            if (isInit) {
                DiagnosticStep("Firebase Initialization", true, "FirebaseApp is initialized successfully.")
            } else {
                DiagnosticStep("Firebase Initialization", false, "FirebaseApp has not been initialized.")
            }
        } catch (e: Exception) {
            DiagnosticStep("Firebase Initialization", false, "Firebase initialization error: ${e.message}")
        }
    }

    private suspend fun checkAnonymousAuth(): DiagnosticStep {
        return try {
            if (authUserProvider != null) {
                val (success, uid) = authUserProvider.invoke()
                if (success) {
                    DiagnosticStep("Anonymous Sign-In", true, "Authenticated user present (uid: $uid).")
                } else {
                    DiagnosticStep("Anonymous Sign-In", false, "User sign-in failed.")
                }
            } else {
                val auth = FirebaseAuth.getInstance()
                var user = auth.currentUser
                if (user == null) {
                    val authResult = auth.signInAnonymously().await()
                    user = authResult.user
                }
                if (user != null) {
                    DiagnosticStep(
                        "Anonymous Sign-In",
                        true,
                        "Authenticated user active (uid: ${user.uid}, isAnonymous: ${user.isAnonymous})."
                    )
                } else {
                    DiagnosticStep("Anonymous Sign-In", false, "Sign-in succeeded but currentUser was null.")
                }
            }
        } catch (e: Exception) {
            DiagnosticStep("Anonymous Sign-In", false, "Anonymous authentication failed: ${e.message}")
        }
    }

    private suspend fun checkIdTokenAcquisition(): DiagnosticStep {
        return try {
            if (idTokenCheck != null) {
                val (success, tokenLength) = idTokenCheck.invoke()
                if (success && tokenLength != null && tokenLength > 0) {
                    DiagnosticStep(
                        "ID-Token Acquisition",
                        true,
                        "Firebase ID token acquired successfully (token length: $tokenLength chars, content redacted)."
                    )
                } else {
                    DiagnosticStep("ID-Token Acquisition", false, "Retrieved ID token was null or empty.")
                }
            } else {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser ?: return DiagnosticStep("ID-Token Acquisition", false, "Current user is null.")
                val tokenResult = user.getIdToken(false).await()
                val token = tokenResult.token
                if (!token.isNullOrBlank()) {
                    DiagnosticStep(
                        "ID-Token Acquisition",
                        true,
                        "Firebase ID token acquired successfully (token length: ${token.length} chars, content redacted)."
                    )
                } else {
                    DiagnosticStep("ID-Token Acquisition", false, "Firebase returned a null or empty ID token.")
                }
            }
        } catch (e: Exception) {
            DiagnosticStep("ID-Token Acquisition", false, "Failed to retrieve Firebase ID token: ${e.message}")
        }
    }

    private suspend fun checkAppCheckTokenAcquisition(): DiagnosticStep {
        return try {
            if (appCheckTokenCheck != null) {
                val (success, tokenLength) = appCheckTokenCheck.invoke()
                if (success && tokenLength != null && tokenLength > 0) {
                    DiagnosticStep(
                        "App Check Token Acquisition",
                        true,
                        "App Check token acquired successfully (token length: $tokenLength chars, content redacted)."
                    )
                } else {
                    DiagnosticStep("App Check Token Acquisition", false, "App Check token was null or empty.")
                }
            } else {
                val appCheck = FirebaseAppCheck.getInstance()
                val tokenResult = appCheck.getAppCheckToken(false).await()
                val token = tokenResult?.token
                if (!token.isNullOrBlank()) {
                    DiagnosticStep(
                        "App Check Token Acquisition",
                        true,
                        "App Check token acquired successfully (token length: ${token.length} chars, content redacted)."
                    )
                } else {
                    DiagnosticStep("App Check Token Acquisition", false, "FirebaseAppCheck returned a null or empty token.")
                }
            }
        } catch (e: Exception) {
            DiagnosticStep("App Check Token Acquisition", false, "App Check token retrieval error: ${e.message}")
        }
    }

    internal fun isEndpointAllowed(url: String): Boolean {
        if (!isDebug || com.sanket_satpute_20.ironmind.BuildConfig.ALLOW_REMOTE_INFERENCE) {
            return true
        }
        val uri = try { java.net.URI(url) } catch (_: Throwable) { null }
        val host = uri?.host?.lowercase() ?: ""
        return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
    }

    private suspend fun checkWorkerHealthConnectivity(): DiagnosticStep {
        if (!isEndpointAllowed(healthUrl) && httpGetExecutor == null) {
            val msg = "FAIL-CLOSED: Health check to remote endpoint '$healthUrl' is strictly blocked in debug validation builds."
            CloudflareWorkerIronMindAI.safeLog("CloudflareWorkerDiagnostic", msg, isError = true)
            return DiagnosticStep(
                "Worker Health Connectivity",
                false,
                msg
            )
        }

        return try {
            val (statusCode, body) = if (httpGetExecutor != null) {
                httpGetExecutor.invoke(healthUrl)
            } else {
                defaultHttpGet(healthUrl)
            }

            if (statusCode == 200) {
                val json = try {
                    JSONObject(body)
                } catch (e: Exception) {
                    null
                }
                val status = json?.optString("status") ?: "unknown"
                val model = json?.optString("model") ?: "unknown"
                DiagnosticStep(
                    "Worker Health Connectivity",
                    true,
                    "Connected to $healthUrl (HTTP 200). Worker reports status='$status', model='$model'."
                )
            } else {
                DiagnosticStep(
                    "Worker Health Connectivity",
                    false,
                    "Worker returned non-200 HTTP code: $statusCode."
                )
            }
        } catch (e: Exception) {
            DiagnosticStep(
                "Worker Health Connectivity",
                false,
                "Could not reach Worker at $healthUrl: ${e.message}."
            )
        }
    }

    private fun defaultHttpGet(urlStr: String): Pair<Int, String> {
        if (!isEndpointAllowed(urlStr)) {
            throw SecurityException("FAIL-CLOSED: Outbound HTTP GET to remote endpoint '$urlStr' is strictly blocked in debug builds.")
        }
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sends the current Firebase ID token and App Check token directly from memory
     * to the local Worker's /v1/auth/verify endpoint.
     *
     * Strict Constraints:
     * - Disabled completely in release builds.
     * - Never logs, persists, or displays raw tokens.
     * - Never invokes /v1/process, AI inference, D1, or production endpoints.
     */
    suspend fun verifyWorkerAuth(
        targetUrl: String = authVerifyUrl
    ): AuthVerificationResult = withContext(Dispatchers.IO) {
        if (!isDebug) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Auth verification is strictly disabled in release builds."
            )
        }

        if (!isEndpointAllowed(targetUrl) && httpPostExecutor == null) {
            val msg = "FAIL-CLOSED: Auth verification to remote endpoint '$targetUrl' is strictly blocked in debug validation builds."
            println("CloudflareWorkerDiagnostic [AUTH_VERIFY_BLOCKED]: $msg")
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 403,
                message = msg
            )
        }

        println("CloudflareWorkerDiagnostic [AUTH_VERIFY_START]: Verifying credentials with $targetUrl...")

        // Step 1: Firebase Initialization Check
        val isFirebaseInit = try {
            if (firebaseInitCheck != null) {
                firebaseInitCheck.invoke()
            } else {
                context?.let { FirebaseApp.getApps(it).isNotEmpty() }
                    ?: FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }

        if (!isFirebaseInit) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "FirebaseApp is not initialized."
            )
        }

        // Step 2: Obtain ID token directly in memory
        val idToken: String? = try {
            if (rawIdTokenProvider != null) {
                rawIdTokenProvider.invoke()
            } else {
                val auth = FirebaseAuth.getInstance()
                var user = auth.currentUser
                if (user == null) {
                    val authResult = auth.signInAnonymously().await()
                    user = authResult.user
                }
                user?.getIdToken(false)?.await()?.token
            }
        } catch (e: Exception) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Failed to acquire Firebase ID token: ${e.message}."
            )
        }

        if (idToken.isNullOrBlank()) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Firebase ID token was null or empty."
            )
        }

        // Step 3: Obtain App Check token directly in memory
        val appCheckToken: String? = try {
            if (rawAppCheckTokenProvider != null) {
                rawAppCheckTokenProvider.invoke()
            } else {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.getAppCheckToken(false).await()?.token
            }
        } catch (e: Exception) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Failed to acquire App Check token: ${e.message}."
            )
        }

        if (appCheckToken.isNullOrBlank()) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Firebase App Check token was null or empty."
            )
        }

        // Step 4: Execute HTTP POST to targetUrl with authentication headers directly from memory
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Authorization" to "Bearer $idToken",
            "X-Firebase-AppCheck" to appCheckToken
        )

        return@withContext try {
            val (statusCode, responseBody) = if (httpPostExecutor != null) {
                httpPostExecutor.invoke(targetUrl, headers)
            } else {
                defaultHttpPost(targetUrl, headers)
            }

            if (statusCode == 200) {
                println("CloudflareWorkerDiagnostic [AUTH_VERIFY_SUCCESS]: HTTP 200 from $targetUrl.")
                AuthVerificationResult(
                    isSuccess = true,
                    statusCode = 200,
                    message = "Worker successfully verified ID token and App Check token (HTTP 200)."
                )
            } else {
                val errorCode = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("code")
                } catch (e: Exception) {
                    null
                }
                val reason = if (!errorCode.isNullOrBlank()) " ($errorCode)" else ""
                println("CloudflareWorkerDiagnostic [AUTH_VERIFY_FAILED]: HTTP $statusCode from $targetUrl$reason.")
                AuthVerificationResult(
                    isSuccess = false,
                    statusCode = statusCode,
                    message = "Worker rejected verification with HTTP $statusCode$reason."
                )
            }
        } catch (e: Exception) {
            println("CloudflareWorkerDiagnostic [AUTH_VERIFY_ERROR]: Could not reach $targetUrl: ${e.message}.")
            AuthVerificationResult(
                isSuccess = false,
                statusCode = 0,
                message = "Could not reach Worker at $targetUrl: ${e.message}."
            )
        }
    }

    private fun defaultHttpPost(urlStr: String, headers: Map<String, String>): Pair<Int, String> {
        if (!isEndpointAllowed(urlStr)) {
            throw SecurityException("FAIL-CLOSED: Outbound HTTP POST to remote endpoint '$urlStr' is strictly blocked in debug builds.")
        }
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            useCaches = false
            doInput = true
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
            setFixedLengthStreamingMode(0)
            doOutput = true
        }

        return try {
            conn.outputStream.close()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sends exactly one INTENT_EXTRACTION request to the production Cloudflare Worker
     * using the app's in-process Firebase Auth and App Check token flow.
     *
     * Strict Boundaries:
     * - Disabled completely in release builds (BuildConfig.DEBUG == true only).
     * - Retries are strictly disabled (maxRetries = 0).
     * - Never logs, persists, or returns raw tokens or secrets.
     * - Captures HTTP status, rate-limit response headers, and parsed AIOutput.
     */
    suspend fun testSingleProductionInference(
        targetUrl: String = productionUrl
    ): ProductionInferenceResult = withContext(Dispatchers.IO) {
        if (!isDebug) {
            return@withContext ProductionInferenceResult(
                isSuccess = false,
                statusCode = 0,
                errorMessage = "Production inference test is strictly disabled in release builds."
            )
        }

        if (!com.sanket_satpute_20.ironmind.BuildConfig.ALLOW_REMOTE_INFERENCE && productionInferenceTransport == null) {
            val uri = try { java.net.URI(targetUrl) } catch (_: Throwable) { null }
            val host = uri?.host?.lowercase() ?: ""
            val isLocal = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            if (!isLocal) {
                CloudflareWorkerIronMindAI.safeLog("CloudflareWorkerDiagnostic", "PRODUCTION_INFERENCE_BLOCKED: Calling remote endpoint $targetUrl is strictly blocked in debug builds.", isError = true)
                return@withContext ProductionInferenceResult(
                    isSuccess = false,
                    statusCode = 403,
                    errorMessage = "FAIL-CLOSED: Production inference call to $targetUrl is blocked in debug validation builds."
                )
            }
        }

        CloudflareWorkerIronMindAI.safeLog("CloudflareWorkerDiagnostic", "PRODUCTION_INFERENCE_START: Target=$targetUrl")

        var capturedStatusCode = 0
        var capturedHeaders: Map<String, List<String>> = emptyMap()

        val capturingTransport: suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String> = { urlStr, headers, body ->
            if (productionInferenceTransport != null) {
                val pair = productionInferenceTransport.invoke(urlStr, headers, body)
                capturedStatusCode = pair.first
                pair
            } else {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 45000
                    doInput = true
                    doOutput = true
                    useCaches = false
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }

                try {
                    conn.outputStream.use { os ->
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        os.write(bytes)
                        os.flush()
                    }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                    val respBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    capturedStatusCode = code
                    capturedHeaders = conn.headerFields
                    Pair(code, respBody)
                } finally {
                    conn.disconnect()
                }
            }
        }

        val workerAi = CloudflareWorkerIronMindAI(
            endpointUrl = targetUrl,
            maxRetries = 0, // STRICTLY ZERO RETRIES
            requireAppCheck = true,
            idTokenProvider = if (rawIdTokenProvider != null) { { _ -> rawIdTokenProvider.invoke() ?: "" } } else null,
            appCheckTokenProvider = if (rawAppCheckTokenProvider != null) { { _ -> rawAppCheckTokenProvider.invoke() } } else null,
            httpTransport = capturingTransport
        )

        val uid = if (productionInferenceAuthUidProvider != null) {
            productionInferenceAuthUidProvider.invoke()
        } else {
            val auth = FirebaseAuth.getInstance()
            var user = auth.currentUser
            if (user == null) {
                val authResult = auth.signInAnonymously().await()
                user = authResult.user
            }
            user?.uid
        } ?: return@withContext ProductionInferenceResult(
            isSuccess = false,
            statusCode = 0,
            errorMessage = "No authenticated user UID available."
        )

        val request = AIRequest(
            userId = uid,
            input = "I want to improve my daily consistency.",
            requestType = AIRequestType.INTENT_EXTRACTION,
            contextSnapshot = null
        )

        val result = workerAi.process(request)

        val remMinute = capturedHeaders["X-RateLimit-Remaining-Minute"]?.firstOrNull()
            ?: capturedHeaders["x-ratelimit-remaining-minute"]?.firstOrNull()
        val remDaily = capturedHeaders["X-RateLimit-Remaining-Daily"]?.firstOrNull()
            ?: capturedHeaders["x-ratelimit-remaining-daily"]?.firstOrNull()
        val cfRay = capturedHeaders["cf-ray"]?.firstOrNull()
            ?: capturedHeaders["CF-RAY"]?.firstOrNull()

        when (result) {
            is Result.Success -> {
                val intent = result.data as? AIOutput.IntentOutput
                CloudflareWorkerIronMindAI.safeLog("CloudflareWorkerDiagnostic", "PRODUCTION_INFERENCE_SUCCESS: status=$capturedStatusCode, intent=${intent?.intentDescription}")
                ProductionInferenceResult(
                    isSuccess = true,
                    statusCode = capturedStatusCode.takeIf { it > 0 } ?: 200,
                    intentDescription = intent?.intentDescription,
                    confidence = intent?.confidence,
                    reasoning = intent?.reasoning,
                    remainingMinute = remMinute,
                    remainingDaily = remDaily,
                    cfRay = cfRay
                )
            }
            is Result.Failure -> {
                val ex = result.error
                val status = if (ex is CloudflareWorkerException) ex.statusCode else capturedStatusCode
                CloudflareWorkerIronMindAI.safeLog("CloudflareWorkerDiagnostic", "PRODUCTION_INFERENCE_FAILED: status=$status, error=${CloudflareWorkerIronMindAI.sanitizeErrorMessage(ex.message)}", isError = true)
                ProductionInferenceResult(
                    isSuccess = false,
                    statusCode = status,
                    errorMessage = ex.message,
                    remainingMinute = remMinute,
                    remainingDaily = remDaily,
                    cfRay = cfRay
                )
            }
        }
    }

    companion object {
        const val DEFAULT_HEALTH_URL = "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev/health"
        const val DEFAULT_AUTH_VERIFY_URL = "http://localhost:8787/v1/auth/verify"
        const val DEFAULT_PRODUCTION_URL = "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev"
    }
}
