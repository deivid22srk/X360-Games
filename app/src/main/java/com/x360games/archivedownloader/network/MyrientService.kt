package com.x360games.archivedownloader.network

import android.content.Context
import android.util.Log
import com.x360games.archivedownloader.data.ArchiveFile
import com.x360games.archivedownloader.data.ArchiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class MyrientService(private val context: Context) {
    
    companion object {
        private const val BASE_URL = "https://myrient.erista.me/files/Redump/Microsoft%20-%20Xbox%20360/"
        private const val TAG = "MyrientService"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    suspend fun getX360Games(): Result<List<ArchiveItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching games from Myrient...")
            
            val request = Request.Builder()
                .url(BASE_URL)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch Myrient page: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)
            
            val items = mutableListOf<ArchiveItem>()
            
            // Parse the HTML table rows
            val rows = doc.select("tr")
            
            for (row in rows) {
                try {
                    val columns = row.select("td")
                    if (columns.size < 3) continue
                    
                    val linkElement = columns[0].selectFirst("a[href]")
                    if (linkElement == null) continue
                    
                    val fileName = linkElement.text().trim()
                    if (fileName.isEmpty() || fileName == "../") continue
                    
                    // Skip non-game files (only get .iso and .rar files)
                    if (!fileName.endsWith(".iso", ignoreCase = true) && 
                        !fileName.endsWith(".rar", ignoreCase = true)) {
                        continue
                    }
                    
                    val sizeText = columns[1].text().trim()
                    val dateText = columns[2].text().trim()
                    
                    val fileUrl = BASE_URL + fileName
                    
                    // Extract game title from filename (remove extension and region tags)
                    val title = fileName
                        .substringBeforeLast(".")
                        .replace(Regex("\\(.*?\\)"), "")
                        .replace(Regex("\\[.*?\\]"), "")
                        .trim()
                    
                    val archiveFile = ArchiveFile(
                        name = fileName,
                        format = fileName.substringAfterLast("."),
                        size = sizeText,
                        md5 = null,
                        modifiedTime = dateText
                    )
                    
                    val item = ArchiveItem(
                        id = fileName.substringBeforeLast("."),
                        title = title,
                        url = fileUrl,
                        files = listOf(archiveFile)
                    )
                    
                    items.add(item)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing row: ${e.message}")
                }
            }
            
            Log.d(TAG, "Found ${items.size} games on Myrient")
            Result.success(items)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Myrient data", e)
            Result.failure(e)
        }
    }
}
