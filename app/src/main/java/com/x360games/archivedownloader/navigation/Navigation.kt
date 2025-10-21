package com.x360games.archivedownloader.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.x360games.archivedownloader.ui.screens.DownloadDetailsScreen
import com.x360games.archivedownloader.ui.screens.DownloadManagerScreen
import com.x360games.archivedownloader.ui.screens.MainScreen
import com.x360games.archivedownloader.ui.screens.SettingsScreen
import com.x360games.archivedownloader.ui.screens.SetupScreen
import com.x360games.archivedownloader.viewmodel.DownloadViewModel
import com.x360games.archivedownloader.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Home : Screen("home")
    object Main : Screen("main")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
    object DownloadDetails : Screen("download_details/{downloadId}") {
        fun createRoute(downloadId: Long) = "download_details/$downloadId"
    }
}

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home : BottomNavItem(Screen.Main.route, Icons.Default.Home, "Início")
    object Downloads : BottomNavItem(Screen.Downloads.route, Icons.Default.Download, "Downloads")
    object Settings : BottomNavItem(Screen.Settings.route, Icons.Default.Settings, "Configurações")
}

@Composable
fun NavigationGraph(navController: NavHostController) {
    val downloadViewModel: DownloadViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel()
    val setupCompleted by mainViewModel.setupCompleted.collectAsState(initial = false)
    
    NavHost(
        navController = navController,
        startDestination = if (setupCompleted) Screen.Home.route else Screen.Setup.route
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onCompleteSetup = { downloadPath ->
                    mainViewModel.updateDownloadPath(downloadPath)
                    mainViewModel.completeSetup()
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreenWithBottomNav(
                downloadViewModel = downloadViewModel,
                mainViewModel = mainViewModel,
                onNavigateToDetails = { downloadId ->
                    navController.navigate(Screen.DownloadDetails.createRoute(downloadId))
                }
            )
        }
        

        
        composable(
            route = Screen.DownloadDetails.route,
            arguments = listOf(
                navArgument("downloadId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: 0L
            
            DownloadDetailsScreen(
                downloadId = downloadId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                downloadViewModel = downloadViewModel
            )
        }
    }
}

@Composable
fun HomeScreenWithBottomNav(
    downloadViewModel: DownloadViewModel,
    mainViewModel: MainViewModel,
    onNavigateToDetails: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val downloads by downloadViewModel.allDownloads.collectAsState()
    val partsProgress by downloadViewModel.partsProgress.collectAsState()
    
    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Downloads,
        BottomNavItem.Settings
    )
    
    Scaffold(
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
                onNavigateToSettings = { selectedTab = 2 }
            )
            1 -> DownloadManagerScreen(
                downloads = downloads,
                partsProgress = partsProgress,
                onNavigateBack = { selectedTab = 0 },
                onNavigateToDetails = onNavigateToDetails,
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

