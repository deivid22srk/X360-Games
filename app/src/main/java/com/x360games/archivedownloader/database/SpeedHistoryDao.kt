package com.x360games.archivedownloader.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedHistoryDao {
    
    @Query("SELECT * FROM speed_history WHERE downloadId = :downloadId ORDER BY timestamp DESC LIMIT 100")
    fun getSpeedHistoryForDownload(downloadId: Long): Flow<List<SpeedHistoryEntity>>
    
    @Query("SELECT * FROM speed_history WHERE downloadId = :downloadId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSpeedHistory(downloadId: Long, limit: Int): List<SpeedHistoryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeedEntry(speedHistory: SpeedHistoryEntity)
    
    @Query("DELETE FROM speed_history WHERE downloadId = :downloadId")
    suspend fun deleteSpeedHistoryForDownload(downloadId: Long)
    
    @Query("DELETE FROM speed_history WHERE timestamp < :timestamp")
    suspend fun deleteOldSpeedHistory(timestamp: Long)
}
