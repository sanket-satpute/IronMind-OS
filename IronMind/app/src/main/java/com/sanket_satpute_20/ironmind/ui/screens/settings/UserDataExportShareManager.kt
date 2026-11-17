package com.sanket_satpute_20.ironmind.ui.screens.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import java.io.File

/**
 * Contract for managing FileProvider content URI resolution and
 * system Share Sheet intent construction for exported user data.
 */
interface UserDataExportShareManager {
    /**
     * Resolves the secure content:// URI for the exported file using AndroidX FileProvider.
     * Never exposes raw file:// URIs.
     */
    fun getExportContentUri(context: Context, file: File): Uri

    /**
     * Constructs an ACTION_SEND Intent with MIME type application/json, EXTRA_STREAM,
     * ClipData, and FLAG_GRANT_READ_URI_PERMISSION.
     */
    fun createShareIntent(contentUri: Uri): Intent

    /**
     * Validates the exported file artifact and builds an Intent.createChooser wrapper
     * ready to be launched by the UI.
     * Returns null if the file does not exist, is empty, or URI resolution fails.
     */
    fun createShareChooserIntent(context: Context, metadata: ExportFileMetadata): Intent?
}

/**
 * Production implementation of [UserDataExportShareManager] backed by AndroidX FileProvider.
 */
class AndroidUserDataExportShareManager(
    private val authorityOverride: String? = null
) : UserDataExportShareManager {

    override fun getExportContentUri(context: Context, file: File): Uri {
        val authority = authorityOverride ?: "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            // AndroidX Core 1.15+ hardcodes forward-slash '/' in FileProvider.belongsToRoot,
            // which fails on Windows host JVM during Robolectric unit testing where File.separator is '\'.
            if (File.separatorChar == '\\' && e.message?.contains("Failed to find configured root") == true) {
                Uri.parse("content://$authority/user_exports/${file.name}")
            } else {
                throw e
            }
        }
    }

    override fun createShareIntent(contentUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri("IronMind User Data Export", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun createShareChooserIntent(context: Context, metadata: ExportFileMetadata): Intent? {
        val file = metadata.file
        if (!file.exists() || file.length() <= 0L) {
            return null
        }

        val contentUri = try {
            getExportContentUri(context, file)
        } catch (e: Exception) {
            return null
        }

        val sendIntent = createShareIntent(contentUri)
        return Intent.createChooser(sendIntent, "Share IronMind Data Export").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
