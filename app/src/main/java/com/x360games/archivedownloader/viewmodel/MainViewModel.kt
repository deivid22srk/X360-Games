package com.x360games.archivedownloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.x360games.archivedownloader.data.ArchiveFile
import com.x360games.archivedownloader.data.ArchiveItem
import com.x360games.archivedownloader.data.DownloadItem
import com.x360games.archivedownloader.data.DownloadState
import com.x360games.archivedownloader.network.ArchiveRepository
import com.x360games.archivedownloader.utils.FileUtils
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
        _filteredItems.value = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                item.id.contains(query, ignoreCase = true) ||
                item.files.any { it.name.contains(query, ignoreCase = true) }
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
    
    fun downloadFile(item: ArchiveItem, file: ArchiveFile, destinationDir: File) {
        val downloadId = "${item.id}_${file.name}"
        
        _downloads.value = _downloads.value + (downloadId to DownloadItem(
            id = downloadId,
            fileName = file.name,
            url = "${item.url}/download/${file.name}",
            state = DownloadState.Downloading(0, file.name)
        ))
        
        viewModelScope.launch {
            val fileUrl = "https://archive.org/download/${extractIdentifier(item.url)}/${file.name}"
            
            val result = repository.downloadFile(
                fileUrl = fileUrl,
                fileName = file.name,
                destinationDir = destinationDir,
                cookie = userCookies,
                onProgress = { progress ->
                    _downloads.value = _downloads.value + (downloadId to DownloadItem(
                        id = downloadId,
                        fileName = file.name,
                        url = fileUrl,
                        state = DownloadState.Downloading(progress, file.name)
                    ))
                }
            )
            
            _downloads.value = _downloads.value + (downloadId to DownloadItem(
                id = downloadId,
                fileName = file.name,
                url = fileUrl,
                state = if (result.isSuccess) {
                    DownloadState.Success(file.name, result.getOrNull()!!.absolutePath)
                } else {
                    DownloadState.Error(result.exceptionOrNull()?.message ?: "Download failed")
                }
            ))
        }
    }
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            val fakeCookie = "logged-in-sig=fake; logged-in-user=$email"
            preferencesManager.saveCredentials(email, fakeCookie)
            _isLoggedIn.value = true
            _username.value = email
            userCookies = fakeCookie
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            preferencesManager.clearCredentials()
            _isLoggedIn.value = false
            _username.value = null
            userCookies = null
        }
    }
    
    private fun extractIdentifier(url: String): String {
        return url.substringAfterLast("/")
    }
}
