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
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

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
    
    private fun extractIdentifier(url: String): String {
        return url.substringAfterLast("/")
    }
}
