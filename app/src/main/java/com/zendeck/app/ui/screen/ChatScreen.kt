package com.zendeck.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.ChatMessage
import com.zendeck.app.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages        by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading       by chatViewModel.isLoading.collectAsStateWithLifecycle()
    val modelAvailable  by chatViewModel.modelAvailable.collectAsStateWithLifecycle()
    val activeModelName by chatViewModel.activeModelName.collectAsStateWithLifecycle()
    val pendingImage    by chatViewModel.pendingImageUri.collectAsStateWithLifecycle()
    val importStatus    by chatViewModel.importStatus.collectAsStateWithLifecycle()
    val c               = LocalZenDeckColors.current
    val listState       = rememberLazyListState()
    val focusManager    = LocalFocusManager.current
    var inputText       by remember { mutableStateOf("") }
    val snackbarState   = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> chatViewModel.setPendingImage(uri) }

    val modelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { chatViewModel.importModelFile(it) } }

    LaunchedEffect(Unit) { chatViewModel.refreshModelState() }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty() || isLoading) {
            listState.animateScrollToItem(maxOf(0, messages.size - 1))
        }
    }

    // Snackbar for import status
    LaunchedEffect(importStatus) {
        when (val s = importStatus) {
            is ChatViewModel.ImportStatus.Done -> {
                snackbarState.showSnackbar("'${s.fileName}' imported — restart the chat to use it.")
                chatViewModel.clearImportStatus()
            }
            is ChatViewModel.ImportStatus.Failed -> {
                snackbarState.showSnackbar("Import failed: ${s.error}")
                chatViewModel.clearImportStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Chat AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary
                    )
                    val subtitle = when {
                        importStatus is ChatViewModel.ImportStatus.Copying ->
                            "Importing ${(importStatus as ChatViewModel.ImportStatus.Copying).fileName}…"
                        activeModelName != null ->
                            activeModelName!!.removeSuffix(".litertlm") + " · CPU"
                        else -> "No model loaded"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (modelAvailable) AccentTeal else c.textDisabled
                    )
                }
                if (messages.size > 1) {
                    IconButton(onClick = { chatViewModel.clearChat() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear chat", tint = c.textSecondary,
                            modifier = Modifier.size(20.dp))
                    }
                }
                // Import model shortcut
                IconButton(onClick = { modelImportLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, "Import model",
                        tint = if (modelAvailable) c.textSecondary else AccentTeal,
                        modifier = Modifier.size(20.dp))
                }
            }

            // ── No-model banner ───────────────────────────────────────────────
            AnimatedVisibility(!modelAvailable) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = AccentTeal.copy(alpha = 0.10f)
                ) {
                    Text(
                        "Import a Gemma 4 2B .litertlm model file via the folder icon above, or in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // ── Messages ──────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message, c = c)
                }

                // Typing indicator
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp, topEnd = 16.dp,
                                    bottomStart = 16.dp, bottomEnd = 16.dp
                                ),
                                color = c.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = AccentTeal,
                                        trackColor = AccentTeal.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        "Generating…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── Pending image preview ─────────────────────────────────────────
            if (pendingImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    AsyncImage(
                        model = pendingImage,
                        contentDescription = "Image to send",
                        modifier = Modifier
                            .height(80.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { chatViewModel.setPendingImage(null) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.Cancel, "Remove image",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        )
                    }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image attach button
                    IconButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                        Icon(
                            Icons.Default.AttachFile, "Attach image",
                            tint = if (pendingImage != null) AccentTeal else c.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Text field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(c.cardBackground, RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                "Ask anything…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textDisabled
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                            cursorBrush = SolidColor(AccentTeal),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 5
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Send button
                    val canSend = !isLoading && (inputText.isNotBlank() || pendingImage != null)
                    FilledIconButton(
                        onClick = {
                            if (canSend) {
                                focusManager.clearFocus()
                                chatViewModel.sendMessage(inputText.trim(), pendingImage)
                                inputText = ""
                            }
                        },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentTeal,
                            disabledContainerColor = AccentTeal.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.Default.Send, "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage, c: ZenDeckColors) {
    if (message.isUser) {
        // User bubble — right-aligned, teal
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            message.imageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(4.dp))
            }
            if (message.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 4.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp
                    ),
                    color = AccentTeal,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    } else {
        // Assistant bubble — left-aligned, wider, rendered with simple markdown
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = c.surface,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text(
                text = renderMarkdown(message.text, c),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** Renders **bold**, *italic*, and bullet points from markdown-like text. */
@Composable
private fun renderMarkdown(
    text: String,
    c: ZenDeckColors
) = buildAnnotatedString {
    text.lines().forEach { line ->
        val trimmed = line.trim()
        // Bullet point
        if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
            val content = trimmed.trimStart('•', '-', '*', ' ')
            append("• ")
            appendInlineMarkdown(content, c)
        } else if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
            // Heading / bold line
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(trimmed.removeSurrounding("**"))
            pop()
        } else {
            appendInlineMarkdown(trimmed, c)
        }
        append("\n")
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    c: ZenDeckColors
) {
    // Simple bold (**text**) and italic (*text*) parsing
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    val italicRegex = Regex("""\*(.+?)\*""")

    var remaining = text
    while (remaining.isNotEmpty()) {
        val boldMatch = boldRegex.find(remaining)
        val italicMatch = italicRegex.find(remaining)
        val nextMatch = listOfNotNull(boldMatch, italicMatch).minByOrNull { it.range.first }

        if (nextMatch == null) {
            append(remaining)
            break
        }
        // Append text before the match
        if (nextMatch.range.first > 0) append(remaining.take(nextMatch.range.first))

        if (nextMatch == boldMatch) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(nextMatch.groupValues[1])
            pop()
        } else {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append(nextMatch.groupValues[1])
            pop()
        }
        remaining = remaining.drop(nextMatch.range.last + 1)
    }
}
