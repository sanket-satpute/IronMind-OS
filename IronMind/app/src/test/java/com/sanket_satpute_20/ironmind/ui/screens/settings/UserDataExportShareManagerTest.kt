package com.sanket_satpute_20.ironmind.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.R
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDataExportShareManagerTest {

    private lateinit var context: Context
    private lateinit var shareManager: UserDataExportShareManager
    private lateinit var exportDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shareManager = AndroidUserDataExportShareManager()
        exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
    }

    @Test
    fun getExportContentUri_resolvesSecureContentUriViaFileProvider() {
        val testFile = File(exportDir, "ironmind_export_user_123_1000.json")
        testFile.writeText("{\"status\":\"ok\"}")

        val contentUri = shareManager.getExportContentUri(context, testFile)

        assertNotNull(contentUri)
        assertEquals("content", contentUri.scheme)
        assertTrue(
            "Authority should end with .fileprovider: ${contentUri.authority}",
            contentUri.authority?.endsWith(".fileprovider") == true
        )
        assertTrue(
            "URI path should reflect user_exports path mapping: ${contentUri.path}",
            contentUri.path?.contains("user_exports") == true || contentUri.path?.contains(testFile.name) == true
        )
        // Ensure raw file scheme is never exposed
        assertTrue(contentUri.scheme != "file")
    }

    @Test
    fun createShareIntent_constructsValidSendIntentWithJsonMimeTypeAndReadPermission() {
        val dummyUri = Uri.parse("content://${context.packageName}.fileprovider/user_exports/test.json")

        val intent = shareManager.createShareIntent(dummyUri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals(dummyUri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertNotNull(intent.clipData)
        assertEquals(dummyUri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(
            "FLAG_GRANT_READ_URI_PERMISSION must be present",
            (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )
        assertTrue(
            "FLAG_GRANT_WRITE_URI_PERMISSION must NOT be granted",
            (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0
        )
    }

    @Test
    fun createShareChooserIntent_withValidExportFile_returnsChooserIntent() {
        val testFile = File(exportDir, "ironmind_export_user_456_2000.json")
        testFile.writeText("{\"records\":[1,2,3]}")

        val metadata = ExportFileMetadata(
            file = testFile,
            fileName = testFile.name,
            sizeBytes = testFile.length(),
            recordCount = 3,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 2000L,
            userId = "user_456"
        )

        val chooserIntent = shareManager.createShareChooserIntent(context, metadata)

        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent?.action)
        assertTrue(
            "Chooser flags must include FLAG_GRANT_READ_URI_PERMISSION",
            (chooserIntent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )

        val targetSendIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetSendIntent)
        assertEquals(Intent.ACTION_SEND, targetSendIntent?.action)
        assertEquals("application/json", targetSendIntent?.type)
        assertTrue((targetSendIntent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun createShareChooserIntent_withMissingFile_returnsNull() {
        val missingFile = File(exportDir, "non_existent_file.json")
        if (missingFile.exists()) missingFile.delete()

        val metadata = ExportFileMetadata(
            file = missingFile,
            fileName = missingFile.name,
            sizeBytes = 0L,
            recordCount = 0,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 3000L,
            userId = "user_789"
        )

        val chooserIntent = shareManager.createShareChooserIntent(context, metadata)
        assertNull("Missing file must be rejected", chooserIntent)
    }

    @Test
    fun createShareChooserIntent_withZeroByteFile_returnsNull() {
        val emptyFile = File(exportDir, "empty_file.json")
        emptyFile.writeText("")

        val metadata = ExportFileMetadata(
            file = emptyFile,
            fileName = emptyFile.name,
            sizeBytes = 0L,
            recordCount = 0,
            format = "JSON",
            exportVersion = 1,
            exportedAt = 4000L,
            userId = "user_789"
        )

        val chooserIntent = shareManager.createShareChooserIntent(context, metadata)
        assertNull("Zero-byte file must be rejected", chooserIntent)
    }
}

