package com.zendeck.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkActionSheet(
    link: LinkItem,
    isArchived: Boolean = false,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEditTags: () -> Unit,
    onArchive: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDelete: () -> Unit = {},
    onResummarize: (() -> Unit)? = null
) {
    val c = LocalZenDeckColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.cardBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp),
                content = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = c.cardBorder
                    ) {}
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = link.title,
                style = MaterialTheme.typography.labelMedium,
                color = c.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 8.dp))

            SheetAction(Icons.Default.OpenInBrowser, "Open Link", c.textPrimary) {
                onOpen(); onDismiss()
            }
            SheetAction(Icons.Default.Label, "Edit Tags & Pin", c.textPrimary) {
                onEditTags(); onDismiss()
            }
            if (onResummarize != null) {
                SheetAction(Icons.Default.Refresh, "Reload content", AccentTeal) {
                    onResummarize(); onDismiss()
                }
            }
            if (!isArchived) {
                SheetAction(Icons.Default.Archive, "Move to Archive", c.textSecondary) {
                    onArchive(); onDismiss()
                }
            } else {
                SheetAction(Icons.Default.Unarchive, "Restore to Inbox", AccentTeal) {
                    onRestore(); onDismiss()
                }
                SheetAction(Icons.Default.DeleteForever, "Delete Permanently", UrgencyCritical) {
                    onDelete(); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}
