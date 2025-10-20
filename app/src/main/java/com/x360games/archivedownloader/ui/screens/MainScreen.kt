package com.x360games.archivedownloader.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.x360games.archivedownloader.R
import com.x360games.archivedownloader.data.DownloadState
import com.x360games.archivedownloader.ui.components.ArchiveItemCard
import com.x360games.archivedownloader.ui.components.SearchBar
import com.x360games.archivedownloader.utils.FileUtils
import com.x360games.archivedownloader.viewmodel.MainViewModel
import com.x360games.archivedownloader.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()
    val expandedItems by viewModel.expandedItems.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val username by viewModel.username.collectAsState()
    val downloadPath by viewModel.downloadPath.collectAsState()
    
    var showLoginWebView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val storagePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        null
    } else {
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    
    LaunchedEffect(downloads) {
        downloads.values.forEach { download ->
            when (val state = download.state) {
                is DownloadState.Success -> {
                    snackbarHostState.showSnackbar("${download.fileName} downloaded successfully")
                }
                is DownloadState.Error -> {
                    snackbarHostState.showSnackbar("Download failed: ${state.message}")
                }
                else -> {}
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("X360 Games") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (isLoggedIn) {
                        Text(
                            text = username ?: "",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = stringResource(R.string.logout)
                            )
                        }
                    } else {
                        IconButton(onClick = { showLoginWebView = true }) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = stringResource(R.string.login)
                            )
                        }
                    }
                    
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )
            
            when (uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            ArchiveItemCard(
                                item = item,
                                isExpanded = expandedItems.contains(item.id),
                                onToggleExpanded = { viewModel.toggleItemExpansion(item.id) },
                                onDownloadFile = { file ->
                                    if (notificationPermissionState?.status?.isGranted == false) {
                                        notificationPermissionState.launchPermissionRequest()
                                    }
                                    
                                    if (storagePermissionState?.status?.isGranted != false) {
                                        viewModel.downloadFile(item, file, downloadPath)
                                    } else {
                                        storagePermissionState.launchPermissionRequest()
                                    }
                                }
                            )
                        }
                    }
                }
                
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = (uiState as UiState.Error).message,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            TextButton(onClick = { viewModel.loadData() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showLoginWebView) {
        LoginWebViewScreen(
            onLoginSuccess = { cookies ->
                viewModel.loginWithCookies(cookies)
                showLoginWebView = false
            },
            onDismiss = { showLoginWebView = false }
        )
    }
    
    if (showSettings) {
        SettingsScreen(
            onNavigateBack = { showSettings = false },
            viewModel = viewModel
        )
    }
}
