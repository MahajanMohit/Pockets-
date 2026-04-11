package com.zendeck.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.theme.ZenDeckColors
import com.zendeck.app.ui.viewmodel.ChatMessage
import com.zendeck.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages        by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading       by chatViewModel.isLoading.collectAsStateWithLifecycle()
    val modelAvailable  by chatViewModel.modelAvailable.collectAsStateWithLifecycle()
    val activeModelName by chatViewModel.activeModelName.collectAsStateWithLifecycle()
    val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()
    val importStatus    by chatViewModel.importStatus.collectAsStateWithLifecycle()
    val c               = LocalZenDeckColors.current
    val listState       = rememberLazyListState()
    val scope           = rememberCoroutineScope()
    var inputText       by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        chatViewModel.refreshModels()
        chatViewModel.loadArticleContext()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val modelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) chatViewModel.importModel(uri) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> chatViewModel.setSelectedImage(uri) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(importStatus) {
        when (val s = importStatus) {
            is ChatViewModel.ImportStatus.Done -> {
                snackbarHostState.showSnackbar("Model '${s.fileName}' imported. Restart the chat to use it.")
                chatViewModel.clearImportStatus()
            }
            is ChatViewModel.ImportStatus.Failed -> {
                snackbarHostState.showSnackbar("Import failed: ${s.error}")
                chatViewModel.clearImportStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(c.background)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assistant",
                        color = c.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            importStatus is ChatViewModel.ImportStatus.Copying ->
                                "Importing ${(importStatus as ChatViewModel.ImportStatus.Copying).fileName}…"
                            activeModelName != null -> "on-device · ${activeModelName!!.removeSuffix(".litertlm")}"
                            else -> "No model — tap the folder icon to import"
                        },
                        color = if (modelAvailable) AccentTeal else c.textSecondary,
                        fontSize = 12.sp
                    )
                }
                if (messages.size > 1) {
                    IconButton(onClick = { chatViewModel.clearMessages() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear conversation",
                            tint = c.textSecondary)
                    }
                }
                IconButton(onClick = { modelImportLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Import model",
                        tint = AccentTeal)
                }
            }

            HorizontalDivider(color = c.textSecondary.copy(alpha = 0.15f))

            // ── Message list ──────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.hashCode() }) { msg ->
                    MessageBubble(msg = msg, colors = c)
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                                    .background(c.surface)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AccentTeal,
                                    strokeWidth = 2.dp,
                                    trackColor = AccentTeal.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Input row ─────────────────────────────────────────────────────
            HorizontalDivider(color = c.textSecondary.copy(alpha = 0.15f))

            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AttachFile, null, tint = AccentTeal,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedImageUri!!.lastPathSegment ?: "Image attached",
                        color = AccentTeal, fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 1
                    )
                    TextButton(
                        onClick = { chatViewModel.setSelectedImage(null) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("Remove", color = c.textSecondary, fontSize = 11.sp) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (modelAvailable) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.cardBackground)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach image",
                            tint = if (selectedImageUri != null) AccentTeal else c.textSecondary,
                            modifier = Modifier.size(18.dp))
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message…", color = c.textSecondary, fontSize = 14.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = c.textSecondary.copy(alpha = 0.3f),
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = AccentTeal
                    ),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            chatViewModel.sendMessage(inputText)
                            inputText = ""
                            scope.launch {
                                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    })
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            chatViewModel.sendMessage(inputText)
                            inputText = ""
                            scope.launch {
                                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (inputText.isNotBlank() && !isLoading) AccentTeal
                            else AccentTeal.copy(alpha = 0.25f)
                        )
                        .size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                        tint = c.background, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Message bubble with Markdown ─────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage, colors: ZenDeckColors) {
    val isUser = msg.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                    .background(AccentTeal)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text = msg.text, color = colors.background, fontSize = 15.sp, lineHeight = 22.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                MarkdownText(
                    text = msg.text,
                    textColor = colors.textPrimary,
                    codeBackground = colors.background
                )
            }
        }
    }
}

// ── Markdown renderer ─────────────────────────────────────────────────────────

@Composable
private fun MarkdownText(text: String, textColor: Color, codeBackground: Color) {
    val paragraphs = text.split(Regex("\n{2,}"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (paragraph in paragraphs) {
            val lines = paragraph.lines()
            when {
                // Bullet list
                lines.all { it.isBlank() || it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") || it.trimStart().startsWith("• ") } && lines.any { !it.isBlank() } -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (line in lines) {
                            if (line.isBlank()) continue
                            val content = line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("• ")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = AccentTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = parseInline(content, codeBackground),
                                    color = textColor, fontSize = 15.sp, lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
                // Heading
                paragraph.startsWith("### ") -> Text(
                    text = paragraph.removePrefix("### "),
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp
                )
                paragraph.startsWith("## ") -> Text(
                    text = paragraph.removePrefix("## "),
                    color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp
                )
                paragraph.startsWith("# ") -> Text(
                    text = paragraph.removePrefix("# "),
                    color = textColor, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp
                )
                // Code block
                paragraph.startsWith("```") -> {
                    val code = paragraph.lines().drop(1).dropLastWhile { it.startsWith("```") || it.isBlank() }.joinToString("\n")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeBackground
                    ) {
                        Text(
                            text = code,
                            color = AccentTeal,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                // Regular paragraph (possibly multi-line)
                else -> Text(
                    text = parseInline(paragraph, codeBackground),
                    color = textColor, fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}

private fun parseInline(text: String, @Suppress("UNUSED_PARAMETER") codeBackground: Color) = buildAnnotatedString {
    var remaining = text
    while (remaining.isNotEmpty()) {
        when {
            remaining.startsWith("**") -> {
                val end = remaining.indexOf("**", 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(remaining.substring(2, end)) }
                    remaining = remaining.substring(end + 2)
                } else { append("**"); remaining = remaining.substring(2) }
            }
            remaining.startsWith("*") -> {
                val end = remaining.indexOf("*", 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(remaining.substring(1, end)) }
                    remaining = remaining.substring(end + 1)
                } else { append("*"); remaining = remaining.substring(1) }
            }
            remaining.startsWith("`") -> {
                val end = remaining.indexOf("`", 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                        append(remaining.substring(1, end))
                    }
                    remaining = remaining.substring(end + 1)
                } else { append("`"); remaining = remaining.substring(1) }
            }
            else -> {
                val nextSpecial = listOf("**", "*", "`")
                    .mapNotNull { m -> remaining.indexOf(m).takeIf { it > 0 } }
                    .minOrNull() ?: remaining.length
                append(remaining.substring(0, nextSpecial))
                remaining = remaining.substring(nextSpecial)
            }
        }
    }
}
