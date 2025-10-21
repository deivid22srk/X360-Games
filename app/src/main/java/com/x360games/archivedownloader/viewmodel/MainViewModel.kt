package com.x360games.archivedownloader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.cache.CacheManager
import com.x360games.archivedownloader.data.ArchiveFile
import com.x360games.archivedownloader.data.ArchiveItem
import com.x360games.archivedownloader.data.DownloadItem
import com.x360games.archivedownloader.data.DownloadState
import com.x360games.archivedownloader.network.ArchiveRepository
import com.x360games.archivedownloader.service.DownloadService
import com.x360games.archivedownloader.utils.FileUtils
import com.x360games.archivedownloader.utils.NotificationHelper
import com.x360games.archivedownloader.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed class UiState {
    object Loading : UiState()
    data class Success(val items: List<ArchiveItem>) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArchiveRepository(application)
    private val preferencesManager = PreferencesManager(application)
    private val notificationHelper = NotificationHelper(application)
    private val cacheManager = CacheManager(application)
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filteredItems = MutableStateFlow<List<ArchiveItem>>(emptyList())
    val filteredItems: StateFlow<List<ArchiveItem>> = _filteredItems.asStateFlow()
    
    private val _expandedItems = MutableStateFlow<Set<String>>(emptySet())
    val expandedItems: StateFlow<Set<String>> = _expandedItems.asStateFlow()
    
    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()
    
    private var userCookies: String? = null
    private var allItems: List<ArchiveItem> = emptyList()
    
    val storedUsername = preferencesManager.username.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    
    val storedCookies = preferencesManager.cookies.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    
    val downloadPath = preferencesManager.downloadPath.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    
    val autoExtract = preferencesManager.autoExtract.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    
    val extractionPath = preferencesManager.extractionPath.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    
    val concurrentDownloads = preferencesManager.concurrentDownloads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        1
    )
    
    val setupCompleted = preferencesManager.setupCompleted.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    
    init {
        loadData()
        observeStoredCredentials()
    }
    
    private fun observeStoredCredentials() {
        viewModelScope.launch {
            storedUsername.collect { username ->
                _isLoggedIn.value = username != null
                _username.value = username
            }
        }
        
        viewModelScope.launch {
            storedCookies.collect { cookies ->
                userCookies = cookies
            }
        }
    }
    
    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                val cachedItems = cacheManager.getCachedItems()
                if (cachedItems != null && cachedItems.isNotEmpty()) {
                    allItems = cachedItems
                    filterItems(_searchQuery.value)
                    _uiState.value = UiState.Success(allItems)
                    Log.d("MainViewModel", "Loaded ${allItems.size} items from cache")
                    return@launch
                }
            }
            
            _uiState.value = UiState.Loading
            
            val collectionResult = repository.getX360Collection()
            if (collectionResult.isFailure) {
                _uiState.value = UiState.Error(collectionResult.exceptionOrNull()?.message ?: "Unknown error")
                return@launch
            }
            
            val collection = collectionResult.getOrNull()!!
            val itemsResult = repository.getAllArchiveItems(collection)
            
            if (itemsResult.isSuccess) {
                allItems = itemsResult.getOrNull()!!
                filterItems(_searchQuery.value)
                _uiState.value = UiState.Success(allItems)
                Log.d("MainViewModel", "Loaded ${allItems.size} items from API")
                
                cacheManager.cacheItems(allItems)
            } else {
                _uiState.value = UiState.Error(itemsResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        Log.d("MainViewModel", "Search query updated to: '$query', allItems size: ${allItems.size}")
        if (allItems.isNotEmpty()) {
            filterItems(query)
        } else {
            Log.w("MainViewModel", "Cannot filter - allItems is empty")
        }
    }
    
    private fun filterItems(query: String) {
        val filtered = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                val matchesTitle = item.title.contains(query, ignoreCase = true)
                val matchesId = item.id.contains(query, ignoreCase = true)
                val matchesFileName = item.files.any { file ->
                    file.name.contains(query, ignoreCase = true)
                }
                matchesTitle || matchesId || matchesFileName
            }
        }
        _filteredItems.value = filtered
        Log.d("MainViewModel", "Filtered items: ${filtered.size} out of ${allItems.size} (query: '$query')")
        if (filtered.isEmpty() && query.isNotBlank()) {
            Log.d("MainViewModel", "No items matched the search query")
        }
    }
    
    fun toggleItemExpansion(itemId: String) {
        _expandedItems.value = if (_expandedItems.value.contains(itemId)) {
            _expandedItems.value - itemId
        } else {
            _expandedItems.value + itemId
        }
    }
    
    fun downloadFile(item: ArchiveItem, file: ArchiveFile, customPath: String? = null) {
        val notificationId = "${item.id}_${file.name}".hashCode()
        val fileUrl = "https://archive.org/download/${extractIdentifier(item.url)}/${file.name}"
        
        val destinationPath = if (customPath != null && customPath.startsWith("content://")) {
            val treeUri = Uri.parse(customPath)
            val docFile = DocumentFile.fromTreeUri(getApplication(), treeUri)
            val newFile = docFile?.findFile(file.name) ?: docFile?.createFile("application/octet-stream", file.name)
            newFile?.uri?.toString() ?: run {
                val defaultDir = FileUtils.getDefaultDownloadDirectory(getApplication())
                File(defaultDir, file.name).absolutePath
            }
        } else {
            val defaultDir = FileUtils.getDefaultDownloadDirectory(getApplication())
            File(defaultDir, file.name).absolutePath
        }
        
        val totalBytes = FileUtils.parseFileSize(file.size)
        
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_FILE_NAME, file.name)
            putExtra(DownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(DownloadService.EXTRA_IDENTIFIER, item.id)
            putExtra(DownloadService.EXTRA_DESTINATION_PATH, destinationPath)
            putExtra(DownloadService.EXTRA_TOTAL_BYTES, totalBytes)
            putExtra(DownloadService.EXTRA_COOKIE, userCookies)
            putExtra(DownloadService.EXTRA_NOTIFICATION_ID, notificationId)
        }
        
        getApplication<Application>().startService(intent)
    }
    
    fun loginWithCookies(cookies: String) {
        viewModelScope.launch {
            val username = extractUsernameFromCookies(cookies)
            preferencesManager.saveCredentials(username, cookies)
            _isLoggedIn.value = true
            _username.value = username
            userCookies = cookies
        }
    }
    
    private fun extractUsernameFromCookies(cookies: String): String {
        val usernameCookie = cookies.split("; ")
            .find { it.startsWith("logged-in-user=") }
        return usernameCookie?.substringAfter("=") ?: "User"
    }
    
    fun logout() {
        viewModelScope.launch {
            preferencesManager.clearCredentials()
            _isLoggedIn.value = false
            _username.value = null
            userCookies = null
        }
    }
    
    fun updateDownloadPath(path: String) {
        viewModelScope.launch {
            preferencesManager.saveDownloadPath(path)
        }
    }
    
    fun setAutoExtract(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoExtract(enabled)
        }
    }
    
    fun updateExtractionPath(path: String) {
        viewModelScope.launch {
            preferencesManager.saveExtractionPath(path)
        }
    }
    
    fun setConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            preferencesManager.setConcurrentDownloads(count)
        }
    }
    
    fun completeSetup() {
        viewModelScope.launch {
            preferencesManager.setSetupCompleted(true)
        }
    }
    
    private fun extractIdentifier(url: String): String {
        return url.substringAfterLast("/")
    }
}
