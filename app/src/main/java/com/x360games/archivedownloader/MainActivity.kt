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

class MainActivity : AppCompatActivity() {
    
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_main)
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Check if setup is completed
        lifecycleScope.launch {
            val setupCompleted = mainViewModel.setupCompleted.first()
            if (setupCompleted) {
                navController.navigate(R.id.mainFragment)
            }
        }
        
        // Resume downloads
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL
        }
        startService(intent)
    }
}
