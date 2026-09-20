package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.NotificationRecordDao
import com.sanket_satpute_20.ironmind.data.local.entity.NotificationRecordEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationDeliveryStatus
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationRecord
import com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRecordRepositoryImpl(
    private val dao: NotificationRecordDao
) : NotificationRecordRepository {

    override suspend fun saveRecord(record: NotificationRecord): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insert(record.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMostRecentByKey(key: String): Result<NotificationRecord?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getMostRecentByKey(key)
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getRecent(limit: Int): Result<List<NotificationRecord>, Exception> = withContext(Dispatchers.IO) {
        try {
            val list = dao.getRecent(limit).map { it.toDomain() }
            Result.Success(list)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

fun NotificationRecord.toEntity(): NotificationRecordEntity = NotificationRecordEntity(
    id = id,
    timestamp = timestamp,
    deduplicationKey = deduplicationKey,
    deliveryStatus = deliveryStatus.name,
    suppressionReason = suppressionReason
)

fun NotificationRecordEntity.toDomain(): NotificationRecord = NotificationRecord(
    id = id,
    timestamp = timestamp,
    deduplicationKey = deduplicationKey,
    deliveryStatus = try { NotificationDeliveryStatus.valueOf(deliveryStatus) } catch (e: Exception) { NotificationDeliveryStatus.SUPPRESSED },
    suppressionReason = suppressionReason
)
