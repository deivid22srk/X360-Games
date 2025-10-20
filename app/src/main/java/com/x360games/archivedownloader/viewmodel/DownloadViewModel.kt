package com.x360games.archivedownloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.database.AppDatabase
import com.x360games.archivedownloader.database.DownloadEntity
import com.x360games.archivedownloader.database.SpeedHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val downloadDao = database.downloadDao()
    private val speedHistoryDao = database.speedHistoryDao()
    
    val allDownloads: StateFlow<List<DownloadEntity>> = downloadDao.getAllDownloads()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )
    
    fun getDownloadById(downloadId: Long): StateFlow<DownloadEntity?> {
        return downloadDao.getDownloadById(downloadId)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )
    }
    
    fun getSpeedHistoryForDownload(downloadId: Long): StateFlow<List<SpeedHistoryEntity>> {
        return speedHistoryDao.getSpeedHistoryForDownload(downloadId)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )
    }
    
    suspend fun getDownloadByIdDirect(downloadId: Long): DownloadEntity? {
        return downloadDao.getDownloadByIdSync(downloadId)
    }
    
    fun clearFinishedDownloads() {
        viewModelScope.launch {
            downloadDao.clearFinishedDownloads()
        }
    }
    
    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            downloadDao.deleteDownloadById(downloadId)
        }
    }
}
