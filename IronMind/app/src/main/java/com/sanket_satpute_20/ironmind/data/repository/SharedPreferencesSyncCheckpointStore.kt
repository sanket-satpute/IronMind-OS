package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sanket_satpute_20.ironmind.domain.repository.SyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
import java.util.concurrent.ConcurrentHashMap

class SharedPreferencesSyncCheckpointStore(
    context: Context
) : SyncCheckpointStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ironmind_sync_checkpoints", Context.MODE_PRIVATE)

    override fun getLastCursor(userId: String, entityType: String): SyncCursor {
        val legacy = prefs.getLong(keyFor(userId, entityType), 0L)
        val ts = prefs.getLong(keyForTs(userId, entityType), 0L)
        val docId = prefs.getString(keyForDoc(userId, entityType), "") ?: ""
        val effectiveTs = maxOf(legacy, ts)
        return SyncCursor(timestamp = effectiveTs, documentId = docId)
    }

    override fun setLastCursor(userId: String, entityType: String, cursor: SyncCursor) {
        val current = getLastCursor(userId, entityType)
        val shouldUpdate = cursor.timestamp > current.timestamp ||
            (cursor.timestamp == current.timestamp && cursor.documentId > current.documentId)
        if (shouldUpdate) {
            prefs.edit()
                .putLong(keyForTs(userId, entityType), cursor.timestamp)
                .putString(keyForDoc(userId, entityType), cursor.documentId)
                .putLong(keyFor(userId, entityType), cursor.timestamp)
                .apply()
        }
    }

    override fun getLastCheckpoint(userId: String, entityType: String): Long {
        return getLastCursor(userId, entityType).timestamp
    }

    override fun setLastCheckpoint(userId: String, entityType: String, checkpoint: Long) {
        setLastCursor(userId, entityType, SyncCursor(checkpoint, ""))
    }

    override fun clearCheckpointsForUser(userId: String) {
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith("sync_cp_${userId}_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun keyFor(userId: String, entityType: String): String =
        "sync_cp_${userId}_${entityType}"

    private fun keyForTs(userId: String, entityType: String): String =
        "sync_cp_${userId}_${entityType}_ts"

    private fun keyForDoc(userId: String, entityType: String): String =
        "sync_cp_${userId}_${entityType}_doc"
}

class InMemorySyncCheckpointStore : SyncCheckpointStore {
    private val cursors = ConcurrentHashMap<String, SyncCursor>()

    override fun getLastCursor(userId: String, entityType: String): SyncCursor {
        return cursors["${userId}_${entityType}"] ?: SyncCursor(0L, "")
    }

    override fun setLastCursor(userId: String, entityType: String, cursor: SyncCursor) {
        cursors.compute("${userId}_${entityType}") { _, current ->
            if (current == null) {
                cursor
            } else if (cursor.timestamp > current.timestamp ||
                (cursor.timestamp == current.timestamp && cursor.documentId > current.documentId)) {
                cursor
            } else {
                current
            }
        }
    }

    override fun getLastCheckpoint(userId: String, entityType: String): Long =
        getLastCursor(userId, entityType).timestamp

    override fun setLastCheckpoint(userId: String, entityType: String, checkpoint: Long) {
        setLastCursor(userId, entityType, SyncCursor(checkpoint, ""))
    }

    override fun clearCheckpointsForUser(userId: String) {
        val keysToRemove = cursors.keys.filter { it.startsWith("${userId}_") }
        keysToRemove.forEach { cursors.remove(it) }
    }
}
