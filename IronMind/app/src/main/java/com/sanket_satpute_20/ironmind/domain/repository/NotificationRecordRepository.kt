package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationRecord
import com.sanket_satpute_20.ironmind.domain.common.Result

interface NotificationRecordRepository {
    suspend fun saveRecord(record: NotificationRecord): Result<Unit, Exception>
    suspend fun getMostRecentByKey(key: String): Result<NotificationRecord?, Exception>
    suspend fun getRecent(limit: Int): Result<List<NotificationRecord>, Exception>
}
