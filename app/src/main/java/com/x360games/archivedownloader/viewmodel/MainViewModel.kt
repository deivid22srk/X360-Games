package com.x360games.archivedownloader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.data.ArchiveFile
import com.x360games.archivedownloader.data.ArchiveItem
import com.x360games.archivedownloader.data.DownloadItem
import com.x360games.archivedownloader.data.DownloadState
import com.x360games.archivedownloader.network.ArchiveRepository
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
    
    fun loadData() {
        viewModelScope.launch {
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
                _filteredItems.value = allItems
                _uiState.value = UiState.Success(allItems)
            } else {
                _uiState.value = UiState.Error(itemsResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterItems(query)
    }
    
    private fun filterItems(query: String) {
        viewModelScope.launch {
            _filteredItems.value = if (query.isBlank()) {
                allItems
            } else {
                allItems.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                    item.id.contains(query, ignoreCase = true)
                }
            }
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
        val downloadId = "${item.id}_${file.name}"
        val notificationId = downloadId.hashCode()
        
        _downloads.value = _downloads.value + (downloadId to DownloadItem(
            id = downloadId,
            fileName = file.name,
            url = "${item.url}/download/${file.name}",
            state = DownloadState.Downloading(0, file.name)
        ))
        
        notificationHelper.showDownloadProgress(notificationId, file.name, 0)
        
        viewModelScope.launch {
            val fileUrl = "https://archive.org/download/${extractIdentifier(item.url)}/${file.name}"
            
            val result = if (customPath != null && customPath.startsWith("content://")) {
                repository.downloadFileToUri(
                    fileUrl = fileUrl,
                    fileName = file.name,
                    destinationUri = Uri.parse(customPath),
                    cookie = userCookies,
                    onProgress = { progress ->
                        _downloads.value = _downloads.value + (downloadId to DownloadItem(
                            id = downloadId,
                            fileName = file.name,
                            url = fileUrl,
                            state = DownloadState.Downloading(progress, file.name)
                        ))
                        notificationHelper.showDownloadProgress(notificationId, file.name, progress)
                    }
                ).map { it }
            } else {
                val defaultDir = FileUtils.getDefaultDownloadDirectory(getApplication())
                repository.downloadFile(
                    fileUrl = fileUrl,
                    fileName = file.name,
                    destinationDir = defaultDir,
                    cookie = userCookies,
                    onProgress = { progress ->
                        _downloads.value = _downloads.value + (downloadId to DownloadItem(
                            id = downloadId,
                            fileName = file.name,
                            url = fileUrl,
                            state = DownloadState.Downloading(progress, file.name)
                        ))
                        notificationHelper.showDownloadProgress(notificationId, file.name, progress)
                    }
                ).map { it.absolutePath }
            }
            
            _downloads.value = _downloads.value + (downloadId to DownloadItem(
                id = downloadId,
                fileName = file.name,
                url = fileUrl,
                state = if (result.isSuccess) {
                    notificationHelper.showDownloadComplete(notificationId, file.name)
                    DownloadState.Success(file.name, result.getOrNull()!!)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Download failed"
                    notificationHelper.showDownloadError(notificationId, file.name, errorMsg)
                    DownloadState.Error(errorMsg)
                }
            ))
        }
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
    
    private fun extractIdentifier(url: String): String {
        return url.substringAfterLast("/")
    }
}
