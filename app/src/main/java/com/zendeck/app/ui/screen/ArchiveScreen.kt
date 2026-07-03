package com.zendeck.app.ui.screen

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
import com.zendeck.app.ui.components.SearchField
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun ArchiveScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel()
) {
    val links by viewModel.filteredArchivedLinks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.archiveSearch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = LocalZenDeckColors.current

    var expandedLinkId by remember { mutableStateOf<String?>(null) }
    var actionSheetLink by remember { mutableStateOf<LinkItem?>(null) }
    var editingLink by remember { mutableStateOf<LinkItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Archive",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )

        // ── Search bar ────────────────────────────────────────────────────────
        SearchField(
            value = searchQuery,
            onValueChange = { viewModel.setArchiveSearch(it) },
            placeholder = "Search archive…",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── Content ───────────────────────────────────────────────────────────
        if (links.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotEmpty())
                        "No archived links match \"$searchQuery\""
                    else
                        "Nothing archived yet.\nLong-press any inbox card to archive it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(links, key = { it.id }, contentType = { it.contentType }) { link ->
                    val isExpanded = expandedLinkId == link.id
                    LinkCard(
                        link = link,
                        isExpanded = isExpanded,
                        onTap = {
                            expandedLinkId = if (isExpanded) null else link.id
                        },
                        onDoubleTap = {
                            viewModel.openInCustomTab(context, link.url)
                        },
                        onLongPress = { actionSheetLink = link }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    actionSheetLink?.let { link ->
        LinkActionSheet(
            link = link,
            isArchived = true,
            onDismiss = { actionSheetLink = null },
            onOpen = {
                viewModel.openInCustomTab(context, link.url)
                actionSheetLink = null
            },
            onEditTags = {
                editingLink = link
                actionSheetLink = null
            },
            onRestore = {
                viewModel.restoreLink(link.id)
                if (expandedLinkId == link.id) expandedLinkId = null
                actionSheetLink = null
            },
            onDelete = {
                viewModel.deleteLink(link.id)
                if (expandedLinkId == link.id) expandedLinkId = null
                actionSheetLink = null
            }
        )
    }

    editingLink?.let { link ->
        TagEditDialog(
            link = link,
            onDismiss = { editingLink = null },
            onSave = { tags, isPinned ->
                viewModel.saveTagsAndPin(link.id, tags, isPinned)
                editingLink = null
            }
        )
    }
}
