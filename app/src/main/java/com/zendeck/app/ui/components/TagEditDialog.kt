package com.zendeck.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    link: LinkItem,
    onDismiss: () -> Unit,
    onSave: (tags: List<String>, isPinned: Boolean) -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(link.tags.toMutableList()) }
    var isPinned by remember { mutableStateOf(link.isPinned) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Edit Link",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column {
                // Pin toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin",
                        tint = if (isPinned) AccentTeal else TextDisabled,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pin to top",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentTeal)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Tag input
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Add tag…", color = TextDisabled) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = CardBorderDefault,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentTeal
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val tag = tagInput.trim().lowercase()
                        if (tag.isNotBlank() && !tags.contains(tag)) {
                            tags = (tags + tag).toMutableList()
                        }
                        tagInput = ""
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                // Existing tags
                if (tags.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text("#$tag", color = AccentTeal) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { tags = tags.filter { it != tag }.toMutableList() },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = AccentTeal.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Auto-commit any text the user typed but didn't confirm with keyboard Done
                val pending = tagInput.trim().lowercase()
                val finalTags = if (pending.isNotBlank() && !tags.contains(pending))
                    tags + pending
                else
                    tags.toList()
                onSave(finalTags, isPinned)
            }) {
                Text("Save", color = AccentTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
