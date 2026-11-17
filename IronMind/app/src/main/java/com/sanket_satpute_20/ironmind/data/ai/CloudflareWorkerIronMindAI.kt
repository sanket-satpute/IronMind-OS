package com.sanket_satpute_20.ironmind.data.ai

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Concrete implementation of IronMindAI that routes reasoning requests to the
 * Cloudflare Worker backend running Workers AI (@cf/google/gemma-4-26b-a4b-it).
 *
 * Security & Protocol:
 * - Obtains a fresh Firebase Auth ID token, anonymously authenticating if no user is present.
 * - Obtains a Firebase App Check token (attested via Play Integrity in release, Debug provider in debug).
 * - Sends POST /v1/process with:
 *     Authorization: Bearer <Firebase ID token>
 *     X-Firebase-AppCheck: <App Check token> (if available)
 * - Serializes AIRequest into JSON matching the Worker's AIRequestPayload schema.
 * - Handles token refresh retries on 401 / 403.
 * - Parses error responses (429 quota exhaustion, 502 malformed JSON, 504 timeout).
 * - Delegates domain validation to GeminiIronMindAI.parseAndValidateOutput to preserve behavioral parity.
 * - Never fabricates AI responses on failure.
 */
class CloudflareWorkerIronMindAI(
    private val endpointUrl: String = DEFAULT_LOCAL_DEV_URL,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val maxRetries: Int = 1,
    private val requireAppCheck: Boolean? = null,
    private val idTokenProvider: (suspend (forceRefresh: Boolean) -> String)? = null,
    private val appCheckTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
    private val httpTransport: (suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null,
    private val authUserProvider: (() -> String?)? = null
) : IronMindAI {

    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        val authenticatedUid = authUserProvider?.invoke() ?: try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Throwable) {
            null
        }

        val effectiveRequest = if (authenticatedUid != null && request.userId != authenticatedUid) {
            request.copy(userId = authenticatedUid)
        } else {
            request
        }

        val resolvedUrl = resolveEndpointUrl(endpointUrl)
        val body = buildRequestBody(effectiveRequest)

        var currentAttempt = 0
        var forceRefresh = false
        var lastException: Exception? = null

        while (currentAttempt <= maxRetries) {
            try {
                // 1. Obtain Firebase Auth ID token (guaranteed non-null, signing in anonymously if needed)
                val idToken = try {
                    if (idTokenProvider != null) {
                        idTokenProvider.invoke(forceRefresh)
                    } else {
                        defaultGetIdToken(forceRefresh)
                    }
                } catch (e: Exception) {
                    safeLog("CloudflareWorkerIronMindAI", "AUTH_FAILED: ${sanitizeErrorMessage(e.message)}", isError = true)
                    return Result.Failure(
                        CloudflareWorkerException("AUTH_ERROR", "Failed to obtain Firebase ID token: ${sanitizeErrorMessage(e.message)}", 401)
                    )
                }

                // 2. Obtain Firebase App Check token (safely handling when App Check is uninitialized or absent)
                var appCheckProviderError: Exception? = null
                var tokenReceived: String? = null
                val appCheckToken = try {
                    if (appCheckTokenProvider != null) {
                        tokenReceived = appCheckTokenProvider.invoke(forceRefresh)
                        tokenReceived
                    } else {
                        tokenReceived = defaultGetAppCheckToken(forceRefresh)
                        tokenReceived
                    }
                } catch (e: Exception) {
                    appCheckProviderError = e
                    safeLog(
                        "CloudflareWorkerIronMindAI",
                        "App Check token retrieval threw exception: ${e.javaClass.simpleName} - ${sanitizeErrorMessage(e.message)}",
                        isError = true
                    )
                    null
                }

                val mustEnforceAppCheck = shouldEnforceAppCheck(resolvedUrl, requireAppCheck)
                if (appCheckToken.isNullOrBlank()) {
                    if (mustEnforceAppCheck) {
                        val providerType = if (com.sanket_satpute_20.ironmind.BuildConfig.DEBUG) "DebugAppCheckProvider" else "PlayIntegrityAppCheckProvider"
                        val exType = appCheckProviderError?.javaClass?.simpleName ?: "None"
                        val exMsg = sanitizeErrorMessage(appCheckProviderError?.message) ?: "None"
                        val tokenReturned = if (tokenReceived != null) "returned empty/blank token (length=${tokenReceived.length})" else "null (no token returned)"

                        val diagnosticDetail = "[provider=$providerType, exception=$exType, message=$exMsg, tokenStatus=$tokenReturned]"
                        val errMsg = "Firebase App Check token acquisition failed or returned empty in production/enforced mode. Details: $diagnosticDetail"
                        safeLog("CloudflareWorkerIronMindAI", errMsg, isError = true)
                        return Result.Failure(
                            CloudflareWorkerException("APP_CHECK_TOKEN_MISSING", errMsg, 403)
                        )
                    } else {
                        safeLog("CloudflareWorkerIronMindAI", "Proceeding without App Check token for local development endpoint ($resolvedUrl).")
                    }
                }

                // 3. Assemble request headers
                val headers = mutableMapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "Authorization" to "Bearer $idToken"
                )
                if (!appCheckToken.isNullOrBlank()) {
                    headers["X-Firebase-AppCheck"] = appCheckToken
                }

                // 4. Execute HTTP POST
                val (statusCode, responseBody) = if (httpTransport != null) {
                    httpTransport.invoke(resolvedUrl, headers, body)
                } else {
                    defaultHttpTransport(resolvedUrl, headers, body, connectTimeoutMs, readTimeoutMs)
                }

                // 5. Successful response (HTTP 200)
                if (statusCode == 200) {
                    val output = GeminiIronMindAI.parseAndValidateOutput(responseBody, request.requestType)
                    return Result.Success(output)
                }

                // 6. Handle HTTP error from worker
                val errorException = parseWorkerError(statusCode, responseBody)
                println("CloudflareWorkerIronMindAI [HTTP_ERROR]: $statusCode - ${errorException.code}: ${errorException.message}")

                // Retry once with refreshed tokens if auth or App Check failed
                val isAuthOrAppCheckFailure = statusCode == 401 || statusCode == 403
                if (isAuthOrAppCheckFailure && currentAttempt < maxRetries) {
                    println("CloudflareWorkerIronMindAI: Retrying with force-refreshed tokens (attempt ${currentAttempt + 1})...")
                    forceRefresh = true
                    currentAttempt++
                    continue
                }

                // Non-retryable error (e.g. 429 quota exhaustion, 502 malformed model output, 504 timeout)
                return Result.Failure(errorException)

            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                lastException = e
                println("CloudflareWorkerIronMindAI [TRANSPORT_ERROR] attempt=$currentAttempt: ${e.message}")

                // Do not automatically retry socket read timeouts or transport failures that occur
                // after a request may have reached the Worker. Retrying could trigger duplicate AI model invocations.
                if (isNonRetryableTransportError(e)) {
                    return Result.Failure(e)
                }

                currentAttempt++
                if (currentAttempt <= maxRetries) {
                    delay(500L)
                }
            }
        }

        return Result.Failure(lastException ?: IOException("Cloudflare Worker request failed after retries"))
    }

    private suspend fun defaultGetIdToken(forceRefresh: Boolean): String {
        val auth = FirebaseAuth.getInstance()
        var user = auth.currentUser
        if (user == null) {
            println("CloudflareWorkerIronMindAI: No current Firebase user found. Performing anonymous sign-in...")
            val authResult = auth.signInAnonymously().await()
            user = authResult.user ?: throw CloudflareWorkerException(
                "AUTH_SIGN_IN_FAILED",
                "Firebase anonymous sign-in succeeded but current user was null.",
                401
            )
        }
        val tokenResult = user.getIdToken(forceRefresh).await()
        return tokenResult.token ?: throw CloudflareWorkerException(
            "AUTH_TOKEN_NULL",
            "Firebase ID token retrieval returned null.",
            401
        )
    }

    private suspend fun defaultGetAppCheckToken(forceRefresh: Boolean): String? {
        val appCheck = FirebaseAppCheck.getInstance()
        val result = appCheck.getAppCheckToken(forceRefresh).await()
        val token = result?.token
        if (token.isNullOrBlank()) {
            safeLog("CloudflareWorkerIronMindAI", "FirebaseAppCheck returned a null or empty token.")
        }
        return token
    }

    private suspend fun defaultHttpTransport(
        endpointUrl: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        validateEndpointSafety(endpointUrl)
        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            useCaches = false
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }

        try {
            connection.outputStream.use { os ->
                val bytes = body.toByteArray(Charsets.UTF_8)
                os.write(bytes, 0, bytes.size)
                os.flush()
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Pair(statusCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // Standard Android emulator loopback address pointing to host machine's localhost:8787
        const val DEFAULT_LOCAL_DEV_URL = "http://10.0.2.2:8787/v1/process"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15000
        const val DEFAULT_READ_TIMEOUT_MS = 30000
        const val DEFAULT_TIMEOUT_MS = 30000

        internal fun resolveEndpointUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            return when {
                trimmed.endsWith("/v1/process") -> trimmed
                trimmed.endsWith("/v1/process/") -> trimmed.removeSuffix("/")
                trimmed.endsWith("/") -> "${trimmed}v1/process"
                else -> "$trimmed/v1/process"
            }
        }

        internal fun isLocalDevelopmentUrl(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains("10.0.2.2") || lower.contains("localhost") || lower.contains("127.0.0.1")
        }

        internal fun shouldEnforceAppCheck(url: String, explicitRequireAppCheck: Boolean? = null): Boolean {
            if (explicitRequireAppCheck != null) {
                return explicitRequireAppCheck
            }
            if (!com.sanket_satpute_20.ironmind.BuildConfig.DEBUG) {
                return true
            }
            return !isLocalDevelopmentUrl(url)
        }

        internal fun buildRequestBody(request: AIRequest): String {
            val json = JSONObject()
            json.put("userId", request.userId)
            json.put("input", request.input)
            json.put("requestType", request.requestType.name)
            if (request.contextSnapshot != null) {
                val snapshotObj = JSONObject()
                snapshotObj.put("timestamp", request.contextSnapshot.timestamp)
                snapshotObj.put("dayOfWeek", request.contextSnapshot.dayOfWeek)
                val memoriesArray = JSONArray()
                request.contextSnapshot.activeMemories
                    .filter { memory ->
                        memory.userId == request.userId &&
                        memory.status !in setOf(MemoryStatus.EXPIRED, MemoryStatus.DELETED, MemoryStatus.INACTIVE) &&
                        memory.confirmationState != MemoryConfirmationState.USER_REJECTED
                    }
                    .forEach { mem ->
                        val memObj = JSONObject()
                        memObj.put("id", mem.id)
                        memObj.put("content", mem.content)
                        memObj.put("confidence", mem.confidence.toDouble())
                        memObj.put("confirmationState", mem.confirmationState.name)
                        memoriesArray.put(memObj)
                    }
                snapshotObj.put("activeMemories", memoriesArray)

                val patternsArray = JSONArray()
                request.contextSnapshot.recentPatterns
                    .filter { pattern ->
                        pattern.userId == request.userId &&
                        pattern.status == com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus.ACTIVE &&
                        pattern.confirmationState != MemoryConfirmationState.USER_REJECTED
                    }
                    .forEach { pat ->
                        val patObj = JSONObject()
                        patObj.put("id", pat.id)
                        patObj.put("type", pat.type.name)
                        patObj.put("description", pat.description)
                        patObj.put("confidence", pat.confidence.toDouble())
                        patObj.put("confirmationState", pat.confirmationState.name)
                        patternsArray.put(patObj)
                    }
                snapshotObj.put("recentPatterns", patternsArray)

                json.put("contextSnapshot", snapshotObj)
            } else {
                json.put("contextSnapshot", JSONObject.NULL)
            }
            return json.toString()
        }

        internal fun parseWorkerError(statusCode: Int, responseBody: String): CloudflareWorkerException {
            return try {
                val json = JSONObject(responseBody)
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optString("code") ?: "HTTP_$statusCode"
                val message = errorObj?.optString("message") ?: responseBody
                CloudflareWorkerException(code, message, statusCode)
            } catch (e: Exception) {
                CloudflareWorkerException(
                    "HTTP_$statusCode",
                    responseBody.ifEmpty { "HTTP $statusCode error" },
                    statusCode
                )
            }
        }

        internal fun isNonRetryableTransportError(e: Exception): Boolean {
            if (e is SocketTimeoutException || e is InterruptedIOException) {
                return true
            }
            val msg = e.message?.lowercase() ?: ""
            return msg.contains("timeout") || msg.contains("timed out")
        }

        internal fun safeLog(tag: String, message: String, isError: Boolean = false) {
            try {
                if (isError) {
                    android.util.Log.e(tag, message)
                } else {
                    android.util.Log.i(tag, message)
                }
            } catch (_: Throwable) {
                println("$tag: $message")
            }
        }

        internal fun sanitizeErrorMessage(message: String?): String? {
            if (message == null) return null
            return message
                .replace(Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"), "[REDACTED_JWT]")
                .replace(Regex("Bearer\\s+[a-zA-Z0-9._-]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
                .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[REDACTED_UUID]")
                .replace(Regex("AIza[0-9A-Za-z-_]{35}"), "[REDACTED_API_KEY]")
        }

        fun validateEndpointSafety(url: String) {
            if (com.sanket_satpute_20.ironmind.BuildConfig.DEBUG && !com.sanket_satpute_20.ironmind.BuildConfig.ALLOW_REMOTE_INFERENCE) {
                val uri = try { java.net.URI(url) } catch (_: Throwable) { null }
                val host = uri?.host?.lowercase() ?: ""
                val isLocal = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
                if (!isLocal) {
                    throw SecurityException("FAIL-CLOSED: Outbound call to remote inference endpoint '$url' is strictly blocked in debug builds.")
                }
            }
        }
    }
}

class CloudflareWorkerException(
    val code: String,
    override val message: String,
    val statusCode: Int
) : IOException("Cloudflare Worker Error ($statusCode) [$code]: $message")
