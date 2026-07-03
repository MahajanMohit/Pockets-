package com.zendeck.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.zendeck.app.ui.components.SearchField
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    inboxViewModel: InboxViewModel = viewModel(),
    initialExpandLinkId: String? = null,
    onLinkExpanded: () -> Unit = {}
) {
    val links by inboxViewModel.filteredInboxLinks.collectAsStateWithLifecycle()
    val searchQuery by inboxViewModel.inboxSearch.collectAsStateWithLifecycle()
    val activeLinkCount by inboxViewModel.activeLinkCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = LocalZenDeckColors.current

    var expandedLinkId by remember { mutableStateOf<String?>(null) }
    var actionSheetLink by remember { mutableStateOf<LinkItem?>(null) }
    var editingLink by remember { mutableStateOf<LinkItem?>(null) }
    val listState = rememberLazyListState()

    // Auto-expand and scroll to a link opened from the widget
    LaunchedEffect(initialExpandLinkId, links) {
        if (initialExpandLinkId != null && links.isNotEmpty()) {
            expandedLinkId = initialExpandLinkId
            val idx = links.indexOfFirst { it.id == initialExpandLinkId }
            if (idx >= 0) listState.animateScrollToItem(idx)
            onLinkExpanded()
        }
    }

    LaunchedEffect(links) {
        if (expandedLinkId != null && links.none { it.id == expandedLinkId }) {
            expandedLinkId = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (activeLinkCount > 0) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = AccentTeal.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$activeLinkCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Search bar ────────────────────────────────────────────────────────
        SearchField(
            value = searchQuery,
            onValueChange = { inboxViewModel.setInboxSearch(it) },
            placeholder = "Search links…",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── Content ───────────────────────────────────────────────────────────
        if (links.isEmpty()) {
            if (searchQuery.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No links match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                InboxEmptyState()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(links, key = { it.id }, contentType = { it.contentType }) { link ->
                    val isExpanded = expandedLinkId == link.id
                    LinkCard(
                        link = link,
                        modifier = Modifier.animateItem(),
                        isExpanded = isExpanded,
                        onTap = {
                            expandedLinkId = if (isExpanded) null else link.id
                        },
                        onDoubleTap = {
                            inboxViewModel.openInCustomTab(context, link.url)
                        },
                        onLongPress = { actionSheetLink = link }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
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
                inboxViewModel.archiveLink(link.id)
                if (expandedLinkId == link.id) expandedLinkId = null
                actionSheetLink = null
            },
            onResummarize = {
                inboxViewModel.resummarizeLink(link)
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
    val c = LocalZenDeckColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Inbox Zero",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Share any link, screenshot or text from any app.\nLong-press a card to archive or edit.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
