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
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun ArchiveScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel()
) {
    val links by viewModel.archivedLinks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var expandedLinkId by remember { mutableStateOf<String?>(null) }
    var actionSheetLink by remember { mutableStateOf<LinkItem?>(null) }
    var editingLink by remember { mutableStateOf<LinkItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Archive",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (links.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing archived yet.\nLong-press any inbox card to archive it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(links, key = { it.id }) { link ->
                    val isExpanded = expandedLinkId == link.id
                    LinkCard(
                        link = link,
                        isExpanded = isExpanded,
                        onTap = {
                            if (isExpanded) viewModel.openInCustomTab(context, link.url)
                            else expandedLinkId = link.id
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
