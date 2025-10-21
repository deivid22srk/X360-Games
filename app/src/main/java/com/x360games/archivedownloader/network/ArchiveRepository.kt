package com.x360games.archivedownloader.network

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.x360games.archivedownloader.data.ArchiveItem
import com.x360games.archivedownloader.data.ArchiveMetadataResponse
import com.x360games.archivedownloader.data.X360Collection
import com.x360games.archivedownloader.database.DownloadPartEntity
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
    
    init {
        cleanupOrphanedTempFiles()
    }
    
    private fun cleanupOrphanedTempFiles() {
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.listFiles()?.filter { file ->
                file.name.startsWith(".temp_") && 
                (System.currentTimeMillis() - file.lastModified() > 7 * 24 * 60 * 60 * 1000)
            }?.forEach { file ->
                val deleted = file.delete()
                Log.d("ArchiveRepository", "Cleaned up old temp file: ${file.name} (deleted: $deleted)")
            }
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error cleaning up temp files", e)
        }
    }
    
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
        existingParts: List<DownloadPartEntity> = emptyList(),
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit,
        onPartProgress: ((partIndex: Int, partBytes: Long, partTotal: Long) -> Unit)? = null,
        onSavePartProgress: ((partIndex: Int, startByte: Long, endByte: Long, downloadedBytes: Long, isCompleted: Boolean) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ArchiveRepository", "=== Multi-part download DISABLED - using single-part ===")
            Log.d("ArchiveRepository", "Destination: $destinationPath")
            Log.d("ArchiveRepository", "Existing bytes: $existingBytes")
            
            return@withContext downloadFileSinglePart(
                fileUrl, destinationPath, existingBytes, cookie, onProgress
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
        existingParts: List<DownloadPartEntity> = emptyList(),
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit,
        onPartProgress: ((partIndex: Int, partBytes: Long, partTotal: Long) -> Unit)? = null,
        onSavePartProgress: ((partIndex: Int, startByte: Long, endByte: Long, downloadedBytes: Long, isCompleted: Boolean) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val partSize = totalBytes / parts
            
            val existingPartsMap = existingParts.associateBy { it.partIndex }
            val totalExistingBytes = existingParts.sumOf { it.downloadedBytes }
            
            val downloadedBytesAtomic = AtomicLong(totalExistingBytes)
            val lastUpdateTime = AtomicLong(System.currentTimeMillis())
            val lastDownloadedBytes = AtomicLong(totalExistingBytes)
            val progressMutex = Mutex()
            val partProgressMap = mutableMapOf<Int, AtomicLong>()
            (0 until parts).forEach { partIndex ->
                val existingPartBytes = existingPartsMap[partIndex]?.downloadedBytes ?: 0L
                partProgressMap[partIndex] = AtomicLong(existingPartBytes)
            }
            
            Log.d("ArchiveRepository", "=== Multi-Part Download Started ===")
            Log.d("ArchiveRepository", "Total Size: ${totalBytes / 1024 / 1024} MB")
            Log.d("ArchiveRepository", "Parts: $parts")
            Log.d("ArchiveRepository", "Part Size: ${partSize / 1024 / 1024} MB each")
            Log.d("ArchiveRepository", "Existing Parts: ${existingParts.size}, Total Existing Bytes: ${totalExistingBytes / 1024 / 1024} MB")
            existingParts.forEach { part ->
                Log.d("ArchiveRepository", "  Part ${part.partIndex}: ${part.downloadedBytes}/${part.endByte - part.startByte + 1} bytes (${if(part.isCompleted) "COMPLETED" else "INCOMPLETE"})")
            }
            
            val partRanges = mutableListOf<Pair<Long, Long>>()
            (0 until parts).forEach { partIndex ->
                val start = partIndex * partSize
                val end = if (partIndex == parts - 1) totalBytes - 1 else (partIndex + 1) * partSize - 1
                partRanges.add(start to end)
            }
            
            Log.d("ArchiveRepository", "=== Verificando ranges das partes ===")
            partRanges.forEachIndexed { index, range ->
                Log.d("ArchiveRepository", "Parte $index: ${range.first}-${range.second} (${range.second - range.first + 1} bytes)")
                
                if (index > 0) {
                    val prevRange = partRanges[index - 1]
                    if (range.first <= prevRange.second) {
                        Log.e("ArchiveRepository", "ERRO: Parte $index sobrepõe parte ${index - 1}! ${range.first} <= ${prevRange.second}")
                    } else if (range.first != prevRange.second + 1) {
                        Log.e("ArchiveRepository", "ERRO: GAP entre parte ${index - 1} e $index! ${prevRange.second} -> ${range.first}")
                    }
                }
            }
            
            val totalCoverage = partRanges.sumOf { it.second - it.first + 1 }
            if (totalCoverage != totalBytes) {
                Log.e("ArchiveRepository", "ERRO: Total de bytes cobertos ($totalCoverage) != totalBytes ($totalBytes)")
            } else {
                Log.d("ArchiveRepository", "✓ Ranges corretos: $totalCoverage bytes cobertos")
            }
            
            if (!file.exists()) {
                file.createNewFile()
                Log.d("ArchiveRepository", "File created: ${file.absolutePath}")
            } else {
                Log.d("ArchiveRepository", "File already exists with size: ${file.length()} bytes")
            }
            
            Log.d("ArchiveRepository", "Allocating file space for ${totalBytes / 1024 / 1024} MB...")
            
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    val currentSize = raf.length()
                    
                    if (currentSize < totalBytes) {
                        Log.d("ArchiveRepository", "Current: ${currentSize / 1024 / 1024} MB, Target: ${totalBytes / 1024 / 1024} MB")
                        
                        raf.setLength(totalBytes)
                        
                        val writePositions = listOf(
                            0L,
                            totalBytes / 4,
                            totalBytes / 2,
                            (totalBytes * 3) / 4,
                            totalBytes - 1
                        )
                        
                        for (pos in writePositions) {
                            raf.seek(pos)
                            raf.write(0)
                        }
                        
                        raf.channel.force(true)
                        raf.fd.sync()
                        
                        Log.d("ArchiveRepository", "File allocated: ${file.length()} bytes")
                    } else {
                        Log.d("ArchiveRepository", "File already has size: ${currentSize / 1024 / 1024} MB")
                    }
                }
                
                Thread.sleep(100)
                
                val verifiedSize = file.length()
                Log.d("ArchiveRepository", "Verified size: ${verifiedSize / 1024 / 1024} MB (expected: ${totalBytes / 1024 / 1024} MB)")
                Log.d("ArchiveRepository", "File path: ${file.absolutePath}")
                
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null
                ) { path, uri ->
                    Log.d("ArchiveRepository", "Media scanner notified for: $path")
                }
                
            } catch (e: Exception) {
                Log.e("ArchiveRepository", "Error allocating file: ${e.message}", e)
            }
            
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                val jobs = (0 until parts).map { partIndex ->
                    async {
                        val start = partIndex * partSize
                        val end = if (partIndex == parts - 1) totalBytes - 1 else (partIndex + 1) * partSize - 1
                        val partTotal = end - start + 1
                        
                        val existingPart = existingPartsMap[partIndex]
                        val isAlreadyCompleted = existingPart?.isCompleted == true
                        
                        if (isAlreadyCompleted) {
                            Log.d("ArchiveRepository", "Part $partIndex: SKIPPING (already completed)")
                            return@async
                        }
                        
                        val partDownloadedBytes = existingPart?.downloadedBytes ?: 0L
                        val adjustedStart = start + partDownloadedBytes
                        
                        if (adjustedStart >= end + 1) {
                            Log.d("ArchiveRepository", "Part $partIndex: COMPLETED (adjustedStart=$adjustedStart >= end+1=${end+1})")
                            onSavePartProgress?.invoke(partIndex, start, end, partTotal, true)
                            return@async
                        }
                        
                        Log.d("ArchiveRepository", "Part $partIndex: Range bytes=$adjustedStart-$end (${(end - adjustedStart + 1) / 1024 / 1024} MB) [Original: $start-$end, Downloaded: ${partDownloadedBytes / 1024 / 1024} MB]")
                        
                        val partStartTime = System.currentTimeMillis()
                        
                        downloadPartWithRetry(
                            fileUrl = fileUrl,
                            start = adjustedStart,
                            end = end,
                            cookie = cookie,
                            randomAccessFile = randomAccessFile,
                            downloadedBytesAtomic = downloadedBytesAtomic,
                            lastUpdateTime = lastUpdateTime,
                            lastDownloadedBytes = lastDownloadedBytes,
                            progressMutex = progressMutex,
                            totalBytes = totalBytes,
                            onProgress = onProgress,
                            partIndex = partIndex,
                            partProgressMap = partProgressMap,
                            onPartProgress = onPartProgress,
                            partOriginalStart = start,
                            partTotal = partTotal,
                            onSavePartProgress = onSavePartProgress
                        )
                        
                        val partDuration = System.currentTimeMillis() - partStartTime
                        val partSpeed = if (partDuration > 0) ((end - start) * 1000) / partDuration else 0
                        Log.d("ArchiveRepository", "Part $partIndex completed in ${partDuration / 1000}s (${partSpeed / 1024} KB/s)")
                    }
                }
                
                jobs.awaitAll()
                Log.d("ArchiveRepository", "=== All parts downloaded successfully ===")
            }
            
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                Log.d("ArchiveRepository", "Download completed - Media scanner notified for: $path")
            }
            
            Log.d("ArchiveRepository", "Final file size: ${file.length()} bytes (${file.length() / 1024 / 1024} MB)")
            
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
        partIndex: Int = -1,
        partProgressMap: MutableMap<Int, AtomicLong>? = null,
        onPartProgress: ((partIndex: Int, partBytes: Long, partTotal: Long) -> Unit)? = null,
        partOriginalStart: Long = start,
        partTotal: Long = end - start + 1,
        onSavePartProgress: ((partIndex: Int, startByte: Long, endByte: Long, downloadedBytes: Long, isCompleted: Boolean) -> Unit)? = null,
        maxRetries: Int = 3
    ) {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                downloadPart(
                    fileUrl, start, end, cookie, randomAccessFile,
                    downloadedBytesAtomic, lastUpdateTime, lastDownloadedBytes,
                    progressMutex, totalBytes, onProgress, partIndex, partProgressMap, onPartProgress,
                    partOriginalStart, partTotal, onSavePartProgress
                )
                if (partIndex >= 0 && onSavePartProgress != null) {
                    val partEnd = partOriginalStart + partTotal - 1
                    onSavePartProgress.invoke(partIndex, partOriginalStart, partEnd, partTotal, true)
                    Log.d("ArchiveRepository", "Part $partIndex marked as COMPLETED in database")
                }
                return
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    Log.w("ArchiveRepository", "Part $partIndex download failed, retry $attempt/$maxRetries: ${e.message}")
                    kotlinx.coroutines.delay(1000L * attempt)
                } else {
                    Log.e("ArchiveRepository", "Part $partIndex failed after $maxRetries retries", e)
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
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit,
        partIndex: Int = -1,
        partProgressMap: MutableMap<Int, AtomicLong>? = null,
        onPartProgress: ((partIndex: Int, partBytes: Long, partTotal: Long) -> Unit)? = null,
        partOriginalStart: Long = start,
        partTotal: Long = end - start + 1,
        onSavePartProgress: ((partIndex: Int, startByte: Long, endByte: Long, downloadedBytes: Long, isCompleted: Boolean) -> Unit)? = null
    ) {
        val rangeHeader = "bytes=$start-$end"
        val response = archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
        
        if (!response.isSuccessful) {
            val errorMsg = "Part download failed: HTTP ${response.code()} - ${response.message()}"
            Log.e("ArchiveRepository", errorMsg)
            Log.e("ArchiveRepository", "Range: $rangeHeader")
            throw Exception(errorMsg)
        }
        
        val body = response.body() ?: throw Exception("Empty response body for part")
        
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            var bytes: Int
            var currentPosition = start
            
            var currentSpeed = 0L
            var speedSampleStart = System.currentTimeMillis()
            var speedSampleBytes = 0L
            
            var lastSyncTime = System.currentTimeMillis()
            var lastSyncBytes = currentPosition
            val syncInterval = 2000L
            val syncBytesThreshold = 65536L
            
            while (input.read(buffer).also { bytes = it } >= 0) {
                coroutineContext.ensureActive()
                
                synchronized(randomAccessFile) {
                    randomAccessFile.seek(currentPosition)
                    randomAccessFile.write(buffer, 0, bytes)
                }
                
                currentPosition += bytes
                speedSampleBytes += bytes
                val totalDownloaded = downloadedBytesAtomic.addAndGet(bytes.toLong())
                
                val currentTime = System.currentTimeMillis()
                
                if (partIndex >= 0 && partProgressMap != null) {
                    val partDownloaded = partProgressMap[partIndex]?.addAndGet(bytes.toLong()) ?: 0L
                    onPartProgress?.invoke(partIndex, partDownloaded, partTotal)
                }
                val sampleDelta = currentTime - speedSampleStart
                
                if (sampleDelta > 500) {
                    val sampleSpeed = (speedSampleBytes * 1000) / sampleDelta
                    currentSpeed = if (currentSpeed == 0L) {
                        sampleSpeed
                    } else {
                        ((currentSpeed * 9) + sampleSpeed) / 10
                    }
                    
                    speedSampleStart = currentTime
                    speedSampleBytes = 0
                }
                
                val bytesSinceSync = currentPosition - lastSyncBytes
                val timeSinceSync = currentTime - lastSyncTime
                
                if (bytesSinceSync >= syncBytesThreshold || timeSinceSync >= syncInterval) {
                    synchronized(randomAccessFile) {
                        randomAccessFile.fd.sync()
                    }
                    lastSyncTime = currentTime
                    lastSyncBytes = currentPosition
                    
                    if (partIndex >= 0 && onSavePartProgress != null && partProgressMap != null) {
                        val partDownloaded = partProgressMap[partIndex]?.get() ?: 0L
                        val partEnd = partOriginalStart + partTotal - 1
                        onSavePartProgress.invoke(partIndex, partOriginalStart, partEnd, partDownloaded, false)
                    }
                    
                    progressMutex.withLock {
                        val bytesDiff = totalDownloaded - lastDownloadedBytes.get()
                        val timeDiff = currentTime - lastUpdateTime.get()
                        
                        val instantSpeed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                        val reportSpeed = if (currentSpeed > 0) {
                            ((currentSpeed * 7) + (instantSpeed * 3)) / 10
                        } else {
                            instantSpeed
                        }
                        
                        onProgress(totalDownloaded, reportSpeed)
                        
                        lastUpdateTime.set(currentTime)
                        lastDownloadedBytes.set(totalDownloaded)
                    }
                }
            }
            
            synchronized(randomAccessFile) {
                randomAccessFile.fd.sync()
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
                    fileUrl, destinationPath, existingBytes, cookie, 1, emptyList(), onProgress, null, null
                )
            }
            
            val file = File(destinationPath)
            
            if (!file.parentFile?.exists()!!) {
                file.parentFile?.mkdirs()
            }
            
            Log.d("ArchiveRepository", "=== Single-Part Download Started ===")
            Log.d("ArchiveRepository", "URL: $fileUrl")
            Log.d("ArchiveRepository", "Destination: ${file.absolutePath}")
            Log.d("ArchiveRepository", "Existing bytes: $existingBytes")
            Log.d("ArchiveRepository", "File exists: ${file.exists()}, current size: ${if (file.exists()) file.length() else 0}")
            
            val response = if (existingBytes > 0 && file.exists()) {
                val rangeHeader = "bytes=$existingBytes-"
                Log.d("ArchiveRepository", "Resuming download with Range: $rangeHeader")
                archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
            } else {
                Log.d("ArchiveRepository", "Starting new download")
                archiveApi.downloadFile(fileUrl, cookie)
            }
            
            if (!response.isSuccessful) {
                Log.e("ArchiveRepository", "Download failed: HTTP ${response.code()} - ${response.message()}")
                return@withContext Result.failure(Exception("Download failed: ${response.code()}"))
            }
            
            Log.d("ArchiveRepository", "Response headers:")
            Log.d("ArchiveRepository", "  - Content-Length: ${response.headers()["Content-Length"]}")
            Log.d("ArchiveRepository", "  - Content-Range: ${response.headers()["Content-Range"]}")
            Log.d("ArchiveRepository", "  - Server: ${response.headers()["Server"]}")
            
            val body = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
            
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: body.contentLength()
            val totalBytes = if (existingBytes > 0) existingBytes + contentLength else contentLength
            var downloadedBytes = existingBytes
            
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            
            Log.d("ArchiveRepository", "Total size: ${totalBytes / 1024 / 1024} MB")
            
            val downloadStartTime = System.currentTimeMillis()
            
            body.byteStream().use { input ->
                FileOutputStream(file, existingBytes > 0).use { output ->
                    val buffer = ByteArray(8192 * 32)
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
            
            val downloadDuration = System.currentTimeMillis() - downloadStartTime
            val avgSpeed = if (downloadDuration > 0) ((downloadedBytes - existingBytes) * 1000) / downloadDuration else 0
            Log.d("ArchiveRepository", "=== Download completed in ${downloadDuration / 1000}s (Avg: ${avgSpeed / 1024} KB/s) ===")
            
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
        parts: Int = 4,
        existingParts: List<DownloadPartEntity> = emptyList(),
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit,
        onPartProgress: ((partIndex: Int, partBytes: Long, partTotal: Long) -> Unit)? = null,
        onSavePartProgress: ((partIndex: Int, startByte: Long, endByte: Long, downloadedBytes: Long, isCompleted: Boolean) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("ArchiveRepository", "=== URI Download (Single-Part ONLY) ===")
            Log.d("ArchiveRepository", "URI: $destinationUri")
            Log.d("ArchiveRepository", "Existing bytes: $existingBytes")
            
            return@withContext downloadFileResumableUriSinglePart(
                fileUrl, destinationUri, existingBytes, cookie, onProgress
            )
            
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error downloading file to URI", e)
            Result.failure(e)
        }
    }
    
    private suspend fun copyFileToUri(sourceFile: File, destinationUri: Uri) = withContext(Dispatchers.IO) {
        sourceFile.inputStream().use { input ->
            context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                val buffer = ByteArray(8192 * 32)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } >= 0) {
                    output.write(buffer, 0, bytes)
                }
                output.flush()
            } ?: throw Exception("Could not open output stream for URI")
        }
    }
    
    private suspend fun downloadFileResumableUriSinglePart(
        fileUrl: String,
        destinationUri: String,
        existingBytes: Long = 0,
        cookie: String? = null,
        onProgress: (downloadedBytes: Long, speed: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(destinationUri)
            
            Log.d("ArchiveRepository", "=== URI Single-Part Download ===")
            
            val response = if (existingBytes > 0) {
                val rangeHeader = "bytes=$existingBytes-"
                Log.d("ArchiveRepository", "Resuming with Range: $rangeHeader")
                archiveApi.downloadFileWithRange(fileUrl, rangeHeader, cookie)
            } else {
                archiveApi.downloadFile(fileUrl, cookie)
            }
            
            if (!response.isSuccessful) {
                Log.e("ArchiveRepository", "Download failed: HTTP ${response.code()}")
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
                    val buffer = ByteArray(8192 * 32)
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
    
    fun cleanupTempFileForDownload(destinationUri: String) {
        try {
            if (!destinationUri.startsWith("content://")) return
            
            val uri = Uri.parse(destinationUri)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            val displayName = documentFile?.name ?: return
            val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            
            val tempFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), ".temp_$sanitizedName")
            if (tempFile.exists()) {
                val deleted = tempFile.delete()
                Log.d("ArchiveRepository", "Cleaned up temp file for cancelled/removed download: $sanitizedName (deleted: $deleted)")
            }
        } catch (e: Exception) {
            Log.e("ArchiveRepository", "Error cleaning up temp file", e)
        }
    }
}
