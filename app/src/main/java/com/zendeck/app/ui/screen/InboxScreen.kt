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
import com.zendeck.app.ui.components.LinkActionSheet
import com.zendeck.app.ui.components.LinkCard
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    inboxViewModel: InboxViewModel = viewModel()
) {
    val links by inboxViewModel.topFiveLinks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Accordion expansion: only one card open at a time
    var expandedLinkId by remember { mutableStateOf<String?>(null) }
    var actionSheetLink by remember { mutableStateOf<LinkItem?>(null) }
    var editingLink by remember { mutableStateOf<LinkItem?>(null) }
    val dismissedIds = remember { mutableStateListOf<String>() }

    // If the expanded link is archived/dismissed, collapse it
    LaunchedEffect(links) {
        if (expandedLinkId != null && links.none { it.id == expandedLinkId }) {
            expandedLinkId = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (links.isEmpty()) {
            InboxEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(links, key = { it.id }) { link ->
                    AnimatedVisibility(
                        visible = link.id !in dismissedIds,
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val isExpanded = expandedLinkId == link.id
                        LinkCard(
                            link = link,
                            isExpanded = isExpanded,
                            onTap = {
                                if (isExpanded) {
                                    inboxViewModel.openInCustomTab(context, link.url)
                                } else {
                                    // Collapse any other card, expand this one
                                    expandedLinkId = link.id
                                }
                            },
                            onLongPress = { actionSheetLink = link }
                        )
                    }
                }
            }
        }
    }

    // Long-press action sheet
    actionSheetLink?.let { link ->
        LinkActionSheet(
            link = link,
            isArchived = false,
            onDismiss = { actionSheetLink = null },
            onOpen = {
                inboxViewModel.openInCustomTab(context, link.url)
                actionSheetLink = null
            },
            onEditTags = {
                editingLink = link
                actionSheetLink = null
            },
            onArchive = {
                dismissedIds.add(link.id)
                inboxViewModel.archiveLink(link.id)
                if (expandedLinkId == link.id) expandedLinkId = null
                actionSheetLink = null
            }
        )
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
            text = "Share any link to ZenDeck to start saving.\nTap a card to expand — tap again to open.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
