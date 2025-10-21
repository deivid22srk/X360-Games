package com.x360games.archivedownloader.network

import android.content.Context
import android.net.Uri
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
            if (destinationPath.startsWith("content://")) {
                return@withContext downloadFileResumableUri(
                    fileUrl, destinationPath, existingBytes, cookie, parts, existingParts, onProgress, onPartProgress, onSavePartProgress
                )
            }
            
            val file = File(destinationPath)
            
            if (!file.parentFile?.exists()!!) {
                file.parentFile?.mkdirs()
            }
            
            if (parts <= 1) {
                Log.d("ArchiveRepository", "Using single-part download. Parts=$parts")
                return@withContext downloadFileSinglePart(
                    fileUrl, destinationPath, existingBytes, cookie, onProgress
                )
            }
            
            Log.d("ArchiveRepository", "Checking server support for multi-part download...")
            Log.d("ArchiveRepository", "URL: $fileUrl")
            
            val headResponse = archiveApi.getFileInfo(fileUrl, cookie)
            val totalBytes = headResponse.headers()["Content-Length"]?.toLongOrNull() ?: 0L
            val acceptsRanges = headResponse.headers()["Accept-Ranges"]?.equals("bytes", ignoreCase = true) ?: false
            val serverHeader = headResponse.headers()["Server"] ?: "Unknown"
            
            Log.d("ArchiveRepository", "Server Response Headers:")
            Log.d("ArchiveRepository", "  - Content-Length: $totalBytes bytes")
            Log.d("ArchiveRepository", "  - Accept-Ranges: ${headResponse.headers()["Accept-Ranges"]}")
            Log.d("ArchiveRepository", "  - Server: $serverHeader")
            Log.d("ArchiveRepository", "  - Content-Type: ${headResponse.headers()["Content-Type"]}")
            
            if (!acceptsRanges || totalBytes == 0L) {
                Log.w("ArchiveRepository", "Server doesn't support ranges (acceptsRanges=$acceptsRanges, totalBytes=$totalBytes)")
                Log.w("ArchiveRepository", "Falling back to single-part download")
                return@withContext downloadFileSinglePart(
                    fileUrl, destinationPath, 0, cookie, onProgress
                )
            }
            
            Log.d("ArchiveRepository", "Server supports ranges! Starting multi-part download with $parts parts...")
            
            return@withContext downloadFileMultiPart(
                fileUrl, file, totalBytes, parts, cookie, existingParts, onProgress, onPartProgress, onSavePartProgress
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
            } else {
                Log.d("ArchiveRepository", "File already exists with size: ${file.length()} bytes")
            }
            
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                val fileChannel = randomAccessFile.channel
                
                if (fileChannel.size() < totalBytes) {
                    Log.d("ArchiveRepository", "Allocating file space from ${fileChannel.size()} to $totalBytes bytes...")
                    try {
                        fileChannel.position(totalBytes - 1)
                        randomAccessFile.write(0)
                        fileChannel.force(true)
                        randomAccessFile.fd.sync()
                        Log.d("ArchiveRepository", "File space allocated by writing at end. File size: ${file.length()} bytes")
                    } catch (e: Exception) {
                        Log.w("ArchiveRepository", "Failed to allocate by writing, trying setLength: ${e.message}")
                        try {
                            randomAccessFile.setLength(totalBytes)
                            randomAccessFile.fd.sync()
                            Log.d("ArchiveRepository", "File space allocated via setLength. File size: ${file.length()} bytes")
                        } catch (e2: Exception) {
                            Log.e("ArchiveRepository", "Failed to allocate file space: ${e2.message}")
                        }
                    }
                } else {
                    Log.d("ArchiveRepository", "File already has correct size: ${fileChannel.size()} bytes")
                }
                
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
            Log.d("ArchiveRepository", "Existing bytes: $existingBytes")
            
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
            val uri = Uri.parse(destinationUri)
            
            Log.d("ArchiveRepository", "=== URI Download Started ===")
            Log.d("ArchiveRepository", "URI: $destinationUri")
            Log.d("ArchiveRepository", "Parts requested: $parts")
            
            if (parts <= 1) {
                Log.d("ArchiveRepository", "Using single-part download for URI (parts=$parts)")
                return@withContext downloadFileResumableUriSinglePart(
                    fileUrl, destinationUri, existingBytes, cookie, onProgress
                )
            }
            
            Log.d("ArchiveRepository", "Checking server support for multi-part download...")
            val headResponse = archiveApi.getFileInfo(fileUrl, cookie)
            val totalBytes = headResponse.headers()["Content-Length"]?.toLongOrNull() ?: 0L
            val acceptsRanges = headResponse.headers()["Accept-Ranges"]?.equals("bytes", ignoreCase = true) ?: false
            
            Log.d("ArchiveRepository", "Server Response:")
            Log.d("ArchiveRepository", "  - Content-Length: $totalBytes bytes")
            Log.d("ArchiveRepository", "  - Accept-Ranges: ${headResponse.headers()["Accept-Ranges"]}")
            Log.d("ArchiveRepository", "  - Server: ${headResponse.headers()["Server"]}")
            
            if (!acceptsRanges || totalBytes == 0L) {
                Log.w("ArchiveRepository", "Server doesn't support ranges for URI download, falling back to single-part")
                return@withContext downloadFileResumableUriSinglePart(
                    fileUrl, destinationUri, 0, cookie, onProgress
                )
            }
            
            Log.d("ArchiveRepository", "Server supports ranges! Using temp file for multi-part download...")
            
            val tempFile = File(context.cacheDir, "temp_download_${System.currentTimeMillis()}.tmp")
            try {
                val multiPartResult = downloadFileMultiPart(
                    fileUrl, tempFile, totalBytes, parts, cookie, existingParts, onProgress, onPartProgress, onSavePartProgress
                )
                
                if (multiPartResult.isSuccess) {
                    Log.d("ArchiveRepository", "Multi-part download completed, copying to URI...")
                    copyFileToUri(tempFile, uri)
                    Log.d("ArchiveRepository", "=== URI download completed successfully ===")
                    return@withContext Result.success(destinationUri)
                } else {
                    return@withContext multiPartResult.map { destinationUri }
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d("ArchiveRepository", "Temp file deleted")
                }
            }
            
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
}
