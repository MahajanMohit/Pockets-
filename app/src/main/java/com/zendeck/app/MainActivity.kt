package com.zendeck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.screen.ArchiveScreen
import com.zendeck.app.ui.screen.InboxScreen
import com.zendeck.app.ui.screen.SettingsScreen
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.GlassBackground
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.theme.ZenDeckTheme
import com.zendeck.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        /** Extra key used by ZenDeckWidget to request opening a specific link. */
        const val EXTRA_OPEN_LINK_ID = "open_link_id"
    }

    // Survives config changes; updated by onNewIntent for FLAG_ACTIVITY_SINGLE_TOP re-delivers
    private val _pendingLinkId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Capture link ID from widget tap
        intent.getStringExtra(EXTRA_OPEN_LINK_ID)?.let { _pendingLinkId.value = it }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val darkMode by settingsViewModel.darkMode.collectAsStateWithLifecycle()
            val fontScale by settingsViewModel.fontScale.collectAsStateWithLifecycle()
            val pendingLinkId by _pendingLinkId.asStateFlow().collectAsStateWithLifecycle()
            ZenDeckTheme(useDarkTheme = darkMode, fontScale = fontScale) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackground(darkTheme = darkMode)
                    ZenDeckApp(
                        pendingLinkId = pendingLinkId,
                        onLinkIdConsumed = { _pendingLinkId.value = null }
                    )
                }
            }
        }
    }

    /** Called when the activity is already running and a new widget tap arrives. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_LINK_ID)?.let { _pendingLinkId.value = it }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Default.Home),
    Archive("Archive", Icons.Default.Archive),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
private fun ZenDeckApp(
    pendingLinkId: String? = null,
    onLinkIdConsumed: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(Tab.Inbox) }
    var previousOrdinal by remember { mutableIntStateOf(0) }
    val c = LocalZenDeckColors.current

    // When a widget tap arrives, switch to the Inbox tab to show the link
    LaunchedEffect(pendingLinkId) {
        if (pendingLinkId != null && selectedTab != Tab.Inbox) {
            previousOrdinal = selectedTab.ordinal
            selectedTab = Tab.Inbox
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                HorizontalDivider(color = c.divider, thickness = 1.dp)
                NavigationBar(containerColor = c.navBackground, tonalElevation = 0.dp) {
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
                                unselectedIconColor = c.textDisabled,
                                unselectedTextColor = c.textDisabled,
                                indicatorColor = AccentTeal.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // Spring-driven, Apple-style: short travel, no fixed duration,
                // fully interruptible — rapid tab taps retain their velocity.
                val slideDir = if (targetState.ordinal > previousOrdinal) 1 else -1
                (slideInHorizontally(
                    initialOffsetX = { w -> w * slideDir / 8 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 380f
                    )
                ) + fadeIn(animationSpec = spring(stiffness = 600f)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { w -> -w * slideDir / 8 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 380f
                            )
                        ) + fadeOut(animationSpec = spring(stiffness = 900f))
                    )
            },
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                Tab.Inbox -> InboxScreen(
                    modifier = Modifier.padding(innerPadding),
                    initialExpandLinkId = pendingLinkId,
                    onLinkExpanded = onLinkIdConsumed
                )
                Tab.Archive -> ArchiveScreen(modifier = Modifier.padding(innerPadding))
                Tab.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
