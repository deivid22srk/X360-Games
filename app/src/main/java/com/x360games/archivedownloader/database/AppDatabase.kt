package com.x360games.archivedownloader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class, SpeedHistoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun downloadDao(): DownloadDao
    abstract fun speedHistoryDao(): SpeedHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "x360_games_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toDownloadStatus(status: String): DownloadStatus {
        return DownloadStatus.valueOf(status)
    }
}
