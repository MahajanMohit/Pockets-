package com.zendeck.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.server.ZenDeckNanoServer
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val ttlHours        by viewModel.ttlHours.collectAsStateWithLifecycle()
    val darkMode        by viewModel.darkMode.collectAsStateWithLifecycle()
    val fontScale       by viewModel.fontScale.collectAsStateWithLifecycle()
    val lanRunning      by viewModel.lanServerRunning.collectAsStateWithLifecycle()
    val aiEnabled       by viewModel.aiSummariesEnabled.collectAsStateWithLifecycle()
    val customPrompt    by viewModel.customSummaryPrompt.collectAsStateWithLifecycle()
    val importStatus    by viewModel.importStatus.collectAsStateWithLifecycle()
    val c               = LocalZenDeckColors.current
    val clipboard       = LocalClipboardManager.current

    val activeModelName by viewModel.activeModelNameState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    // SAF file picker for the AI model — accepts any file type (.bin has no MIME)
    val importModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModelFile(it) } }

    // Show snackbar when import finishes
    LaunchedEffect(importStatus) {
        if (importStatus is SettingsViewModel.ImportStatus.Done ||
            importStatus is SettingsViewModel.ImportStatus.Failed
        ) {
            viewModel.clearImportStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        Spacer(Modifier.height(24.dp))

        // ── Appearance ──────────────────────────────────────────────────────
        SectionHeader("Appearance", c)
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            label = "Dark mode",
            checked = darkMode,
            icon = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
            onToggle = { viewModel.setDarkMode(it) },
            c = c
        )

        Spacer(Modifier.height(16.dp))

        // Font size
        Text("Font size", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        val fontOptions = listOf(
            0.85f to "Small",
            1.0f  to "Normal",
            1.15f to "Large",
            1.3f  to "XL"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fontOptions.forEach { (scale, label) ->
                val isSelected = fontScale == scale
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setFontScale(scale) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AccentTeal.copy(alpha = 0.18f) else c.surface,
                    border = if (isSelected)
                        androidx.compose.foundation.BorderStroke(1.5.dp, AccentTeal)
                    else
                        androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) AccentTeal else c.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        Divider(c)

        // ── Default link expiry ─────────────────────────────────────────────
        SectionHeader("Default Link Expiry", c)
        Text(
            "Links auto-archive after this duration.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.selectableGroup()) {
            listOf(24L to "1 day", 48L to "2 days", 72L to "3 days (default)", 168L to "1 week")
                .forEach { (hours, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = ttlHours == hours,
                                onClick = { viewModel.setTtlHours(hours) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = ttlHours == hours,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = AccentTeal)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ttlHours == hours) c.textPrimary else c.textSecondary
                        )
                    }
                }
        }

        Divider(c)

        // ── AI Model ────────────────────────────────────────────────────────
        SectionHeader("AI Model", c)

        // Active model status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Memory, null,
                tint = if (activeModelName != null) AccentTeal else c.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            val modelLabel = when {
                importStatus is SettingsViewModel.ImportStatus.Copying ->
                    "Copying ${(importStatus as SettingsViewModel.ImportStatus.Copying).fileName}…"
                activeModelName != null -> activeModelName!!.removeSuffix(".litertlm") + " · GPU auto-detected"
                else -> "No model — download and import below"
            }
            Text(modelLabel, style = MaterialTheme.typography.bodySmall,
                color = if (activeModelName != null) AccentTeal else c.textSecondary)
        }

        Spacer(Modifier.height(14.dp))

        // Download instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(c.surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("How to get the model", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text("1. Search HuggingFace for:", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text("   gemma-4-E2B-it.litertlm", style = MaterialTheme.typography.bodySmall, color = AccentTeal,
                fontWeight = FontWeight.Medium)
            Text("   (Model: google/gemma-4-on-device — ~2.6 GB)", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text("2. Save the file to Downloads/ or pick it with the button below.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text("3. GPU is used automatically when available. No toggle needed.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { importModelLauncher.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal),
            enabled = importStatus !is SettingsViewModel.ImportStatus.Copying
        ) {
            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (importStatus is SettingsViewModel.ImportStatus.Copying) "Copying…" else "Import model file (.litertlm)")
        }

        AnimatedVisibility(importStatus is SettingsViewModel.ImportStatus.Done) {
            Text("Model imported. Open the Assistant tab to start chatting.",
                style = MaterialTheme.typography.bodySmall, color = AccentTeal,
                modifier = Modifier.padding(top = 4.dp))
        }
        AnimatedVisibility(importStatus is SettingsViewModel.ImportStatus.Failed) {
            Text("Import failed: ${(importStatus as? SettingsViewModel.ImportStatus.Failed)?.error}",
                style = MaterialTheme.typography.bodySmall, color = UrgencyWarning,
                modifier = Modifier.padding(top = 4.dp))
        }

        LaunchedEffect(Unit) { viewModel.createModelFolder() }

        Divider(c)

        // ── AI Summaries ────────────────────────────────────────────────────
        SectionHeader("AI Summaries", c)
        Text(
            "When enabled, AI Link Triage summarises each saved link using the on-device AI model. " +
            "Turn off to use just the page title and description, or to compare output quality.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            label = "Enable AI summaries",
            checked = aiEnabled,
            icon = Icons.Default.AutoAwesome,
            onToggle = { viewModel.setAiSummariesEnabled(it) },
            c = c
        )

        AnimatedVisibility(aiEnabled) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Custom summary prompt",
                    style = MaterialTheme.typography.labelMedium, color = c.textPrimary
                )
                Text(
                    "Leave blank to use the built-in prompt (5–6 sentence prose summary). " +
                    "Write your own to change the output style. The article text is always appended after your prompt.",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                var promptDraft by remember(customPrompt) { mutableStateOf(customPrompt) }
                OutlinedTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "e.g. Summarise in 2 sentences focusing on key takeaways.",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = c.textSecondary.copy(alpha = 0.4f),
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = AccentTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3, maxLines = 6
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.setCustomSummaryPrompt(promptDraft) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
                    ) { Text("Save prompt") }
                    if (customPrompt.isNotBlank()) {
                        TextButton(onClick = {
                            promptDraft = ""
                            viewModel.setCustomSummaryPrompt("")
                        }) { Text("Reset to default", color = c.textSecondary) }
                    }
                }
                if (customPrompt.isNotBlank()) {
                    Text(
                        "Custom prompt active",
                        style = MaterialTheme.typography.labelSmall, color = AccentTeal,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Divider(c)

        // ── AI Memory ───────────────────────────────────────────────────────
        SectionHeader("AI Memory", c)
        Text(
            "The Chat AI remembers facts stored here. Write anything you want it to know about you, " +
            "your preferences, or your work. This context is included in every chat session.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(10.dp))

        // Read memory directly from file for the settings view
        val memCtx = androidx.compose.ui.platform.LocalContext.current
        var memoryDraft by remember {
            val f = memCtx.filesDir.resolve("user_memory.txt")
            mutableStateOf(if (f.exists()) f.readText() else "")
        }

        OutlinedTextField(
            value = memoryDraft,
            onValueChange = { memoryDraft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "e.g. I'm a software engineer interested in AI, productivity, and startups. " +
                    "I prefer concise technical summaries.",
                    style = MaterialTheme.typography.bodySmall, color = c.textDisabled
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = c.textSecondary.copy(alpha = 0.4f),
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = AccentTeal
            ),
            shape = RoundedCornerShape(10.dp),
            minLines = 4, maxLines = 8
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    try { memCtx.filesDir.resolve("user_memory.txt").writeText(memoryDraft) }
                    catch (_: Exception) { }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
            ) { Text("Save memory") }
            if (memoryDraft.isNotBlank()) {
                TextButton(onClick = {
                    memoryDraft = ""
                    try { memCtx.filesDir.resolve("user_memory.txt").delete() }
                    catch (_: Exception) { }
                }) { Text("Clear", color = c.textSecondary) }
            }
        }

        Divider(c)

        // ── Backup & Restore ────────────────────────────────────────────────
        SectionHeader("Backup & Restore", c)
        Text(
            "Export your links as JSON. Save to Google Drive or local storage. Import to restore.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("zendeck_backup.json") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
            ) { Text("Export") }
            OutlinedButton(
                onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)
            ) { Text("Import") }
        }
        Text(
            "Tip: Android automatically backs up your data to Google Drive. Reinstalling restores your links.",
            style = MaterialTheme.typography.labelSmall, color = c.textDisabled,
            modifier = Modifier.padding(top = 8.dp)
        )

        Divider(c)

        // ── LAN Access ──────────────────────────────────────────────────────
        SectionHeader("LAN Access", c)
        Text(
            "Open your saved links in any laptop browser on the same WiFi network.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            label = if (lanRunning) "Server running" else "Server off",
            checked = lanRunning,
            icon = if (lanRunning) Icons.Default.Wifi else Icons.Default.WifiOff,
            onToggle = { viewModel.toggleLanServer(it) },
            c = c
        )
        if (lanRunning) {
            val ip = viewModel.getLanIpAddress()
            Spacer(Modifier.height(10.dp))
            if (ip != null) {
                val url = "http://$ip:${ZenDeckNanoServer.PORT}"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentTeal.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(url)) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(url, style = MaterialTheme.typography.bodyMedium, color = AccentTeal, modifier = Modifier.weight(1f))
                        Text("Copy", style = MaterialTheme.typography.labelSmall, color = AccentTeal.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap to copy · Open on your laptop browser · Refresh auto-updates every 30s",
                    style = MaterialTheme.typography.labelSmall, color = c.textDisabled
                )
            } else {
                Text("No WiFi detected. Connect to WiFi for LAN access.",
                    style = MaterialTheme.typography.bodySmall, color = UrgencyWarning)
            }
        }

        Divider(c)

        // ── App info ────────────────────────────────────────────────────────
        Text("AI Link Triage v1.0", style = MaterialTheme.typography.labelSmall, color = c.textDisabled)
        Text("All data stored locally. No tracking, no cloud AI, no ads.",
            style = MaterialTheme.typography.labelSmall, color = c.textDisabled)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cards: tap to expand · tap again to collapse · double-tap to open · long-press for actions",
            style = MaterialTheme.typography.labelSmall, color = c.textDisabled
        )
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, c: ZenDeckColors) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
}

@Composable
private fun Divider(c: ZenDeckColors) {
    HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit,
    c: ZenDeckColors
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (checked) AccentTeal else c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentTeal,
                checkedTrackColor = AccentTeal.copy(alpha = 0.4f)
            )
        )
    }
}
