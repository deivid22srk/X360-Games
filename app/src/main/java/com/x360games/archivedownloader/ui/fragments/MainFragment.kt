package com.x360games.archivedownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.x360games.archivedownloader.R
import com.x360games.archivedownloader.ui.screens.MainScreen
import com.x360games.archivedownloader.ui.screens.DownloadManagerScreen
import com.x360games.archivedownloader.ui.screens.SettingsScreen
import com.x360games.archivedownloader.viewmodel.MainViewModel
import com.x360games.archivedownloader.viewmodel.DownloadViewModel
import com.x360games.archivedownloader.ui.theme.X360GamesTheme

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Início")
    object Downloads : BottomNavItem("downloads", Icons.Default.Download, "Downloads")
    object Settings : BottomNavItem("settings", Icons.Default.Settings, "Configurações")
}

class MainFragment : Fragment() {
    
    private val mainViewModel: MainViewModel by activityViewModels()
    private val downloadViewModel: DownloadViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                X360GamesTheme {
                    var selectedTab by remember { mutableIntStateOf(0) }
                val downloads by downloadViewModel.allDownloads.collectAsState()
                val partsProgress by downloadViewModel.partsProgress.collectAsState()
                
                val bottomNavItems = listOf(
                    BottomNavItem.Home,
                    BottomNavItem.Downloads,
                    BottomNavItem.Settings
                )
                
                // Main scaffold with bottom navigation
                // contentWindowInsets = 0 allows inner scaffolds to handle their own insets
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar {
                            bottomNavItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    when (selectedTab) {
                        0 -> MainScreen(
                            viewModel = mainViewModel,
                            onNavigateToDownloadManager = { selectedTab = 1 },
                            onNavigateToSettings = { selectedTab = 2 },
                            modifier = Modifier.padding(paddingValues)
                        )
                        1 -> DownloadManagerScreen(
                            downloads = downloads,
                            partsProgress = partsProgress,
                            onNavigateBack = { selectedTab = 0 },
                            onNavigateToDetails = { downloadId ->
                                findNavController().navigate(
                                    R.id.action_mainFragment_to_downloadDetailsFragment,
                                    Bundle().apply {
                                        putLong("downloadId", downloadId)
                                    }
                                )
                            },
                            onClearFinished = { downloadViewModel.clearFinishedDownloads() },
                            onDeleteDownloads = { downloadIds ->
                                downloadViewModel.deleteDownloads(downloadIds)
                            },
                            modifier = Modifier.padding(paddingValues)
                        )
                        2 -> SettingsScreen(
                            onNavigateBack = { selectedTab = 0 },
                            viewModel = mainViewModel,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
                }
            }
        }
    }
}
