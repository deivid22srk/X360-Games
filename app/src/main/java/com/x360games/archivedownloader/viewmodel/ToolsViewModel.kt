package com.x360games.archivedownloader.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.service.Iso2GodService
import com.x360games.archivedownloader.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ToolsUiState(
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val conversionStatus: String? = null,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
    val isCopying: Boolean = false,
    val copyProgress: Float = 0f
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()
    
    fun onIsoFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    errorMessage = null
                )
                
                // Obter informações do arquivo
                val fileName = withContext(Dispatchers.IO) {
                    FileUtils.getFileNameFromUri(context, uri) ?: "unknown.iso"
                }
                
                Log.d("ToolsViewModel", "ISO file selected: $fileName")
                
                // Validar extensão
                if (!fileName.endsWith(".iso", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Por favor, selecione um arquivo ISO válido (.iso)"
                    )
                    return@launch
                }
                
                val fileSize = withContext(Dispatchers.IO) {
                    FileUtils.getFileSizeFromUri(context, uri)
                }
                
                Log.d("ToolsViewModel", "File size: ${FileUtils.formatFileSize(fileSize)}")
                
                // Validar tamanho (máximo 15GB)
                val maxSize = 15L * 1024L * 1024L * 1024L // 15GB
                if (fileSize > maxSize) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Arquivo muito grande (${FileUtils.formatFileSize(fileSize)}). Máximo: 15GB"
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isCopying = true,
                    copyProgress = 0f
                )
                
                // Copiar arquivo via ContentResolver para local acessível
                val tempIsoFile = withContext(Dispatchers.IO) {
                    copyIsoFileFromUri(context, uri, fileName, fileSize)
                }
                
                if (tempIsoFile == null) {
                    _uiState.value = _uiState.value.copy(
                        isCopying = false,
                        errorMessage = "Erro ao preparar arquivo para conversão"
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    isCopying = false
                )
                
                // Preparar diretório de saída
                val outputPath = FileUtils.getDefaultDownloadDirectory(context)
                val godOutputPath = File(outputPath, "GOD")
                godOutputPath.mkdirs()
                
                // Iniciar conversão
                startConversion(tempIsoFile.absolutePath, godOutputPath.absolutePath)
                
            } catch (e: Exception) {
                Log.e("ToolsViewModel", "Error selecting ISO file", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isCopying = false,
                    errorMessage = "Erro ao processar arquivo: ${e.message}"
                )
            }
        }
    }
    
    private fun copyIsoFileFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long
    ): File? {
        try {
            // Criar diretório temporário no armazenamento do app
            val tempDir = File(context.getExternalFilesDir(null), "iso_temp")
            tempDir.mkdirs()
            
            // Limpar arquivos antigos
            tempDir.listFiles()?.forEach { it.delete() }
            
            val tempFile = File(tempDir, fileName)
            
            Log.d("ToolsViewModel", "Copying ISO to: ${tempFile.absolutePath}")
            
            // Abrir InputStream via ContentResolver
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192) // 8KB buffer
                    var totalCopied = 0L
                    var read: Int
                    var lastProgressUpdate = 0f
                    
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalCopied += read
                        
                        // Atualizar progresso a cada 1%
                        val progress = totalCopied.toFloat() / fileSize.toFloat()
                        if (progress - lastProgressUpdate >= 0.01f) {
                            _uiState.value = _uiState.value.copy(
                                copyProgress = progress
                            )
                            lastProgressUpdate = progress
                        }
                    }
                    
                    outputStream.flush()
                }
            } ?: run {
                Log.e("ToolsViewModel", "Failed to open InputStream from URI")
                return null
            }
            
            Log.d("ToolsViewModel", "ISO copied successfully: ${tempFile.length()} bytes")
            return tempFile
            
        } catch (e: Exception) {
            Log.e("ToolsViewModel", "Error copying ISO file", e)
            return null
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
