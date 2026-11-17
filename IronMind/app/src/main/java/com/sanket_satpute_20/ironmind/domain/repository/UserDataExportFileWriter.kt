package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import com.sanket_satpute_20.ironmind.domain.usecase.data.UserDataExport

/**
 * Port interface for serializing and persisting a UserDataExport snapshot to disk.
 */
interface UserDataExportFileWriter {
    /**
     * Serializes UserDataExport directly to disk in the dedicated exports directory.
     *
     * @param export The materialized snapshot of user data.
     * @param appVersion Optional app version string for root metadata.
     * @return Result containing ExportFileMetadata on success, or Exception on failure.
     */
    fun writeToFile(
        export: UserDataExport,
        appVersion: String? = null
    ): Result<ExportFileMetadata, Exception>
}
