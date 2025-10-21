package com.x360games.archivedownloader.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "download_parts",
    primaryKeys = ["downloadId", "partIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("downloadId")]
)
data class DownloadPartEntity(
    val downloadId: Long,
    val partIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0,
    val isCompleted: Boolean = false
)
