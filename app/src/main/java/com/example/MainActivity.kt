package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as FlashTonnosApplication).container
        
        // Silent auto-sync on startup if GitHub credentials are configured
        lifecycleScope.launch {
            val owner = appContainer.appPreferences.getGitHubOwner()
            val repo = appContainer.appPreferences.getGitHubRepo()
            val token = appContainer.appPreferences.getGitHubToken()
            if (owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()) {
                appContainer.syncRepository.sync()
            }
        }
        
        setContent {
            val themeIndex by appContainer.appPreferences.selectedThemeFlow.collectAsState(initial = 0)
            
            MyApplicationTheme(themeIndex = themeIndex) {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
