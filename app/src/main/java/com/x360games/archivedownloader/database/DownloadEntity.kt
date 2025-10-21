package com.x360games.archivedownloader.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileUrl: String,
    val identifier: String,
    val destinationPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val speed: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val cookie: String? = null,
    val notificationId: Int = 0,
    val downloadParts: Int = 1,
    val partsProgress: String = "{}"
) {
    fun getPartsProgressMap(): Map<Int, Long> {
        return try {
            val parts = mutableMapOf<Int, Long>()
            val json = partsProgress.trim().removeSurrounding("{", "}")
            if (json.isEmpty()) return emptyMap()
            
            json.split(",").forEach { pair ->
                val (key, value) = pair.split(":").map { it.trim().trim('"') }
                parts[key.toInt()] = value.toLong()
            }
            parts
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    fun getPartProgress(partIndex: Int): Float {
        if (downloadParts <= 1 || totalBytes == 0L) return 0f
        val partSize = totalBytes / downloadParts
        val partBytes = getPartsProgressMap()[partIndex] ?: 0L
        return partBytes.toFloat() / partSize.toFloat()
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
