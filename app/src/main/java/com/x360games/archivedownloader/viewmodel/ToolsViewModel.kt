package com.x360games.archivedownloader.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.service.Iso2GodService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToolsUiState(
    val isConverting: Boolean = false,
    val conversionProgress: Float = 0f,
    val conversionStatus: String? = null,
    val errorMessage: String? = null
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()
    
    fun selectIsoFile() {
        // Esta função será chamada pela Activity/Fragment para abrir o seletor de arquivos
        // Por enquanto, vamos apenas mostrar que está em desenvolvimento
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Ferramenta em desenvolvimento - ISO to GOD converter"
            )
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
