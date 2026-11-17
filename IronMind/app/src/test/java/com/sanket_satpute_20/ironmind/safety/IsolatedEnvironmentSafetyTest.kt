package com.sanket_satpute_20.ironmind.safety

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.BuildConfig
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerDiagnostic
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerIronMindAI
import com.sanket_satpute_20.ironmind.data.ai.DisabledIronMindAI
import com.sanket_satpute_20.ironmind.di.AIProviderType
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.json.JSONObject
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IsolatedEnvironmentSafetyTest {

    @Test
    fun `validateEndpointSafety blocks remote workers dev domains in debug mode`() {
        val ex = assertThrows(SecurityException::class.java) {
            CloudflareWorkerIronMindAI.validateEndpointSafety("https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev/v1/process")
        }
        assertTrue(ex.message?.contains("FAIL-CLOSED") == true)
        assertTrue(ex.message?.contains("strictly blocked in debug builds") == true)
    }

    @Test
    fun `validateEndpointSafety allows local loopback and emulator endpoints`() {
        // These should not throw
        CloudflareWorkerIronMindAI.validateEndpointSafety("http://10.0.2.2:8787/v1/process")
        CloudflareWorkerIronMindAI.validateEndpointSafety("http://localhost:8787/v1/process")
        CloudflareWorkerIronMindAI.validateEndpointSafety("http://127.0.0.1:8787/v1/process")
    }

    @Test
    fun `DisabledIronMindAI fails closed without invoking remote AI services`() = runBlocking {
        val ai = DisabledIronMindAI()
        val request = AIRequest(
            userId = "test-user-123",
            input = "Plan my day",
            requestType = AIRequestType.PLAN_GENERATION
        )
        val result = ai.process(request)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is SecurityException)
        assertTrue(error.message?.contains("AI_INFERENCE_DISABLED") == true)
    }

    @Test
    fun `CloudflareWorkerDiagnostic fails closed when production inference test is triggered on remote url`() = runBlocking {
        val diagnostic = CloudflareWorkerDiagnostic(
            context = ApplicationProvider.getApplicationContext(),
            productionUrl = "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev",
            isDebug = true
        )

        val result = diagnostic.testSingleProductionInference()
        assertFalse(result.isSuccess)
        assertEquals(403, result.statusCode)
        assertTrue(result.errorMessage?.contains("FAIL-CLOSED") == true)
    }

    @Test
    fun `DefaultAppContainer uses isolated debug database and disabled AI provider by default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = DefaultAppContainer(context)

        // Verify AI provider is fail-closed DisabledIronMindAI
        assertTrue(container.ironMindAI is DisabledIronMindAI)

        // Verify database name is isolated debug database
        val openHelper = container.database.openHelper
        assertEquals("ironmind_database_debug", openHelper.databaseName)
    }

    @Test
    fun `CloudflareWorkerDiagnostic checkWorkerHealthConnectivity fails closed when healthUrl is remote and httpGetExecutor is null`() = runBlocking {
        val diagnostic = CloudflareWorkerDiagnostic(
            context = ApplicationProvider.getApplicationContext(),
            healthUrl = CloudflareWorkerDiagnostic.DEFAULT_HEALTH_URL,
            isDebug = true,
            firebaseInitCheck = { true },
            authUserProvider = { Pair(true, "mock-user") },
            idTokenCheck = { Pair(true, 500) },
            appCheckTokenCheck = { Pair(true, 200) },
            httpGetExecutor = null // Real network dispatch path
        )

        val report = diagnostic.runDiagnostic()

        assertFalse("Health step must fail when pointing to remote production endpoint", report.workerHealthStep.isSuccess)
        assertTrue(
            "Message must indicate fail-closed block without network dispatch",
            report.workerHealthStep.message.contains("FAIL-CLOSED")
        )
        assertTrue(
            "Message must cite the blocked remote endpoint",
            report.workerHealthStep.message.contains(CloudflareWorkerDiagnostic.DEFAULT_HEALTH_URL)
        )
    }

    @Test
    fun `CloudflareWorkerDiagnostic verifyWorkerAuth fails closed when targetUrl is remote and httpPostExecutor is null`() = runBlocking {
        val diagnostic = CloudflareWorkerDiagnostic(
            context = ApplicationProvider.getApplicationContext(),
            authVerifyUrl = "https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev/v1/auth/verify",
            isDebug = true,
            firebaseInitCheck = { true },
            rawIdTokenProvider = { "mock-token" },
            httpPostExecutor = null // Real network dispatch path
        )

        val result = diagnostic.verifyWorkerAuth()

        assertFalse("Auth verify step must fail when pointing to remote production endpoint", result.isSuccess)
        assertEquals(403, result.statusCode)
        assertTrue("Result message must indicate fail-closed block", result.message.contains("FAIL-CLOSED"))
    }

    @Test
    fun `CloudflareWorkerDiagnostic isEndpointAllowed correctly identifies local vs remote endpoints`() {
        val diagnostic = CloudflareWorkerDiagnostic(isDebug = true)

        assertTrue(diagnostic.isEndpointAllowed("http://10.0.2.2:8787/health"))
        assertTrue(diagnostic.isEndpointAllowed("http://localhost:8787/health"))
        assertTrue(diagnostic.isEndpointAllowed("http://127.0.0.1:8787/health"))

        assertFalse(diagnostic.isEndpointAllowed("https://ironmind-ai-worker-production.sanket-satpute-dev.workers.dev/health"))
        assertFalse(diagnostic.isEndpointAllowed("https://example.com/health"))
    }

    @Test
    fun `Firebase Emulator configuration uses dedicated non-conflicting ports in BuildConfig and matches firebase_json`() {
        assertTrue("USE_FIREBASE_EMULATORS must be true in debug", BuildConfig.USE_FIREBASE_EMULATORS)
        assertEquals("127.0.0.1", BuildConfig.FIREBASE_EMULATOR_HOST)
        assertEquals(9199, BuildConfig.FIREBASE_AUTH_EMULATOR_PORT)
        assertEquals(8180, BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT)

        // Strict verification: must NOT use Daily Battle's occupied ports (Auth 9099, Firestore 8080)
        assertNotEquals(9099, BuildConfig.FIREBASE_AUTH_EMULATOR_PORT)
        assertNotEquals(8080, BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT)
    }

    @Test
    fun `DefaultAppContainer authRepository fails closed and never connects to production`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = DefaultAppContainer(context)

        val result = container.authRepository.signInAnonymously()
        assertTrue("Sign in must fail closed when emulator is unavailable", result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(
            "Error message must indicate emulator failure and strictly blocked production fallback",
            ex.message?.contains("Local Firebase Auth emulator unavailable") == true ||
                ex.message?.contains("No Firebase in test environment") == true
        )
    }

    @Test
    fun `DefaultAppContainer syncRepository fails closed and never connects to production`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = DefaultAppContainer(context)

        val entry = OutboxEntry(
            operationId = "op-safety-test",
            entityType = "Goal",
            entityId = "goal-1",
            operationType = OutboxOperationType.UPSERT,
            payload = "{\"id\":\"goal-1\"}",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING
        )

        val result = container.syncRepository.upload("test-user-safety", entry)
        assertTrue("Upload must fail closed when emulator is unavailable", result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(
            "Error message must indicate failure without connecting to production Firestore",
            ex.message?.contains("Local Firestore emulator unavailable") == true ||
                ex.message?.contains("Failed to configure Firestore emulator") == true ||
                ex.message?.contains("failed") == true ||
                ex.message?.contains("UNAVAILABLE") == true
        )
    }

    private fun findWorkspaceRoot(): File {
        var current = File(".").canonicalFile
        while (current != null) {
            if (File(current, "firebase.json").exists()) {
                return current
            }
            val parent = current.parentFile ?: break
            current = parent
        }
        throw IllegalStateException("Could not find workspace root containing firebase.json from ${File(".").canonicalPath}")
    }

    @Test
    fun `firebase_json specifies firestore rules file`() {
        val root = findWorkspaceRoot()
        val configFile = File(root, "firebase.json")
        assertTrue("firebase.json must exist in workspace root", configFile.exists())

        val json = JSONObject(configFile.readText())
        assertTrue("firebase.json must declare a 'firestore' configuration block", json.has("firestore"))
        val firestoreConfig = json.getJSONObject("firestore")
        assertTrue("firestore block must declare 'rules'", firestoreConfig.has("rules"))
        assertEquals("firestore.rules", firestoreConfig.getString("rules"))
    }

    @Test
    fun `firestore_rules enforces deny-by-default and strict user isolation`() {
        val root = findWorkspaceRoot()
        val rulesFile = File(root, "firestore.rules")
        assertTrue("firestore.rules must exist in workspace root", rulesFile.exists())

        val rules = rulesFile.readText()
        assertTrue("Rules must declare rules_version = '2'", rules.contains("rules_version = '2';"))

        // Must NOT contain permissive allow-all rules
        assertFalse("Rules must not contain permissive read/write", rules.contains("allow read, write: if true;"))
        assertFalse("Rules must not contain permissive read", rules.contains("allow read: if true;"))
        assertFalse("Rules must not contain permissive write", rules.contains("allow write: if true;"))

        // Must enforce user ownership
        assertTrue("Rules must enforce user ownership via request.auth.uid", rules.contains("request.auth.uid == userId"))
        assertTrue("Rules must scope access to user directory", rules.contains("match /users/{userId}"))

        // Must restrict subcollections to known sync entities
        assertTrue("Rules must restrict to 'goals'", rules.contains("'goals'"))
        assertTrue("Rules must restrict to 'plans'", rules.contains("'plans'"))
        assertTrue("Rules must restrict to 'tasks'", rules.contains("'tasks'"))
        assertTrue("Rules must restrict to 'commitments'", rules.contains("'commitments'"))
        assertTrue("Rules must restrict to 'reflections'", rules.contains("'reflections'"))
        assertTrue("Rules must restrict to 'memories'", rules.contains("'memories'"))

        // Must contain explicit fallback deny-all
        assertTrue("Rules must contain explicit fallback deny-all", rules.contains("allow read, write: if false;"))
    }

    @Test
    fun `build_gradle_kts release configuration never targets local emulators`() {
        val root = findWorkspaceRoot()
        val buildGradle = File(root, "IronMind/app/build.gradle.kts")
        assertTrue("build.gradle.kts must exist", buildGradle.exists())
        val content = buildGradle.readText()
        val releaseIndex = content.indexOf("release {")
        assertTrue("release block must exist in build.gradle.kts", releaseIndex != -1)
        val releaseContent = content.substring(releaseIndex)
        assertTrue(releaseContent.contains("buildConfigField(\"boolean\", \"USE_FIREBASE_EMULATORS\", \"false\")"))
        assertTrue(releaseContent.contains("buildConfigField(\"String\", \"FIREBASE_EMULATOR_HOST\", \"\\\"\\\"\")"))
        assertTrue(releaseContent.contains("buildConfigField(\"int\", \"FIREBASE_AUTH_EMULATOR_PORT\", \"0\")"))
        assertTrue(releaseContent.contains("buildConfigField(\"int\", \"FIREBASE_FIRESTORE_EMULATOR_PORT\", \"0\")"))
    }
}

