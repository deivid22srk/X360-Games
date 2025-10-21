package com.x360games.archivedownloader.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
    
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }
    
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var fileSize: Long = 0
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                fileSize = cursor.getLong(sizeIndex)
            }
        }
        return fileSize
    }
    
    /**
     * Obtém o caminho real do arquivo a partir do URI.
     * Se não conseguir obter o caminho direto, retorna null.
     */
    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path
            "content" -> getRealPathFromContentUri(context, uri)
            else -> null
        }
    }
    
    private fun getRealPathFromContentUri(context: Context, uri: Uri): String? {
        // Tentar obter caminho de DocumentsProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (uri.authority == "com.android.externalstorage.documents") {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            }
            // DownloadsProvider
            else if (uri.authority == "com.android.providers.downloads.documents") {
                val docId = DocumentsContract.getDocumentId(uri)
                
                // Verificar se é um caminho direto
                if (docId.startsWith("raw:")) {
                    return docId.replaceFirst("raw:", "")
                }
                
                // Tentar obter de MediaStore
                val contentUri = android.content.ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    docId.toLongOrNull() ?: 0
                )
                return getDataColumn(context, contentUri, null, null)
            }
            // MediaProvider
            else if (uri.authority == "com.android.providers.media.documents") {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                
                val contentUri = when (type) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (e outros Content Providers)
        else if (uri.scheme == "content") {
            return getDataColumn(context, uri, null, null)
        }
        
        return null
    }
    
    private fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            return null
        } finally {
            cursor?.close()
        }
        return null
    }
}
