package com.x360games.archivedownloader.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "speed_history",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["downloadId"])]
)
data class SpeedHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val downloadId: Long,
    val timestamp: Long,
    val speed: Long
)
