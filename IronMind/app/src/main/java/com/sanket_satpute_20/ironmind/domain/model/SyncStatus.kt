package com.sanket_satpute_20.ironmind.domain.model

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT
}
