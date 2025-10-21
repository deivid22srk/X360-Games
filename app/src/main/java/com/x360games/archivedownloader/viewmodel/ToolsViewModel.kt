package com.x360games.archivedownloader.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.service.Iso2GodService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class ToolsUiState(
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val conversionStatus: String? = null,
    val errorMessage: String? = null
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()
    
    fun onIsoFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri) ?: "unknown.iso"
                Log.d("ToolsViewModel", "ISO file selected: $fileName")
                
                if (!fileName.endsWith(".iso", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Por favor, selecione um arquivo ISO válido"
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    conversionStatus = "Copiando arquivo ISO...",
                    errorMessage = null
                )
                
                val tempIsoFile = copyUriToTempFile(context, uri, fileName)
                if (tempIsoFile == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Erro ao copiar arquivo ISO"
                    )
                    return@launch
                }
                
                val outputPath = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "X360Games/GOD"
                )
                outputPath.mkdirs()
                
                startConversion(tempIsoFile.absolutePath, outputPath.absolutePath)
                
            } catch (e: Exception) {
                Log.e("ToolsViewModel", "Error selecting ISO file", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Erro ao processar arquivo: ${e.message}"
                )
            }
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }
    
    private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val tempDir = File(context.cacheDir, "iso_temp")
            tempDir.mkdirs()
            
            val tempFile = File(tempDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.d("ToolsViewModel", "ISO copied to: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e("ToolsViewModel", "Error copying URI to temp file", e)
            null
        }
    }
    
    fun startConversion(isoPath: String, outputPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConverting = true,
                conversionProgress = 0f,
                conversionStatus = "Iniciando conversão...",
                errorMessage = null
            )
            
            try {
                val intent = Intent(getApplication(), Iso2GodService::class.java).apply {
                    action = Iso2GodService.ACTION_CONVERT_ISO
                    putExtra(Iso2GodService.EXTRA_ISO_PATH, isoPath)
                    putExtra(Iso2GodService.EXTRA_OUTPUT_PATH, outputPath)
                }
                getApplication<Application>().startService(intent)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    errorMessage = "Erro ao iniciar conversão: ${e.message}"
                )
            }
        }
    }
    
    fun updateProgress(progress: Float, status: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                conversionProgress = progress,
                conversionStatus = status
            )
        }
    }
    
    fun conversionCompleted(success: Boolean, message: String? = null) {
        viewModelScope.launch {
            _uiState.value = if (success) {
                _uiState.value.copy(
                    isConverting = false,
                    conversionProgress = 1f,
                    conversionStatus = "Conversão concluída com sucesso!",
                    errorMessage = null
                )
            } else {
                _uiState.value.copy(
                    isConverting = false,
                    errorMessage = message ?: "Erro desconhecido na conversão"
                )
            }
        }
    }
    
    fun clearError() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }
}
