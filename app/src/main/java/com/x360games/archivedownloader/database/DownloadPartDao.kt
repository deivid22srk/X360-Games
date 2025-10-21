package com.x360games.archivedownloader.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadPartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: DownloadPartEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<DownloadPartEntity>)
    
    @Query("SELECT * FROM download_parts WHERE downloadId = :downloadId ORDER BY partIndex")
    suspend fun getPartsForDownload(downloadId: Long): List<DownloadPartEntity>
    
    @Query("SELECT * FROM download_parts WHERE downloadId = :downloadId ORDER BY partIndex")
    fun getPartsForDownloadFlow(downloadId: Long): Flow<List<DownloadPartEntity>>
    
    @Query("UPDATE download_parts SET downloadedBytes = :downloadedBytes, isCompleted = :isCompleted WHERE downloadId = :downloadId AND partIndex = :partIndex")
    suspend fun updatePartProgress(downloadId: Long, partIndex: Int, downloadedBytes: Long, isCompleted: Boolean)
    
    @Query("DELETE FROM download_parts WHERE downloadId = :downloadId")
    suspend fun deletePartsForDownload(downloadId: Long)
    
    @Query("SELECT COUNT(*) FROM download_parts WHERE downloadId = :downloadId AND isCompleted = 0")
    suspend fun getIncompletePartsCount(downloadId: Long): Int
}
