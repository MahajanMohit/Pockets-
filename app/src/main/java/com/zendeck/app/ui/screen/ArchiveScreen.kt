package com.zendeck.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.components.LinkCard
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.InboxViewModel

@Composable
fun ArchiveScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = viewModel()
) {
    val links by viewModel.archivedLinks.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Archive",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (links.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing archived yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(links, key = { it.id }) { link ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinkCard(
                            link = link,
                            modifier = Modifier.weight(1f),
                            showSummary = false
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.deleteLink(link.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = UrgencyCritical
                            )
                        }
                    }
                }
            }
        }
    }
}
