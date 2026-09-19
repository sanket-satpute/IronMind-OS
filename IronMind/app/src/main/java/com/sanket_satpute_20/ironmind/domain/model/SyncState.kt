package com.sanket_satpute_20.ironmind.domain.model

data class SyncState(
    val isIdle: Boolean,
    val isSyncing: Boolean,
    val lastSyncedAt: Long?,
    val lastError: String?,
    val pendingCount: Int
)
