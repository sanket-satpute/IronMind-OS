package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.NotificationRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: NotificationRecordEntity)

    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): List<NotificationRecordEntity>
    
    @Query("SELECT * FROM notification_records WHERE deduplicationKey = :key ORDER BY timestamp DESC LIMIT 1")
    fun getMostRecentByKey(key: String): NotificationRecordEntity?
}
