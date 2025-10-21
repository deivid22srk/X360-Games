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
    val isProcessing: Boolean = false
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()
    
    fun onIsoFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Mostrar que está processando
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    errorMessage = null
                )
                
                // Obter informações do arquivo em background
                val (fileName, filePath) = withContext(Dispatchers.IO) {
                    val name = FileUtils.getFileNameFromUri(context, uri) ?: "unknown.iso"
                    val path = FileUtils.getRealPathFromUri(context, uri)
                    Pair(name, path)
                }
                
                Log.d("ToolsViewModel", "ISO file selected: $fileName")
                Log.d("ToolsViewModel", "Real path: $filePath")
                
                // Validar extensão
                if (!fileName.endsWith(".iso", ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Por favor, selecione um arquivo ISO válido (.iso)"
                    )
                    return@launch
                }
                
                // Verificar se conseguiu obter o caminho real
                if (filePath == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Não foi possível acessar o arquivo. Por favor, copie o arquivo ISO para a pasta Downloads e tente novamente."
                    )
                    return@launch
                }
                
                // Verificar se o arquivo existe
                val isoFile = File(filePath)
                if (!isoFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Arquivo não encontrado: $filePath"
                    )
                    return@launch
                }
                
                if (!isoFile.canRead()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Sem permissão para ler o arquivo. Verifique as permissões de armazenamento."
                    )
                    return@launch
                }
                
                val fileSize = FileUtils.getFileSizeFromUri(context, uri)
                Log.d("ToolsViewModel", "File size: ${FileUtils.formatFileSize(fileSize)}")
                
                // Preparar diretório de saída
                val outputPath = FileUtils.getDefaultDownloadDirectory(context)
                val godOutputPath = File(outputPath, "GOD")
                godOutputPath.mkdirs()
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false
                )
                
                // Iniciar conversão
                startConversion(filePath, godOutputPath.absolutePath)
                
            } catch (e: Exception) {
                Log.e("ToolsViewModel", "Error selecting ISO file", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Erro ao processar arquivo: ${e.message}"
                )
            }
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
