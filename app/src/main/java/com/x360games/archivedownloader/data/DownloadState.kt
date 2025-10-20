package com.x360games.archivedownloader.data

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val fileName: String) : DownloadState()
    data class Success(val fileName: String, val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class DownloadItem(
    val id: String,
    val fileName: String,
    val url: String,
    val state: DownloadState
)
