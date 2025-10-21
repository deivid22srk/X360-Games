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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.x360games.archivedownloader.MainActivity
import com.x360games.archivedownloader.R
import com.x360games.archivedownloader.utils.Iso2GodConverter
import kotlinx.coroutines.*
import java.io.File

class Iso2GodService : Service() {
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    
    private var currentConversionJob: Job? = null
    private var isForeground = false
    
    companion object {
        const val CHANNEL_ID = "iso2god_service_channel"
        const val NOTIFICATION_ID = 2000
        
        const val ACTION_CONVERT_ISO = "action_convert_iso"
        const val ACTION_CANCEL_CONVERSION = "action_cancel_conversion"
        
        const val EXTRA_ISO_PATH = "iso_path"
        const val EXTRA_OUTPUT_PATH = "output_path"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): Iso2GodService = this@Iso2GodService
    }
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_CONVERT_ISO -> {
                val isoPath = intent.getStringExtra(EXTRA_ISO_PATH) ?: return
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return
                startConversion(isoPath, outputPath)
            }
            ACTION_CANCEL_CONVERSION -> {
                cancelConversion()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ISO to GOD Converter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ISO to GOD conversion progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        if (!isForeground) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ISO to GOD Converter")
                .setContentText("Preparando conversão...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        }
    }
    
    private fun startConversion(isoPath: String, outputPath: String) {
        if (currentConversionJob?.isActive == true) {
            Log.w("Iso2GodService", "Conversion already in progress")
            return
        }
        
        startForeground()
        
        currentConversionJob = serviceScope.launch {
            try {
                val isoFile = File(isoPath)
                if (!isoFile.exists()) {
                    showErrorNotification("Arquivo ISO não encontrado")
                    return@launch
                }
                
                Log.d("Iso2GodService", "Starting conversion: $isoPath -> $outputPath")
                
                val converter = Iso2GodConverter(applicationContext)
                val result = converter.convertIsoToGod(
                    isoPath = isoPath,
                    outputPath = outputPath,
                    onProgress = { progress, status ->
                        updateProgressNotification(
                            isoFile.name,
                            progress,
                            status
                        )
                    }
                )
                
                result.fold(
                    onSuccess = { godPath ->
                        Log.d("Iso2GodService", "Conversion completed: $godPath")
                        showCompletionNotification(isoFile.name, true)
                        cleanupTempFile(isoPath)
                    },
                    onFailure = { error ->
                        Log.e("Iso2GodService", "Conversion failed", error)
                        showCompletionNotification(isoFile.name, false, error.message)
                        cleanupTempFile(isoPath)
                    }
                )
                
            } catch (e: CancellationException) {
                Log.d("Iso2GodService", "Conversion cancelled")
                showCancelledNotification()
            } catch (e: Exception) {
                Log.e("Iso2GodService", "Conversion error", e)
                showErrorNotification(e.message ?: "Erro desconhecido")
            } finally {
                currentConversionJob = null
                checkStopService()
            }
        }
    }
    
    private fun cancelConversion() {
        currentConversionJob?.cancel()
        currentConversionJob = null
    }
    
    private fun cleanupTempFile(isoPath: String) {
        try {
            val isoFile = File(isoPath)
            // Apenas deletar se estiver no diretório temporário do app
            if (isoPath.contains("iso_temp")) {
                if (isoFile.exists() && isoFile.delete()) {
                    Log.d("Iso2GodService", "Cleaned up temp file: $isoPath")
                }
            }
        } catch (e: Exception) {
            Log.e("Iso2GodService", "Error cleaning up temp file", e)
        }
    }
    
    private fun updateProgressNotification(fileName: String, progress: Float, status: String) {
        val progressPercent = (progress * 100).toInt()
        
        val cancelIntent = Intent(this, Iso2GodService::class.java).apply {
            action = ACTION_CANCEL_CONVERSION
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Convertendo: $fileName")
            .setContentText("$status - $progressPercent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_delete, "Cancelar", cancelPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showCompletionNotification(fileName: String, success: Boolean, errorMessage: String? = null) {
        val title = if (success) "Conversão Concluída" else "Conversão Falhou"
        val text = if (success) {
            "$fileName convertido com sucesso!"
        } else {
            "Falha ao converter $fileName: ${errorMessage ?: "Erro desconhecido"}"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showCancelledNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversão Cancelada")
            .setContentText("A conversão foi cancelada pelo usuário")
            .setSmallIcon(android.R.drawable.ic_delete)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Erro na Conversão")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun checkStopService() {
        if (currentConversionJob == null || currentConversionJob?.isActive == false) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        currentConversionJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
