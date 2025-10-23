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
import com.x360games.archivedownloader.R
import com.x360games.archivedownloader.ui.screens.SetupScreen
import com.x360games.archivedownloader.viewmodel.MainViewModel
import com.x360games.archivedownloader.ui.theme.X360GamesTheme

class SetupFragment : Fragment() {
    
    private val mainViewModel: MainViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                X360GamesTheme {
                    SetupScreen(
                    onSetupComplete = {
                        findNavController().navigate(R.id.action_setupFragment_to_mainFragment)
                    },
                    onCompleteSetup = { downloadPath ->
                        mainViewModel.updateDownloadPath(downloadPath)
                        mainViewModel.completeSetup()
                    }
                )
                }
            }
        }
    }
}
