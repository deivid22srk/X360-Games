package com.x360games.archivedownloader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DownloadProgressTracker {
    private val _partsProgress = MutableStateFlow<Map<Long, Map<Int, Long>>>(emptyMap())
    val partsProgress: StateFlow<Map<Long, Map<Int, Long>>> = _partsProgress
    
    fun updatePartProgress(downloadId: Long, partIndex: Int, partBytes: Long) {
        val currentMap = _partsProgress.value.toMutableMap()
        val partsMap = currentMap[downloadId]?.toMutableMap() ?: mutableMapOf()
        partsMap[partIndex] = partBytes
        currentMap[downloadId] = partsMap
        _partsProgress.value = currentMap
    }
    
    fun clearPartProgress(downloadId: Long) {
        val currentMap = _partsProgress.value.toMutableMap()
        currentMap.remove(downloadId)
        _partsProgress.value = currentMap
    }
    
    fun getPartProgress(downloadId: Long, partIndex: Int): Long {
        return _partsProgress.value[downloadId]?.get(partIndex) ?: 0L
    }
    
    fun getPartsProgressForDownload(downloadId: Long): Map<Int, Long> {
        return _partsProgress.value[downloadId] ?: emptyMap()
    }
}
