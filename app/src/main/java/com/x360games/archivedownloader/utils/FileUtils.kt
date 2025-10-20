package com.x360games.archivedownloader.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FileUtils {
    fun getDefaultDownloadDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "X360Games")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "X360Games")
        }.apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    fun getDownloadDirectory(context: Context, customPath: String?): File {
        return if (customPath != null) {
            try {
                val uri = Uri.parse(customPath)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                if (docFile?.exists() == true && docFile.canWrite()) {
                    File(docFile.uri.path ?: "")
                } else {
                    getDefaultDownloadDirectory(context)
                }
            } catch (e: Exception) {
                getDefaultDownloadDirectory(context)
            }
        } else {
            getDefaultDownloadDirectory(context)
        }
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    fun parseFileSize(sizeStr: String?): Long {
        if (sizeStr == null) return 0
        
        return try {
            sizeStr.toLong()
        } catch (e: NumberFormatException) {
            0
        }
    }
}
