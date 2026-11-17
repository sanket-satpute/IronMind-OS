package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted authoritative session state table.
 *
 * Singleton row (id = 1) that stores the authoritative active userId and
 * monotonically increasing session generation. All distributed sync worker leases
 * validate caller generations against this table to prevent stale or fabricated
 * sessions from acquiring or mutating locks.
 */
@Entity(tableName = "app_session_state")
data class AppSessionStateEntity(
    @PrimaryKey val id: Int = 1,
    val activeUserId: String,
    val currentGeneration: Long,
    val updatedAt: Long
)
