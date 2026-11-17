package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.sanket_satpute_20.ironmind.BuildConfig
import com.sanket_satpute_20.ironmind.data.repository.AuthRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.fail
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Phase 8.3.3.6A Part A Integration Tests.
 *
 * Verifies genuine Firebase Auth Emulator authentication, multi-device shared identity,
 * and Firestore Security Rules enforcement:
 * 1. Both clients authenticate with the same email/pwd and receive the identical genuine UID.
 * 2. Client 1 writes a document; Client 2 reads it successfully (same UID ownership).
 * 3. Client 3 authenticates as a different user and is strictly denied access by Firestore rules.
 * 4. Sign-out clears the session and transitions to UNAUTHENTICATED.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StagingIdentitySdkEmulatorTest {

    private lateinit var context: Context
    private lateinit var app1: FirebaseApp
    private lateinit var app2: FirebaseApp
    private lateinit var app3: FirebaseApp

    private lateinit var auth1: FirebaseAuth
    private lateinit var auth2: FirebaseAuth
    private lateinit var auth3: FirebaseAuth

    private lateinit var firestore1: FirebaseFirestore
    private lateinit var firestore2: FirebaseFirestore
    private lateinit var firestore3: FirebaseFirestore

    private lateinit var authRepo1: AuthRepositoryImpl
    private lateinit var authRepo2: AuthRepositoryImpl
    private lateinit var authRepo3: AuthRepositoryImpl

    private val sharedEmail = "staging-device-${UUID.randomUUID()}@ironmind.local"
    private val stagingPassword = "StagingTestPassword123!"

    private val isolatedEmail = "isolated-user-${UUID.randomUUID()}@ironmind.local"
    private val isolatedPassword = "IsolatedPassword123!"

    @Before
    fun setUp() {
        // Direct Architecture Components to execute synchronously
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        // Unconfined main dispatcher prevents deadlocks under runBlocking
        Dispatchers.setMain(Dispatchers.Unconfined)

        context = ApplicationProvider.getApplicationContext()

        // Strict Pre-Auth Emulator Assertions
        assertTrue("Must use Firebase emulators", BuildConfig.USE_FIREBASE_EMULATORS)

        val options = FirebaseOptions.Builder()
            .setApplicationId("com.sanket_satpute_20.ironmind")
            .setProjectId("demo-ironmind")
            .setApiKey("fake-api-key")
            .build()

        app1 = if (FirebaseApp.getApps(context).none { it.name == "staging-app-1" }) {
            FirebaseApp.initializeApp(context, options, "staging-app-1")
        } else {
            FirebaseApp.getInstance("staging-app-1")
        }

        app2 = if (FirebaseApp.getApps(context).none { it.name == "staging-app-2" }) {
            FirebaseApp.initializeApp(context, options, "staging-app-2")
        } else {
            FirebaseApp.getInstance("staging-app-2")
        }

        app3 = if (FirebaseApp.getApps(context).none { it.name == "staging-app-3" }) {
            FirebaseApp.initializeApp(context, options, "staging-app-3")
        } else {
            FirebaseApp.getInstance("staging-app-3")
        }

        auth1 = FirebaseAuth.getInstance(app1)
        auth2 = FirebaseAuth.getInstance(app2)
        auth3 = FirebaseAuth.getInstance(app3)

        firestore1 = FirebaseFirestore.getInstance(app1)
        firestore2 = FirebaseFirestore.getInstance(app2)
        firestore3 = FirebaseFirestore.getInstance(app3)

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()

        listOf(auth1, auth2, auth3).forEach {
            try {
                it.useEmulator("127.0.0.1", 9199)
            } catch (_: IllegalStateException) {}
        }

        listOf(firestore1, firestore2, firestore3).forEach {
            try {
                it.useEmulator("127.0.0.1", 8180)
                it.firestoreSettings = settings
            } catch (_: IllegalStateException) {}
        }

        authRepo1 = AuthRepositoryImpl(auth1)
        authRepo2 = AuthRepositoryImpl(auth2)
        authRepo3 = AuthRepositoryImpl(auth3)
    }

    @After
    fun tearDown() {
        runBlocking {
            try {
                runWithMainLooperPumping {
                    try { auth1.signOut() } catch (_: Exception) {}
                    try { auth2.signOut() } catch (_: Exception) {}
                    try { auth3.signOut() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            try { ArchTaskExecutor.getInstance().setDelegate(null) } catch (_: Exception) {}
            try { Dispatchers.resetMain() } catch (_: Exception) {}
        }
    }

    private suspend fun <T> runWithMainLooperPumping(block: suspend () -> T): T {
        val deferred = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
            block()
        }
        val tStart = System.currentTimeMillis()
        while (!deferred.isCompleted) {
            if (System.currentTimeMillis() - tStart > 20_000L) {
                throw java.util.concurrent.TimeoutException("runWithMainLooperPumping timed out after 20s")
            }
            try {
                shadowOf(Looper.getMainLooper()).idle()
            } catch (e: Exception) {
                println("DIAG idle err: $e")
            }
            kotlinx.coroutines.delay(50)
        }
        return deferred.await()
    }

    @Test
    fun test01_sharedStagingIdentity_authenticatesIdenticalUidAcrossTwoClients() = runBlocking {
        println("DIAG-AUTH [01] Authenticating Client 1...")
        val t0 = System.currentTimeMillis()
        val result1 = runWithMainLooperPumping {
            authRepo1.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword)
        }
        println("DIAG-AUTH [02] Client 1 completed in ${System.currentTimeMillis() - t0}ms: $result1")
        assertTrue("Client 1 sign in must succeed: ${(result1 as? Result.Failure)?.error?.message}", result1 is Result.Success)
        val uid1 = (result1 as Result.Success).data.id

        println("DIAG-AUTH [03] Authenticating Client 2...")
        val t1 = System.currentTimeMillis()
        val result2 = runWithMainLooperPumping {
            authRepo2.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword)
        }
        println("DIAG-AUTH [04] Client 2 completed in ${System.currentTimeMillis() - t1}ms: $result2")
        assertTrue("Client 2 sign in must succeed: ${(result2 as? Result.Failure)?.error?.message}", result2 is Result.Success)
        val uid2 = (result2 as Result.Success).data.id

        assertEquals("Both clients must share the exact same UID", uid1, uid2)
        assertTrue("UID must not be blank", uid1.isNotBlank())
        assertEquals(sharedEmail, (result1 as Result.Success).data.email)
        assertEquals(sharedEmail, (result2 as Result.Success).data.email)
    }

    @Test
    fun test02_sharedUid_allowsCrossClientReadAndWriteUnderFirestoreRules() = runBlocking {
        val res1 = runWithMainLooperPumping { authRepo1.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword) }
        val res2 = runWithMainLooperPumping { authRepo2.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword) }
        val sharedUid = (res1 as Result.Success).data.id

        val goalId = "shared-goal-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val goalData = mapOf(
            "id" to goalId,
            "userId" to sharedUid,
            "title" to "Goal Created by Client A",
            "importance" to 3,
            "status" to "ACTIVE",
            "version" to 1L,
            "createdAt" to now,
            "updatedAt" to now,
            "isDeleted" to false
        )

        runWithMainLooperPumping {
            firestore1.collection("users").document(sharedUid).collection("goals").document(goalId).set(goalData).await()
        }
        val readSnap = runWithMainLooperPumping {
            firestore2.collection("users").document(sharedUid).collection("goals").document(goalId).get().await()
        }

        assertTrue("Client B must be able to read Client A's document under the same shared UID", readSnap.exists())
        assertEquals("Goal Created by Client A", readSnap.getString("title"))
        assertEquals(sharedUid, readSnap.getString("userId"))
    }

    @Test
    fun test03_differentUid_isStrictlyBlockedByFirestoreSecurityRules() = runBlocking {
        val res1 = runWithMainLooperPumping { authRepo1.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword) }
        val sharedUid = (res1 as Result.Success).data.id

        val res3 = runWithMainLooperPumping { authRepo3.signInOrCreateWithEmailAndPassword(isolatedEmail, isolatedPassword) }
        val isolatedUid = (res3 as Result.Success).data.id

        assertTrue("Isolated UID must differ from shared UID", sharedUid != isolatedUid)

        val goalId = "protected-goal-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val sharedGoal = mapOf(
            "id" to goalId,
            "userId" to sharedUid,
            "title" to "Protected Goal",
            "status" to "ACTIVE",
            "version" to 1L,
            "createdAt" to now,
            "updatedAt" to now,
            "isDeleted" to false
        )

        runWithMainLooperPumping {
            firestore1.collection("users").document(sharedUid).collection("goals").document(goalId).set(sharedGoal).await()
        }

        try {
            runWithMainLooperPumping {
                firestore3.collection("users").document(sharedUid).collection("goals").document(goalId).get().await()
            }
            fail("Client 3 must be blocked from reading /users/{sharedUid} by Firestore rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals("Firestore must return PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        try {
            val maliciousData = mapOf("title" to "Hacked Title", "userId" to sharedUid)
            runWithMainLooperPumping {
                firestore3.collection("users").document(sharedUid).collection("goals").document(goalId).set(maliciousData).await()
            }
            fail("Client 3 must be blocked from writing to /users/{sharedUid} by Firestore rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals("Firestore must return PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun test04_signOut_clearsSessionAndTransitionsToUnauthenticated() = runBlocking {
        val res = runWithMainLooperPumping { authRepo1.signInOrCreateWithEmailAndPassword(sharedEmail, stagingPassword) }
        assertTrue(res is Result.Success)
        assertNotNull(authRepo1.getCurrentUser())

        val signOutRes = runWithMainLooperPumping { authRepo1.signOut() }
        assertTrue(signOutRes is Result.Success)

        assertNull("Current user must be null after signOut", authRepo1.getCurrentUser())
        val status = runWithMainLooperPumping {
            withTimeout(15_000L) {
                authRepo1.authStatus.first()
            }
        }
        assertEquals("AuthStatus must be UNAUTHENTICATED after signOut", AuthStatus.UNAUTHENTICATED, status)
    }
}
