package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationRecord
import com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository

class FakeNotificationRecordRepository : NotificationRecordRepository {
    val savedRecords = mutableListOf<NotificationRecord>()

    override suspend fun saveRecord(record: NotificationRecord): Result<Unit, Exception> {
        savedRecords.add(record)
        return Result.Success(Unit)
    }

    override suspend fun getMostRecentByKey(key: String): Result<NotificationRecord?, Exception> {
        val record = savedRecords.filter { it.deduplicationKey == key }
            .maxByOrNull { it.timestamp }
        return Result.Success(record)
    }

    override suspend fun getRecent(limit: Int): Result<List<NotificationRecord>, Exception> {
        val recent = savedRecords.sortedByDescending { it.timestamp }.take(limit)
        return Result.Success(recent)
    }
}
