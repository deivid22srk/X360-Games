package com.x360games.archivedownloader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.x360games.archivedownloader.service.DownloadService
import com.x360games.archivedownloader.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display (content goes behind system bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Check if user has completed initial setup
        // This determines which screen to show first
        val setupCompleted = runBlocking { mainViewModel.setupCompleted.first() }
        
        setContentView(R.layout.activity_main)
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Dynamically set start destination based on setup status
        // This prevents showing setup screen every time the app opens
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            if (setupCompleted) R.id.mainFragment else R.id.setupFragment
        )
        navController.graph = navGraph
        
        // Resume downloads
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL
        }
        startService(intent)
    }
}
