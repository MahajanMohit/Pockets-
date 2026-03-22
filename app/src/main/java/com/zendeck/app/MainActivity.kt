package com.zendeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.screen.ArchiveScreen
import com.zendeck.app.ui.screen.InboxScreen
import com.zendeck.app.ui.screen.SettingsScreen
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.theme.ZenDeckTheme
import com.zendeck.app.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val darkMode by settingsViewModel.darkMode.collectAsStateWithLifecycle()
            ZenDeckTheme(useDarkTheme = darkMode) {
                ZenDeckApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Default.Home),
    Archive("Archive", Icons.Default.Archive),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
private fun ZenDeckApp() {
    var selectedTab by remember { mutableStateOf(Tab.Inbox) }
    val c = LocalZenDeckColors.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.background) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentTeal,
                            selectedTextColor = AccentTeal,
                            unselectedIconColor = c.textSecondary,
                            unselectedTextColor = c.textSecondary,
                            indicatorColor = AccentTeal.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            Tab.Inbox -> InboxScreen(modifier = Modifier.padding(innerPadding))
            Tab.Archive -> ArchiveScreen(modifier = Modifier.padding(innerPadding))
            Tab.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
