package com.x360games.archivedownloader.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class RarExtractor(private val context: Context) {
    
    suspend fun extractRarFromUri(
        rarUri: Uri,
        destinationPath: String,
        onProgress: (extractedFiles: Int, totalFiles: Int, currentFile: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("RarExtractor", "=== Extracting from URI ===")
            Log.d("RarExtractor", "URI: $rarUri")
            
            val fileName = FileUtils.getFileNameFromUri(context, rarUri) ?: "archive.rar"
            
            onProgress(0, 100, "Preparando arquivo RAR...")
            
            val tempDir = File(context.cacheDir, "rar_temp")
            tempDir.mkdirs()
            tempDir.listFiles()?.forEach { it.delete() }
            
            val tempRarFile = File(tempDir, fileName)
            
            Log.d("RarExtractor", "Copying RAR to temp: ${tempRarFile.absolutePath}")
            
            context.contentResolver.openInputStream(rarUri)?.use { inputStream ->
                FileOutputStream(tempRarFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open RAR file"))
            
            Log.d("RarExtractor", "RAR copied successfully: ${tempRarFile.length()} bytes")
            
            onProgress(0, 100, "Extraindo arquivos...")
            
            val result = extractRarFile(
                rarFilePath = tempRarFile.absolutePath,
                destinationPath = destinationPath,
                onProgress = onProgress
            )
            
            tempRarFile.delete()
            Log.d("RarExtractor", "Temp RAR file cleaned up")
            
            result
            
        } catch (e: Exception) {
            Log.e("RarExtractor", "Error extracting from URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun extractRarFile(
        rarFilePath: String,
        destinationPath: String,
        onProgress: (extractedFiles: Int, totalFiles: Int, currentFile: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("RarExtractor", "=== Starting RAR extraction ===")
            Log.d("RarExtractor", "Source: $rarFilePath")
            Log.d("RarExtractor", "Destination: $destinationPath")
            
            if (destinationPath.startsWith("content://")) {
                Log.d("RarExtractor", "Using URI extraction method")
                return@withContext extractRarFileToUri(rarFilePath, destinationPath, onProgress)
            }
            
            val rarFile = File(rarFilePath)
            if (!rarFile.exists()) {
                Log.e("RarExtractor", "RAR file not found: $rarFilePath")
                return@withContext Result.failure(Exception("RAR file not found"))
            }
            
            Log.d("RarExtractor", "RAR file size: ${rarFile.length() / 1024 / 1024} MB")
            
            val destinationDir = File(destinationPath)
            if (!destinationDir.exists()) {
                val created = destinationDir.mkdirs()
                Log.d("RarExtractor", "Created destination directory: $created")
            }
            
            if (!destinationDir.canWrite()) {
                Log.e("RarExtractor", "Destination directory is not writable")
                return@withContext Result.failure(Exception("Destination directory is not writable"))
            }
            
            Log.d("RarExtractor", "Opening RAR archive...")
            val archive = Archive(rarFile)
            val headers = archive.fileHeaders
            val totalFiles = headers.size
            var extractedFiles = 0
            
            Log.d("RarExtractor", "Total files in archive: $totalFiles")
            
            for (header: FileHeader in headers) {
                if (header.isDirectory) {
                    val dir = File(destinationDir, header.fileName)
                    dir.mkdirs()
                    Log.d("RarExtractor", "Created directory: ${header.fileName}")
                } else {
                    val outputFile = File(destinationDir, header.fileName)
                    outputFile.parentFile?.mkdirs()
                    
                    Log.d("RarExtractor", "Extracting: ${header.fileName} (${header.fullUnpackSize / 1024} KB)")
                    
                    FileOutputStream(outputFile).use { output ->
                        archive.extractFile(header, output)
                    }
                    
                    extractedFiles++
                    onProgress(extractedFiles, totalFiles, header.fileName)
                    
                    if (extractedFiles % 10 == 0) {
                        Log.d("RarExtractor", "Progress: $extractedFiles/$totalFiles files extracted")
                    }
                }
            }
            
            archive.close()
            
            Log.d("RarExtractor", "=== Extraction completed successfully ===")
            Log.d("RarExtractor", "Extracted $extractedFiles files to $destinationPath")
            
            Result.success(destinationPath)
        } catch (e: RarException) {
            Log.e("RarExtractor", "RAR extraction error", e)
            Result.failure(Exception("Failed to extract RAR: ${e.message}"))
        } catch (e: Exception) {
            Log.e("RarExtractor", "Extraction error", e)
            Result.failure(e)
        }
    }
    
    private suspend fun extractRarFileToUri(
        rarFilePath: String,
        destinationUri: String,
        onProgress: (extractedFiles: Int, totalFiles: Int, currentFile: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rarFile = File(rarFilePath)
            if (!rarFile.exists()) {
                return@withContext Result.failure(Exception("RAR file not found"))
            }
            
            val uri = Uri.parse(destinationUri)
            val destinationDir = DocumentFile.fromTreeUri(context, uri)
                ?: return@withContext Result.failure(Exception("Invalid destination folder"))
            
            val archive = Archive(rarFile)
            val headers = archive.fileHeaders
            val totalFiles = headers.size
            var extractedFiles = 0
            
            for (header: FileHeader in headers) {
                if (header.isDirectory) {
                    val pathParts = header.fileName.split("/", "\\")
                    var currentDir = destinationDir
                    
                    for (part in pathParts) {
                        if (part.isNotEmpty()) {
                            val existingDir = currentDir.findFile(part)
                            currentDir = existingDir ?: currentDir.createDirectory(part)
                                ?: return@withContext Result.failure(Exception("Could not create directory"))
                        }
                    }
                } else {
                    val pathParts = header.fileName.split("/", "\\")
                    var currentDir = destinationDir
                    
                    for (i in 0 until pathParts.size - 1) {
                        val part = pathParts[i]
                        if (part.isNotEmpty()) {
                            val existingDir = currentDir.findFile(part)
                            currentDir = existingDir ?: currentDir.createDirectory(part)
                                ?: return@withContext Result.failure(Exception("Could not create directory"))
                        }
                    }
                    
                    val fileName = pathParts.last()
                    val outputFile = currentDir.createFile("application/octet-stream", fileName)
                        ?: return@withContext Result.failure(Exception("Could not create file: $fileName"))
                    
                    context.contentResolver.openOutputStream(outputFile.uri)?.use { output ->
                        val tempFile = File.createTempFile("extract_", ".tmp", context.cacheDir)
                        FileOutputStream(tempFile).use { tempOutput ->
                            archive.extractFile(header, tempOutput)
                        }
                        
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                        
                        tempFile.delete()
                    } ?: return@withContext Result.failure(Exception("Could not open output stream"))
                    
                    extractedFiles++
                    onProgress(extractedFiles, totalFiles, header.fileName)
                }
            }
            
            archive.close()
            
            Result.success(destinationUri)
        } catch (e: RarException) {
            Log.e("RarExtractor", "RAR extraction error to URI", e)
            Result.failure(Exception("Failed to extract RAR: ${e.message}"))
        } catch (e: Exception) {
            Log.e("RarExtractor", "Extraction error to URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun isValidRarFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            
            val archive = Archive(file)
            val isValid = archive.mainHeader != null
            archive.close()
            isValid
        } catch (e: Exception) {
            Log.e("RarExtractor", "Invalid RAR file", e)
            false
        }
    }
}
