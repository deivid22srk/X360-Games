package com.x360games.archivedownloader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.x360games.archivedownloader.ui.screens.DownloadDetailsScreen
import com.x360games.archivedownloader.ui.screens.DownloadManagerScreen
import com.x360games.archivedownloader.ui.screens.MainScreen
import com.x360games.archivedownloader.viewmodel.DownloadViewModel

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object DownloadManager : Screen("download_manager")
    object DownloadDetails : Screen("download_details/{downloadId}") {
        fun createRoute(downloadId: Long) = "download_details/$downloadId"
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    downloadViewModel: DownloadViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToDownloadManager = {
                    navController.navigate(Screen.DownloadManager.route)
                }
            )
        }
        
        composable(Screen.DownloadManager.route) {
            val downloads by downloadViewModel.allDownloads.collectAsState()
            
            DownloadManagerScreen(
                downloads = downloads,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetails = { downloadId ->
                    navController.navigate(Screen.DownloadDetails.createRoute(downloadId))
                },
                onClearFinished = {
                    downloadViewModel.clearFinishedDownloads()
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
            val download by downloadViewModel.getDownloadById(downloadId).collectAsState()
            val speedHistory by downloadViewModel.getSpeedHistoryForDownload(downloadId).collectAsState()
            
            DownloadDetailsScreen(
                download = download,
                speedHistory = speedHistory,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
