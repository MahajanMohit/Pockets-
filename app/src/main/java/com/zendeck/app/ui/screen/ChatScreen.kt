package com.zendeck.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.service.LlmSummarizationService
import com.zendeck.app.ui.theme.AccentTeal
import com.zendeck.app.ui.theme.LocalZenDeckColors
import com.zendeck.app.ui.viewmodel.ChatMessage
import com.zendeck.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by chatViewModel.isLoading.collectAsStateWithLifecycle()
    val modelAvailable by chatViewModel.modelAvailable.collectAsStateWithLifecycle()
    val activeModelName by chatViewModel.activeModelName.collectAsStateWithLifecycle()
    val activeBackend by chatViewModel.activeBackend.collectAsStateWithLifecycle()
    val discoveredModels by chatViewModel.discoveredModels.collectAsStateWithLifecycle()
    val selectedModel by chatViewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()
    val importStatus by chatViewModel.importStatus.collectAsStateWithLifecycle()
    val articleContext by chatViewModel.articleContext.collectAsStateWithLifecycle()
    val c = LocalZenDeckColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Re-check when screen becomes visible
    LaunchedEffect(Unit) {
        chatViewModel.refreshModels()
        chatViewModel.loadArticleContext()
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Model import launcher (picks any file)
    val modelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) chatViewModel.importModel(uri)
    }

    // Image attach launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        chatViewModel.setSelectedImage(uri)
    }

    // Import status snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(importStatus) {
        when (val s = importStatus) {
            is ChatViewModel.ImportStatus.Done -> {
                snackbarHostState.showSnackbar("Model '${s.fileName}' imported")
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chat AI",
                        color = c.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            importStatus is ChatViewModel.ImportStatus.Copying ->
                                "Importing ${(importStatus as ChatViewModel.ImportStatus.Copying).fileName}…"
                            activeModelName != null -> "$activeModelName · $activeBackend · on-device"
                            else -> "Import a model to start chatting"
                        },
                        color = if (modelAvailable) AccentTeal else c.textSecondary,
                        fontSize = 12.sp
                    )
                }
                // Clear conversation
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { chatViewModel.clearMessages() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear conversation",
                            tint = c.textSecondary
                        )
                    }
                }
                // Import model button
                IconButton(onClick = {
                    modelImportLauncher.launch(arrayOf("*/*"))
                }) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Import model",
                        tint = AccentTeal
                    )
                }
            }

            HorizontalDivider(color = c.textSecondary.copy(alpha = 0.2f))

            // ── Model selector chips ──────────────────────────────────────────
            if (discoveredModels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    discoveredModels.forEach { model ->
                        val isSelected = selectedModel?.path == model.path
                        FilterChip(
                            selected = isSelected,
                            onClick = { chatViewModel.selectModel(model) },
                            label = {
                                Text(
                                    text = model.name + if (model.isVision) " \uD83D\uDC41" else "",
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentTeal.copy(alpha = 0.2f),
                                selectedLabelColor = AccentTeal,
                                containerColor = c.cardBackground,
                                labelColor = c.textSecondary
                            )
                        )
                    }
                }
                HorizontalDivider(color = c.textSecondary.copy(alpha = 0.1f))
            }

            // ── Message list ──────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when {
                                        modelAvailable && articleContext.isNotBlank() ->
                                            "Your reading assistant is ready.\nAsk about your ${articleContext.lines().size} saved article(s), get reading priorities, or just chat."
                                        modelAvailable ->
                                            "Ask anything — the AI model is ready."
                                        else ->
                                            "Import a model using the folder icon above.\nPlace .bin files in Download/gemma/ and tap the icon."
                                    },
                                    color = c.textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
                items(messages) { msg ->
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
                                    .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp))
                                    .background(c.surface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AccentTeal,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }

            // ── Input row ─────────────────────────────────────────────────────
            HorizontalDivider(color = c.textSecondary.copy(alpha = 0.2f))

            // Image attachment preview
            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedImageUri!!.lastPathSegment ?: "Image attached",
                        color = AccentTeal,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    TextButton(
                        onClick = { chatViewModel.setSelectedImage(null) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Remove", color = c.textSecondary, fontSize = 11.sp)
                    }
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
                // Image attach button — always shown when model is available (uses OCR)
                if (modelAvailable) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.cardBackground)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach image (OCR)",
                            tint = if (selectedImageUri != null) AccentTeal else c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Ask about your reading list…", color = c.textSecondary, fontSize = 14.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = c.textSecondary.copy(alpha = 0.4f),
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = AccentTeal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (inputText.isNotBlank() && !isLoading) AccentTeal
                            else AccentTeal.copy(alpha = 0.3f)
                        )
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = c.background,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    colors: com.zendeck.app.ui.theme.ZenDeckColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (msg.isUser)
                        RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)
                    else
                        RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp)
                )
                .background(if (msg.isUser) AccentTeal else colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = msg.text,
                color = if (msg.isUser) colors.background else colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
