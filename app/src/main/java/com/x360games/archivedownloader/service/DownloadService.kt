package com.x360games.archivedownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.x360games.archivedownloader.MainActivity
import com.x360games.archivedownloader.R
import com.x360games.archivedownloader.database.AppDatabase
import com.x360games.archivedownloader.database.DownloadEntity
import com.x360games.archivedownloader.database.DownloadStatus
import com.x360games.archivedownloader.database.SpeedHistoryEntity
import com.x360games.archivedownloader.network.ArchiveRepository
import com.x360games.archivedownloader.utils.FileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadService : Service() {
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private lateinit var database: AppDatabase
    private lateinit var repository: ArchiveRepository
    private lateinit var notificationManager: NotificationManager
    
    private val activeDownloads = mutableMapOf<Long, Job>()
    private val downloadStates = MutableStateFlow<Map<Long, DownloadProgressState>>(emptyMap())
    
    private var isForeground = false
    
    companion object {
        const val CHANNEL_ID = "download_service_channel"
        const val NOTIFICATION_ID = 1000
        
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_PAUSE_DOWNLOAD = "action_pause_download"
        const val ACTION_RESUME_DOWNLOAD = "action_resume_download"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val ACTION_RESUME_ALL = "action_resume_all"
        
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_URL = "file_url"
        const val EXTRA_IDENTIFIER = "identifier"
        const val EXTRA_DESTINATION_PATH = "destination_path"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
    
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        repository = ArchiveRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        resumeIncompleteDownloads()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0)
                if (downloadId > 0) {
                    startDownload(downloadId)
                } else {
                    val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return
                    val fileUrl = intent.getStringExtra(EXTRA_FILE_URL) ?: return
                    val identifier = intent.getStringExtra(EXTRA_IDENTIFIER) ?: return
                    val destinationPath = intent.getStringExtra(EXTRA_DESTINATION_PATH) ?: return
                    val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0)
                    val cookie = intent.getStringExtra(EXTRA_COOKIE)
                    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                    
                    createAndStartDownload(
                        fileName, fileUrl, identifier, destinationPath,
                        totalBytes, cookie, notificationId
                    )
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0)
                pauseDownload(downloadId)
            }
            ACTION_RESUME_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0)
                resumeDownload(downloadId)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0)
                cancelDownload(downloadId)
            }
            ACTION_RESUME_ALL -> {
                resumeIncompleteDownloads()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        if (!isForeground) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Download Service")
                .setContentText("Managing downloads...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        }
    }
    
    private fun createAndStartDownload(
        fileName: String,
        fileUrl: String,
        identifier: String,
        destinationPath: String,
        totalBytes: Long,
        cookie: String?,
        notificationId: Int
    ) {
        serviceScope.launch {
            val download = DownloadEntity(
                fileName = fileName,
                fileUrl = fileUrl,
                identifier = identifier,
                destinationPath = destinationPath,
                totalBytes = totalBytes,
                downloadedBytes = 0,
                status = DownloadStatus.QUEUED,
                cookie = cookie,
                notificationId = notificationId
            )
            
            val downloadId = database.downloadDao().insertDownload(download)
            startDownload(downloadId)
        }
    }
    
    private fun startDownload(downloadId: Long) {
        if (activeDownloads.containsKey(downloadId)) return
        
        startForeground()
        
        val job = serviceScope.launch {
            try {
                val download = database.downloadDao().getDownloadByIdSync(downloadId) ?: return@launch
                
                database.downloadDao().updateStatus(downloadId, DownloadStatus.DOWNLOADING)
                
                var lastHistoryUpdate = 0L
                
                val result = repository.downloadFileResumable(
                    fileUrl = download.fileUrl,
                    destinationPath = download.destinationPath,
                    existingBytes = download.downloadedBytes,
                    cookie = download.cookie,
                    onProgress = { downloadedBytes, speed ->
                        serviceScope.launch {
                            database.downloadDao().updateProgress(
                                downloadId,
                                downloadedBytes,
                                speed,
                                System.currentTimeMillis()
                            )
                            
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastHistoryUpdate >= 2000) {
                                database.speedHistoryDao().insertSpeedEntry(
                                    SpeedHistoryEntity(
                                        downloadId = downloadId,
                                        timestamp = currentTime,
                                        speed = speed
                                    )
                                )
                                lastHistoryUpdate = currentTime
                            }
                            
                            updateDownloadState(downloadId, downloadedBytes, speed, download.totalBytes)
                            updateNotification(downloadId, download.fileName, downloadedBytes, download.totalBytes, speed)
                        }
                    }
                )
                
                result.fold(
                    onSuccess = { filePath ->
                        database.downloadDao().updateStatus(downloadId, DownloadStatus.COMPLETED)
                        showCompletionNotification(download.notificationId, download.fileName, true)
                    },
                    onFailure = { error ->
                        database.downloadDao().updateStatusWithError(
                            downloadId,
                            DownloadStatus.FAILED,
                            error.message ?: "Unknown error"
                        )
                        showCompletionNotification(download.notificationId, download.fileName, false, error.message)
                    }
                )
                
            } catch (e: CancellationException) {
                database.downloadDao().updateStatus(downloadId, DownloadStatus.PAUSED)
            } catch (e: Exception) {
                database.downloadDao().updateStatusWithError(
                    downloadId,
                    DownloadStatus.FAILED,
                    e.message ?: "Unknown error"
                )
            } finally {
                activeDownloads.remove(downloadId)
                removeDownloadState(downloadId)
                checkStopService()
            }
        }
        
        activeDownloads[downloadId] = job
    }
    
    private fun pauseDownload(downloadId: Long) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        serviceScope.launch {
            database.downloadDao().updateStatus(downloadId, DownloadStatus.PAUSED)
        }
    }
    
    private fun resumeDownload(downloadId: Long) {
        startDownload(downloadId)
    }
    
    private fun cancelDownload(downloadId: Long) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        
        serviceScope.launch {
            val download = database.downloadDao().getDownloadByIdSync(downloadId)
            download?.let {
                try {
                    val file = File(it.destinationPath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                database.downloadDao().updateStatus(downloadId, DownloadStatus.CANCELLED)
                notificationManager.cancel(it.notificationId)
            }
        }
    }
    
    private fun resumeIncompleteDownloads() {
        serviceScope.launch {
            val pausedDownloads = database.downloadDao().getDownloadsByStatusSync(DownloadStatus.PAUSED)
            val downloadingDownloads = database.downloadDao().getDownloadsByStatusSync(DownloadStatus.DOWNLOADING)
            
            pausedDownloads.forEach { download ->
                database.downloadDao().updateStatus(download.id, DownloadStatus.PAUSED)
            }
            
            downloadingDownloads.forEach { download ->
                startDownload(download.id)
            }
        }
    }
    
    private fun updateDownloadState(downloadId: Long, downloadedBytes: Long, speed: Long, totalBytes: Long) {
        val currentStates = downloadStates.value.toMutableMap()
        currentStates[downloadId] = DownloadProgressState(downloadedBytes, speed, totalBytes)
        downloadStates.value = currentStates
    }
    
    private fun removeDownloadState(downloadId: Long) {
        val currentStates = downloadStates.value.toMutableMap()
        currentStates.remove(downloadId)
        downloadStates.value = currentStates
    }
    
    private fun updateNotification(downloadId: Long, fileName: String, downloadedBytes: Long, totalBytes: Long, speed: Long) {
        val download = activeDownloads.keys.firstOrNull { it == downloadId } ?: return
        
        serviceScope.launch {
            val downloadEntity = database.downloadDao().getDownloadByIdSync(downloadId) ?: return@launch
            
            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
            val speedText = FileUtils.formatFileSize(speed) + "/s"
            val downloadedText = FileUtils.formatFileSize(downloadedBytes)
            val totalText = FileUtils.formatFileSize(totalBytes)
            
            val pauseIntent = Intent(this@DownloadService, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val pausePendingIntent = PendingIntent.getService(
                this@DownloadService,
                downloadEntity.notificationId + 1,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val cancelIntent = Intent(this@DownloadService, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val cancelPendingIntent = PendingIntent.getService(
                this@DownloadService,
                downloadEntity.notificationId + 2,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle(fileName)
                .setContentText("$downloadedText / $totalText • $speedText")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
                .build()
            
            notificationManager.notify(downloadEntity.notificationId, notification)
        }
    }
    
    private fun showCompletionNotification(notificationId: Int, fileName: String, success: Boolean, errorMessage: String? = null) {
        val title = if (success) "Download Complete" else "Download Failed"
        val text = if (success) fileName else "$fileName: ${errorMessage ?: "Unknown error"}"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    private fun checkStopService() {
        if (activeDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
    }
    
    fun getDownloadStates(): StateFlow<Map<Long, DownloadProgressState>> = downloadStates
    
    override fun onDestroy() {
        activeDownloads.values.forEach { it.cancel() }
        serviceScope.cancel()
        super.onDestroy()
    }
}

data class DownloadProgressState(
    val downloadedBytes: Long,
    val speed: Long,
    val totalBytes: Long
)
