import java.net.Socket
import java.net.InetSocketAddress

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sanket_satpute_20.ironmind"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sanket_satpute_20.ironmind"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "true")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"127.0.0.1\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "9199")
            buildConfigField("int", "FIREBASE_FIRESTORE_EMULATOR_PORT", "8180")
            buildConfigField("boolean", "ALLOW_REMOTE_INFERENCE", "false")
            buildConfigField("String", "CLOUDFLARE_DEV_ENDPOINT", "\"http://127.0.0.1:8787/v1/process\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "0")
            buildConfigField("int", "FIREBASE_FIRESTORE_EMULATOR_PORT", "0")
            buildConfigField("boolean", "ALLOW_REMOTE_INFERENCE", "true")
            buildConfigField("String", "CLOUDFLARE_DEV_ENDPOINT", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { testTask ->
                testTask.maxHeapSize = "2048m"
                testTask.forkEvery = 15
                val emulatorTestPatterns = listOf(
                    "**/FirestoreEmulatorSdkTest*",
                    "**/FirestoreDownstreamSyncSdkTest*",
                    "**/Tier1DownstreamAtomicApplyAndTombstoneTest*",
                    "**/Tier1IncrementalPullAndPaginationTest*",
                    "**/StagingIdentitySdkEmulatorTest*"
                )
                if (testTask.name.endsWith("UnitTest") && testTask.name != "testFirebaseEmulatorIntegration") {
                    emulatorTestPatterns.forEach { pattern ->
                        testTask.exclude(pattern)
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20231013")
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val emulatorTestPatterns = listOf(
    "**/FirestoreEmulatorSdkTest*",
    "**/FirestoreDownstreamSyncSdkTest*",
    "**/Tier1DownstreamAtomicApplyAndTombstoneTest*",
    "**/Tier1IncrementalPullAndPaginationTest*",
    "**/StagingIdentitySdkEmulatorTest*"
)

tasks.register<Test>("testFirebaseEmulatorIntegration") {
    group = "verification"
    description = "Runs Firebase Emulator integration tests against local emulator endpoints (demo-ironmind)."

    failOnNoDiscoveredTests.set(false)

    val testDebugTask = tasks.named<Test>("testDebugUnitTest")

    testClassesDirs = testDebugTask.get().testClassesDirs
    classpath = testDebugTask.get().classpath

    emulatorTestPatterns.forEach { include(it) }

    doFirst {
        val host = "127.0.0.1"
        val authPort = 9199
        val firestorePort = 8180

        val verifyDiscoveryOnly = project.hasProperty("verifyEmulatorDiscovery")
        val discoveredClasses = testClassesDirs.asFileTree.matching {
            emulatorTestPatterns.forEach { include(it) }
        }.files.filter { it.name.endsWith(".class") && !it.name.contains("$") }

        println("========================================================================================")
        println("DISCOVERED FIREBASE EMULATOR INTEGRATION TEST SUITES (${discoveredClasses.size} suites):")
        discoveredClasses.sortedBy { it.name }.forEach { println("  - ${it.nameWithoutExtension}") }
        println("========================================================================================")

        if (verifyDiscoveryOnly) {
            println("Verification-only discovery mode active (-PverifyEmulatorDiscovery). Skipping socket execution.")
            filter.isFailOnNoMatchingTests = false
            setIncludes(emptyList<String>())
            setExcludes(listOf("**/*"))
            return@doFirst
        }

        fun isPortOpen(port: Int): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 500)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

        val authOpen = isPortOpen(authPort)
        val firestoreOpen = isPortOpen(firestorePort)

        if (!authOpen || !firestoreOpen) {
            val unavailable = mutableListOf<String>()
            if (!authOpen) unavailable.add("Firebase Auth Emulator ($host:$authPort)")
            if (!firestoreOpen) unavailable.add("Firestore Emulator ($host:$firestorePort)")
            val unavailableStr = unavailable.joinToString("\n|   - ")

            throw org.gradle.api.GradleException(
                """
                |========================================================================================
                | FIREBASE EMULATOR INTEGRATION TEST PRE-FLIGHT FAILURE
                |========================================================================================
                | Required local Firebase emulator endpoint(s) are NOT reachable:
                |   - $unavailableStr
                |
                | These integration tests communicate over live gRPC/HTTP with the local emulator suite.
                | Security & Safety Invariants:
                |   - Isolated Project ID: demo-ironmind (NEVER production ironmind-38968)
                |   - Auth Endpoint: $host:$authPort
                |   - Firestore Endpoint: $host:$firestorePort
                |
                | To launch the emulator suite locally, run:
                |   firebase emulators:start --only auth,firestore --project demo-ironmind
                |========================================================================================
                """.trimMargin()
            )
        }
    }
}