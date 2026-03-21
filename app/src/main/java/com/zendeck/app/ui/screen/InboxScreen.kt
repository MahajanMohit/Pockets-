package com.zendeck.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.components.SwipeableCard
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.InboxViewModel
import com.zendeck.app.ui.viewmodel.SettingsViewModel

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    inboxViewModel: InboxViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val links by inboxViewModel.topFiveLinks.collectAsStateWithLifecycle()
    val showSummary by settingsViewModel.showSummary.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingLink by remember { mutableStateOf<LinkItem?>(null) }
    val dismissedIds = remember { mutableStateListOf<String>() }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (links.isEmpty()) {
            InboxEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = links,
                    key = { it.id }
                ) { link ->
                    AnimatedVisibility(
                        visible = link.id !in dismissedIds,
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SwipeableCard(
                            link = link,
                            showSummary = showSummary,
                            onOpen = {
                                inboxViewModel.openInCustomTab(context, link.url)
                            },
                            onArchive = {
                                dismissedIds.add(link.id)
                                inboxViewModel.archiveLink(link.id)
                            },
                            onLongPress = {
                                editingLink = link
                            }
                        )
                    }
                }
            }
        }
    }

    // Tag edit dialog
    editingLink?.let { link ->
        TagEditDialog(
            link = link,
            onDismiss = { editingLink = null },
            onSave = { tags, isPinned ->
                inboxViewModel.saveTagsAndPin(link.id, tags, isPinned)
                editingLink = null
            }
        )
    }
}

@Composable
private fun InboxEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = UrgencyFresh
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Inbox Zero",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Share any link to ZenDeck to start reading.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
