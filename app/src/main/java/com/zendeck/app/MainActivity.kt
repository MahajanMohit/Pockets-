package com.zendeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.screen.ArchiveScreen
import com.zendeck.app.ui.screen.ChatScreen
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
    Chat("Chat AI", Icons.Default.Forum),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
private fun ZenDeckApp() {
    var selectedTab by remember { mutableStateOf(Tab.Inbox) }
    var previousOrdinal by remember { mutableIntStateOf(0) }
    val c = LocalZenDeckColors.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.background) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            previousOrdinal = selectedTab.ordinal
                            selectedTab = tab
                        },
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
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val slideDir = if (targetState.ordinal > previousOrdinal) 1 else -1
                (slideInHorizontally(
                    initialOffsetX = { w -> w * slideDir / 4 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { w -> -w * slideDir / 4 },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(200))
                    )
            },
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                Tab.Inbox -> InboxScreen(modifier = Modifier.padding(innerPadding))
                Tab.Archive -> ArchiveScreen(modifier = Modifier.padding(innerPadding))
                Tab.Chat -> ChatScreen(modifier = Modifier.padding(innerPadding))
                Tab.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
