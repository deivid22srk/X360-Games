package com.x360games.archivedownloader.network

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.x360games.archivedownloader.data.ArchiveItem
import com.x360games.archivedownloader.data.ArchiveMetadataResponse
import com.x360games.archivedownloader.data.X360Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class ArchiveRepository(private val context: Context) {
    private val githubApi = RetrofitClient.githubApi
    private val archiveApi = RetrofitClient.archiveApi
    
    suspend fun getX360Collection(): Result<X360Collection> = withContext(Dispatchers.IO) {
        try {
            val collection = githubApi.getX360Collection()
            Result.success(collection)
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error getting collection", e)
            Result.failure(e)
        }
    }
    
    suspend fun getAllArchiveItems(collection: X360Collection): Result<List<ArchiveItem>> = 
        withContext(Dispatchers.IO) {
            try {
                val items = collection.items.map { item ->
                    async {
                        try {
                            val identifier = extractIdentifier(item.url)
                            val metadata = archiveApi.getMetadata(identifier)
                            ArchiveItem(
                                id = item.id,
                                title = metadata.metadata?.title ?: item.id,
                                url = item.url,
                                files = metadata.files ?: emptyList()
                            )
                        } catch (e: Exception) {
                            Log.e("ArchiveRepository", "Error getting metadata for ${item.id}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                
                Result.success(items)
            } catch (e: Exception) {
                Log.e("ArchiveRepository", "Error getting all items", e)
                Result.failure(e)
            }
        }
    
    suspend fun downloadFile(
        fileUrl: String,
        fileName: String,
        destinationDir: File,
        cookie: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = archiveApi.downloadFile(fileUrl, cookie)
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code()}"))
            }
            
            val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
            
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            
            val file = File(destinationDir, fileName)
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            onProgress(progress)
                        }
                        
                        bytes = input.read(buffer)
                    }
                }
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadFileToUri(
        fileUrl: String,
        fileName: String,
        destinationUri: Uri,
        cookie: String? = null,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = archiveApi.downloadFile(fileUrl, cookie)
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code()}"))
            }
            
            val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val documentFile = DocumentFile.fromTreeUri(context, destinationUri)
                ?: return@withContext Result.failure(Exception("Invalid destination folder"))
            
            val newFile = documentFile.createFile("application/octet-stream", fileName)
                ?: return@withContext Result.failure(Exception("Could not create file"))
            
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            body.byteStream().use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            onProgress(progress)
                        }
                        
                        bytes = input.read(buffer)
                    }
                } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            }
            
            Result.success(newFile.uri.toString())
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file to URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadFileResumable(
        fileUrl: String,
        destinationPath: String,
        existingBytes: Long = 0,
        cookie: String? = null,
        parts: Int = 4,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (destinationPath.startsWith("content://")) {
                return@withContext downloadFileResumableUri(
                    fileUrl, destinationPath, existingBytes, cookie, onProgress
                )
            }
            
            val file = File(destinationPath)
            
            if (!file.parentFile?.exists()!!) {
                file.parentFile?.mkdirs()
            }
            
            if (parts <= 1 || existingBytes > 0) {
                return@withContext downloadFileSinglePart(
                    fileUrl, destinationPath, existingBytes, cookie, onProgress
                )
            }
            
            val headResponse = archiveApi.getFileInfo(fileUrl, cookie)
            val totalBytes = headResponse.headers()["Content-Length"]?.toLongOrNull() ?: 0L
            val acceptsRanges = headResponse.headers()["Accept-Ranges"]?.equals("bytes", ignoreCase = true) ?: false
            
            if (!acceptsRanges || totalBytes == 0L) {
                Log.d("ArchiveRepository", "Server doesn't support ranges, falling back to single-part download")
                return@withContext downloadFileSinglePart(
                    fileUrl, destinationPath, 0, cookie, onProgress
                )
            }
            
            return@withContext downloadFileMultiPart(
                fileUrl, file, totalBytes, parts, cookie, onProgress
            )
            
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file resumable", e)
            Result.failure(e)
        }
    }
    
    private suspend fun downloadFileMultiPart(
        fileUrl: String,
        file: File,
        totalBytes: Long,
        parts: Int,
        cookie: String?,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val partSize = totalBytes / parts
            val downloadedBytesAtomic = AtomicLong(0)
            val lastUpdateTime = AtomicLong(System.currentTimeMillis())
            val lastDownloadedBytes = AtomicLong(0)
            val progressMutex = Mutex()
            
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                randomAccessFile.setLength(totalBytes)
                
                val jobs = (0 until parts).map { partIndex ->
                    async {
                        val start = partIndex * partSize
                        val end = if (partIndex == parts - 1) totalBytes - 1 else (partIndex + 1) * partSize - 1
                        
                        downloadPartWithRetry(
                            fileUrl = fileUrl,
                            start = start,
                            end = end,
                            cookie = cookie,
                            randomAccessFile = randomAccessFile,
                            downloadedBytesAtomic = downloadedBytesAtomic,
                            lastUpdateTime = lastUpdateTime,
                            lastDownloadedBytes = lastDownloadedBytes,
                            progressMutex = progressMutex,
                            totalBytes = totalBytes,
                            onProgress = onProgress
                        )
                    }
                }
                
                jobs.awaitAll()
            }
            
            onProgress(totalBytes, 0)
            Result.success(file.absolutePath)
            
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error in multi-part download", e)
            Result.failure(e)
        }
    }
    
    private suspend fun downloadPartWithRetry(
        fileUrl: String,
        start: Long,
        end: Long,
        cookie: String?,
        randomAccessFile: RandomAccessFile,
        downloadedBytesAtomic: AtomicLong,
        lastUpdateTime: AtomicLong,
        lastDownloadedBytes: AtomicLong,
        progressMutex: Mutex,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit,
        maxRetries: Int = 3
    ) {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                downloadPart(
                    fileUrl, start, end, cookie, randomAccessFile,
                    downloadedBytesAtomic, lastUpdateTime, lastDownloadedBytes,
                    progressMutex, totalBytes, onProgress
                )
                return
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    Log.w("ArchiveRepository", "Part download failed, retry $attempt/$maxRetries", e)
                    kotlinx.coroutines.delay(1000L * attempt)
                }
            }
        }
        
        throw lastError ?: Exception("Failed to download part after $maxRetries retries")
    }
    
    private suspend fun downloadPart(
        fileUrl: String,
        start: Long,
        end: Long,
        cookie: String?,
        randomAccessFile: RandomAccessFile,
        downloadedBytesAtomic: AtomicLong,
        lastUpdateTime: AtomicLong,
        lastDownloadedBytes: AtomicLong,
        progressMutex: Mutex,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ) {
        val rangeHeader = "bytes=$start-$end"
        val response = archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
        
        if (!response.isSuccessful) {
            throw Exception("Part download failed: ${response.code()}")
        }
        
        val body = response.body() ?: throw Exception("Empty response body for part")
        
        body.byteStream().use { input ->
            val buffer = ByteArray(8192 * 16)
            var bytes: Int
            var currentPosition = start
            
            while (input.read(buffer).also { bytes = it } >= 0) {
                coroutineContext.ensureActive()
                
                synchronized(randomAccessFile) {
                    randomAccessFile.seek(currentPosition)
                    randomAccessFile.write(buffer, 0, bytes)
                }
                
                currentPosition += bytes
                val totalDownloaded = downloadedBytesAtomic.addAndGet(bytes.toLong())
                
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastUpdateTime.get()
                
                if (timeDiff >= 500) {
                    progressMutex.withLock {
                        if (System.currentTimeMillis() - lastUpdateTime.get() >= 500) {
                            val bytesDiff = totalDownloaded - lastDownloadedBytes.get()
                            val speed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0
                            
                            onProgress(totalDownloaded, speed)
                            
                            lastUpdateTime.set(currentTime)
                            lastDownloadedBytes.set(totalDownloaded)
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun downloadFileSinglePart(
        fileUrl: String,
        destinationPath: String,
        existingBytes: Long,
        cookie: String?,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (destinationPath.startsWith("content://")) {
                return@withContext downloadFileResumableUri(
                    fileUrl, destinationPath, existingBytes, cookie, onProgress
                )
            }
            
            val file = File(destinationPath)
            
            if (!file.parentFile?.exists()!!) {
                file.parentFile?.mkdirs()
            }
            
            val response = if (existingBytes > 0 && file.exists()) {
                val rangeHeader = "bytes=$existingBytes-"
                archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
            } else {
                archiveApi.downloadFile(fileUrl, cookie)
            }
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code()}"))
            }
            
            val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: body.contentLength()
            val totalBytes = if (existingBytes > 0) existingBytes + contentLength else contentLength
            var downloadedBytes = existingBytes
            
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            
            body.byteStream().use { input ->
                FileOutputStream(file, existingBytes > 0).use { output ->
                    val buffer = ByteArray(8192 * 16)
                    var bytes: Int
                    var flushCounter = 0
                    
                    while (input.read(buffer).also { bytes = it } >= 0) {
                        coroutineContext.ensureActive()
                        
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        flushCounter++
                        
                        if (flushCounter >= 10) {
                            output.flush()
                            flushCounter = 0
                        }
                        
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdateTime
                        
                        if (timeDiff >= 500) {
                            val bytesDiff = downloadedBytes - lastDownloadedBytes
                            val speed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0
                            
                            onProgress(downloadedBytes, speed)
                            
                            lastUpdateTime = currentTime
                            lastDownloadedBytes = downloadedBytes
                        }
                    }
                    
                    output.flush()
                    onProgress(downloadedBytes, 0)
                }
            }
            
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file resumable", e)
            Result.failure(e)
        }
    }
    
    private suspend fun downloadFileResumableUri(
        fileUrl: String,
        destinationUri: String,
        existingBytes: Long = 0,
        cookie: String? = null,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(destinationUri)
            
            val response = if (existingBytes > 0) {
                val rangeHeader = "bytes=$existingBytes-"
                archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
            } else {
                archiveApi.downloadFile(fileUrl, cookie)
            }
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code()}"))
            }
            
            val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: body.contentLength()
            val totalBytes = if (existingBytes > 0) existingBytes + contentLength else contentLength
            var downloadedBytes = existingBytes
            
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            
            body.byteStream().use { input ->
                context.contentResolver.openOutputStream(uri, if (existingBytes > 0) "wa" else "w")?.use { output ->
                    val buffer = ByteArray(8192 * 16)
                    var bytes: Int
                    var flushCounter = 0
                    
                    while (input.read(buffer).also { bytes = it } >= 0) {
                        coroutineContext.ensureActive()
                        
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        flushCounter++
                        
                        if (flushCounter >= 10) {
                            output.flush()
                            flushCounter = 0
                        }
                        
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdateTime
                        
                        if (timeDiff >= 500) {
                            val bytesDiff = downloadedBytes - lastDownloadedBytes
                            val speed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0
                            
                            onProgress(downloadedBytes, speed)
                            
                            lastUpdateTime = currentTime
                            lastDownloadedBytes = downloadedBytes
                        }
                    }
                    
                    output.flush()
                    onProgress(downloadedBytes, 0)
                } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            }
            
            Result.success(destinationUri)
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file resumable URI", e)
            Result.failure(e)
        }
    }
    
    private fun extractIdentifier(url: String): String {
        return url.substringAfterLast("/")
    }
}
