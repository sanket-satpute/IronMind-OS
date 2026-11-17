package com.sanket_satpute_20.ironmind.domain.usecase.data

import java.io.File

/**
 * Strongly typed file metadata describing a completed user data export artifact.
 *
 * Epistemic & Privacy Guarantee:
 * Returns only safe file descriptor metadata (path, size, record count, timestamps, identifiers)
 * rather than holding the entire materialized JSON payload in heap.
 */
data class ExportFileMetadata(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
    val recordCount: Int,
    val format: String,
    val exportVersion: Int,
    val exportedAt: Long,
    val userId: String
)
