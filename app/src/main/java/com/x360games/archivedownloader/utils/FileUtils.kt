package com.x360games.archivedownloader.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
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
    
    /**
     * Obtém o nome do arquivo a partir do URI
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Fallback: tentar obter do path do URI
            fileName = uri.lastPathSegment
        }
        return fileName
    }
    
    /**
     * Obtém o tamanho do arquivo a partir do URI
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var fileSize: Long = 0
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            // Se falhar, retornar 0
            fileSize = 0
        }
        return fileSize
    }
}
