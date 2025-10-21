package com.x360games.archivedownloader.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Iso2GodConverter(private val context: Context) {
    
    companion object {
        init {
            try {
                System.loadLibrary("iso2god")
                Log.d("Iso2GodConverter", "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("Iso2GodConverter", "Failed to load native library", e)
            }
        }
        
        private const val BLOCK_SIZE = 4096
        private const val BLOCKS_PER_PART = 41412
    }
    
    // Native methods (implementadas em C++)
    private external fun nativeConvertIso(
        isoPath: String,
        outputPath: String,
        progressCallback: ProgressCallback
    ): Int
    
    private external fun nativeGetIsoInfo(isoPath: String): IsoInfo?
    
    private external fun nativeCancelConversion()
    
    /**
     * Converte um arquivo ISO do Xbox 360 para o formato GOD (Games on Demand)
     * 
     * @param isoPath Caminho do arquivo ISO de origem
     * @param outputPath Caminho de saída para os arquivos GOD
     * @param onProgress Callback para atualizações de progresso (progress: 0.0-1.0, status: String)
     * @return Result<String> com o caminho do GOD gerado ou erro
     */
    suspend fun convertIsoToGod(
        isoPath: String,
        outputPath: String,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isoFile = File(isoPath)
            if (!isoFile.exists()) {
                return@withContext Result.failure(Exception("Arquivo ISO não encontrado: $isoPath"))
            }
            
            if (!isoFile.canRead()) {
                return@withContext Result.failure(Exception("Sem permissão de leitura para: $isoPath"))
            }
            
            val outputDir = File(outputPath)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            if (!outputDir.canWrite()) {
                return@withContext Result.failure(Exception("Sem permissão de escrita em: $outputPath"))
            }
            
            Log.d("Iso2GodConverter", "Starting conversion...")
            Log.d("Iso2GodConverter", "ISO: $isoPath (${isoFile.length() / 1024 / 1024} MB)")
            Log.d("Iso2GodConverter", "Output: $outputPath")
            
            onProgress(0f, "Analisando arquivo ISO...")
            
            // Obter informações do ISO
            val isoInfo = nativeGetIsoInfo(isoPath)
            if (isoInfo == null) {
                return@withContext Result.failure(Exception("Falha ao ler informações do ISO"))
            }
            
            Log.d("Iso2GodConverter", "ISO Info: ${isoInfo.gameName} (${isoInfo.titleId})")
            
            onProgress(0.05f, "Iniciando conversão...")
            
            val progressCallback = object : ProgressCallback {
                override fun onProgress(progress: Float, currentOperation: String) {
                    onProgress(progress, currentOperation)
                }
            }
            
            val result = nativeConvertIso(isoPath, outputPath, progressCallback)
            
            if (result == 0) {
                onProgress(1f, "Conversão concluída!")
                val godPath = File(outputPath, isoInfo.titleId).absolutePath
                Log.d("Iso2GodConverter", "Conversion successful: $godPath")
                Result.success(godPath)
            } else {
                val errorMessage = when (result) {
                    -1 -> "Erro ao abrir arquivo ISO"
                    -2 -> "Erro ao criar arquivos de saída"
                    -3 -> "Erro durante a conversão"
                    -4 -> "Conversão cancelada"
                    else -> "Erro desconhecido (código: $result)"
                }
                Result.failure(Exception(errorMessage))
            }
            
        } catch (e: Exception) {
            Log.e("Iso2GodConverter", "Conversion error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtém informações de um arquivo ISO
     */
    suspend fun getIsoInfo(isoPath: String): Result<IsoInfo> = withContext(Dispatchers.IO) {
        try {
            val info = nativeGetIsoInfo(isoPath)
            if (info != null) {
                Result.success(info)
            } else {
                Result.failure(Exception("Falha ao ler informações do ISO"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Cancela uma conversão em andamento
     */
    fun cancelConversion() {
        try {
            nativeCancelConversion()
            Log.d("Iso2GodConverter", "Conversion cancellation requested")
        } catch (e: Exception) {
            Log.e("Iso2GodConverter", "Error cancelling conversion", e)
        }
    }
    
    // Interface para callback de progresso
    interface ProgressCallback {
        fun onProgress(progress: Float, currentOperation: String)
    }
}

/**
 * Informações extraídas de um arquivo ISO do Xbox 360
 */
data class IsoInfo(
    val gameName: String,
    val titleId: String,
    val mediaId: String,
    val platform: String, // "Xbox 360" ou "Xbox"
    val sizeBytes: Long,
    val volumeDescriptor: String
)
