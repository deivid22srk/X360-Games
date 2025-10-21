package com.x360games.archivedownloader.data

data class PartProgress(
    val partIndex: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
    
    val isComplete: Boolean
        get() = downloadedBytes >= totalBytes && totalBytes > 0
}

data class MultiPartDownloadProgress(
    val downloadId: Long,
    val parts: List<PartProgress>,
    val totalDownloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long
) {
    val overallProgress: Float
        get() = if (totalBytes > 0) totalDownloadedBytes.toFloat() / totalBytes.toFloat() else 0f
    
    val completedParts: Int
        get() = parts.count { it.isComplete }
    
    val totalParts: Int
        get() = parts.size
}
