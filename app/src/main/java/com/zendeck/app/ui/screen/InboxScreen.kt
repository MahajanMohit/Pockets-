package com.zendeck.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.components.LinkActionSheet
import com.zendeck.app.ui.components.LinkCard
import com.zendeck.app.ui.components.SwipeableCard
import com.zendeck.app.ui.components.TagEditDialog
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.theme.UrgencyFresh
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    inboxViewModel: InboxViewModel = viewModel()
) {
    val links by inboxViewModel.filteredInboxLinks.collectAsStateWithLifecycle()
    val searchQuery by inboxViewModel.inboxSearch.collectAsStateWithLifecycle()
    val modelAvailable by inboxViewModel.modelAvailable.collectAsStateWithLifecycle()
    val activeLinkCount by inboxViewModel.activeLinkCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = LocalZenDeckColors.current

    // Check model presence when screen first appears
    LaunchedEffect(Unit) { inboxViewModel.checkModelAvailability() }

    var expandedLinkId by remember { mutableStateOf<String?>(null) }
    var actionSheetLink by remember { mutableStateOf<LinkItem?>(null) }
    var editingLink by remember { mutableStateOf<LinkItem?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    // Track IDs being dismissed so we can animate them out before DB removes them
    val dismissedIds = remember { mutableStateListOf<String>() }

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
                    shape = MaterialTheme.shapes.small,
                    color = AccentTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$activeLinkCount links",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Search bar ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { inboxViewModel.setInboxSearch(it) },
            placeholder = {
                Text("Search links…", color = c.textDisabled,
                    style = MaterialTheme.typography.bodyMedium)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = c.textDisabled, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { inboxViewModel.setInboxSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear",
                            tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = c.cardBorder,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = AccentTeal
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── AI model banner ───────────────────────────────────────────────────
        if (!modelAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentTeal.copy(alpha = 0.12f))
                    .clickable { showDownloadDialog = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable AI summaries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentTeal
                    )
                    Text(
                        "Download Gemma 4 E2B model (~2.6 GB) · WiFi only",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        if (links.isEmpty() && dismissedIds.isEmpty()) {
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(links, key = { it.id }) { link ->
                    AnimatedVisibility(
                        visible = link.id !in dismissedIds,
                        exit = shrinkVertically(animationSpec = tween(280)) +
                               fadeOut(animationSpec = tween(200))
                    ) {
                        val isExpanded = expandedLinkId == link.id
                        SwipeableCard(
                            onSwipeToArchive = {
                                dismissedIds.add(link.id)
                                inboxViewModel.archiveLink(link.id)
                                inboxViewModel.recordLinkSkip(link)
                                if (expandedLinkId == link.id) expandedLinkId = null
                            }
                        ) {
                            LinkCard(
                                link = link,
                                isExpanded = isExpanded,
                                onTap = {
                                    expandedLinkId = if (isExpanded) null else link.id
                                },
                                onDoubleTap = {
                                    inboxViewModel.openInCustomTab(context, link.url)
                                    inboxViewModel.recordLinkOpen(link)
                                },
                                onLongPress = { actionSheetLink = link }
                            )
                        }
                    }
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
                inboxViewModel.recordLinkOpen(link)
                actionSheetLink = null
            },
            onEditTags = {
                editingLink = link
                actionSheetLink = null
            },
            onArchive = {
                dismissedIds.add(link.id)
                inboxViewModel.archiveLink(link.id)
                inboxViewModel.recordLinkSkip(link)
                if (expandedLinkId == link.id) expandedLinkId = null
                actionSheetLink = null
            },
            onResummarize = if (modelAvailable) {
                {
                    inboxViewModel.resummarizeLink(link)
                    actionSheetLink = null
                }
            } else null
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

    // Model download confirmation dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download AI Model") },
            text = {
                Text(
                    "This will download the Gemma 4 E2B model (~2.6 GB) over WiFi " +
                    "to enable on-device AI summaries.\n\n" +
                    "The download runs in the background and a notification will " +
                    "appear when it's complete.\n\n" +
                    "By downloading you accept Google's Gemma Terms of Use."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    inboxViewModel.enqueueModelDownload()
                    showDownloadDialog = false
                }) {
                    Text("Download", color = AccentTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Not now")
                }
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
        Text("✓", style = MaterialTheme.typography.displayLarge, color = UrgencyFresh)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Inbox Zero",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Share any link to AI Link Triage to start saving.\nSwipe left to archive · double-tap to open.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
