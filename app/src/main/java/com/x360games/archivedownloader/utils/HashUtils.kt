package com.x360games.archivedownloader.utils

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import android.content.Context

object HashUtils {
    
    suspend fun calculateMD5(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                throw IllegalArgumentException("URI paths not supported for hash calculation directly")
            }
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $filePath"))
            }
            
            val md5 = calculateHashForFile(file, "MD5")
            Result.success(md5)
        } catch (e: Exception) {
            Log.e("HashUtils", "Error calculating MD5", e)
            Result.failure(e)
        }
    }
    
    suspend fun calculateSHA256(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                throw IllegalArgumentException("URI paths not supported for hash calculation directly")
            }
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $filePath"))
            }
            
            val sha256 = calculateHashForFile(file, "SHA-256")
            Result.success(sha256)
        } catch (e: Exception) {
            Log.e("HashUtils", "Error calculating SHA-256", e)
            Result.failure(e)
        }
    }
    
    suspend fun calculateMD5ForUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream for URI"))
            
            val md5 = calculateHashForStream(inputStream, "MD5")
            Result.success(md5)
        } catch (e: Exception) {
            Log.e("HashUtils", "Error calculating MD5 for URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun calculateSHA256ForUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream for URI"))
            
            val sha256 = calculateHashForStream(inputStream, "SHA-256")
            Result.success(sha256)
        } catch (e: Exception) {
            Log.e("HashUtils", "Error calculating SHA-256 for URI", e)
            Result.failure(e)
        }
    }
    
    private fun calculateHashForFile(file: File, algorithm: String): String {
        FileInputStream(file).use { inputStream ->
            return calculateHashForStream(inputStream, algorithm)
        }
    }
    
    private fun calculateHashForStream(inputStream: InputStream, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        
        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    fun verifyHash(calculatedHash: String, expectedHash: String): Boolean {
        return calculatedHash.equals(expectedHash, ignoreCase = true)
    }
}
