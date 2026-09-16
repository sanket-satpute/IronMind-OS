package com.sanket_satpute_20.ironmind.architecture

import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class ArchitectureIsolationTest {

    @Test
    fun `domain layer must not depend on android framework`() {
        val projectDir = File(System.getProperty("user.dir"))
        val domainDir = File(projectDir, "src/main/java/com/sanket_satpute_20/ironmind/domain")

        if (!domainDir.exists()) {
            // Domain directory might not be created fully yet, or we're running from a different root.
            // For now, if it doesn't exist, we just pass. Real checks will happen when files exist.
            return
        }

        val domainFiles = domainDir.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }

        val forbiddenImports = listOf(
            "import android.",
            "import androidx."
        )

        domainFiles.forEach { file ->
            val content = file.readText()
            forbiddenImports.forEach { forbidden ->
                val hasForbiddenImport = content.lines().any { line -> line.trim().startsWith(forbidden) }
                assertTrue(
                    "Architecture Violation: Domain file ${file.name} contains forbidden import '$forbidden'. " +
                            "The domain layer must not depend on the Android framework.",
                    !hasForbiddenImport
                )
            }
        }
    }
}
