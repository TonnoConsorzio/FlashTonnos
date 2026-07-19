package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.di.AppContainer
import com.example.ui.screens.sync.SyncScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.study.StudyScreen
import com.example.ui.screens.deepdive.DeepDiveScreen

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Study : Screen("study", "Studia", { Icon(Icons.Default.Style, contentDescription = "Studia") })
    object Sync : Screen("sync", "Sincronizza", { Icon(Icons.Default.CloudSync, contentDescription = "Sincronizza") })
    object Stats : Screen("stats", "Statistiche", { Icon(Icons.Default.BarChart, contentDescription = "Statistiche") })
    object Settings : Screen("settings", "Impostazioni", { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") })
}

val items = listOf(
    Screen.Study,
    Screen.Sync,
    Screen.Stats,
    Screen.Settings
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Study.route,
        modifier = modifier
    ) {
        composable(Screen.Study.route) { StudyScreen(appContainer, navController = navController) }
        composable(Screen.Sync.route) { SyncScreen(appContainer, navController = navController) }
        composable(Screen.Stats.route) { StatsScreen(appContainer, navController = navController) }
        composable(Screen.Settings.route) { SettingsScreen(appContainer, navController = navController) }
        composable("deep_dive") { DeepDiveScreen(appContainer, navController = navController) }
    }
}
