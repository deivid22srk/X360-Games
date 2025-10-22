package com.x360games.archivedownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.x360games.archivedownloader.ui.screens.DownloadDetailsScreen
import com.x360games.archivedownloader.viewmodel.DownloadViewModel

class DownloadDetailsFragment : Fragment() {
    
    private val downloadViewModel: DownloadViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val downloadId = arguments?.getLong("downloadId") ?: 0L
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DownloadDetailsScreen(
                    downloadId = downloadId,
                    onNavigateBack = {
                        findNavController().navigateUp()
                    },
                    downloadViewModel = downloadViewModel
                )
            }
        }
    }
}
