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
    val lanRunning      by viewModel.lanServerRunning.collectAsStateWithLifecycle()
    val aiEnabled       by viewModel.aiSummariesEnabled.collectAsStateWithLifecycle()
    val customPrompt    by viewModel.customSummaryPrompt.collectAsStateWithLifecycle()
    val importStatus    by viewModel.importStatus.collectAsStateWithLifecycle()
    val syncPeerIp      by viewModel.syncPeerIp.collectAsStateWithLifecycle()
    val syncStatus      by viewModel.syncStatus.collectAsStateWithLifecycle()
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

        // Active model indicator
        val modelLabel = when {
            importStatus is SettingsViewModel.ImportStatus.Copying ->
                "Copying ${(importStatus as SettingsViewModel.ImportStatus.Copying).fileName}…"
            activeModelName != null -> "Active: $activeModelName"
            else -> "No model found"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Memory, null,
                tint = if (activeModelName != null) AccentTeal else c.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(modelLabel, style = MaterialTheme.typography.bodySmall,
                color = if (activeModelName != null) AccentTeal else c.textSecondary)
        }

        Spacer(Modifier.height(12.dp))

        // Import model via SAF (no All Files permission needed)
        Text(
            "Import via file picker (recommended)",
            style = MaterialTheme.typography.labelMedium, color = c.textPrimary
        )
        Text(
            "Picks the .bin file from anywhere on your device and copies it into the app's " +
            "private storage — no special permissions required. Works for both CPU and GPU models.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importModelLauncher.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal),
            enabled = importStatus !is SettingsViewModel.ImportStatus.Copying
        ) {
            Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (importStatus is SettingsViewModel.ImportStatus.Copying) "Copying…" else "Import model file")
        }

        // Import status feedback
        AnimatedVisibility(importStatus is SettingsViewModel.ImportStatus.Done) {
            Text(
                "✓ Model imported successfully. Send a message in Chat AI to test it.",
                style = MaterialTheme.typography.bodySmall, color = AccentTeal,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        AnimatedVisibility(importStatus is SettingsViewModel.ImportStatus.Failed) {
            Text(
                "Import failed: ${(importStatus as? SettingsViewModel.ImportStatus.Failed)?.error}",
                style = MaterialTheme.typography.bodySmall, color = UrgencyWarning,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // Manual folder instructions (expandable)
        var showInstructions by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showInstructions = !showInstructions }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Manual setup instructions",
                style = MaterialTheme.typography.labelMedium, color = c.textSecondary
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = c.textSecondary, modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(showInstructions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("1. The app has created a folder for you:", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                Text("   /storage/emulated/0/Download/gemma/", style = MaterialTheme.typography.bodySmall, color = AccentTeal)
                Text("2. Download one of these CPU model files into that folder:", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                Text("   • gemma-2b-it-cpu-int4.bin      (Gemma 2B CPU — recommended, ~1.35 GB)", style = MaterialTheme.typography.bodySmall, color = AccentTeal)
                Text("   • gemma-3-1b-it-cpu-int4.bin    (Gemma 3 1B CPU — faster, ~0.8 GB)", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text("   • gemma-3-4b-it-cpu-int4.bin    (Gemma 3 4B CPU — best quality, ~2.5 GB)", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text("   • gemma-2b-it-gpu-int4.bin      (GPU only — use only if CPU fails, ~1.0 GB)", style = MaterialTheme.typography.bodySmall, color = c.textDisabled)
                Text("3. OR use the 'Import model file' button above to pick the file directly — no folder needed.", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                Text("4. On Android 11+ you may need to grant 'All files access' in:\n   Settings → Apps → ZenDeck → Permissions", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text("After setup go to Chat AI tab and type anything to verify.", style = MaterialTheme.typography.bodySmall, color = AccentTeal)
            }
        }

        // Create the Download/gemma folder proactively
        LaunchedEffect(Unit) { viewModel.createModelFolder() }

        Divider(c)

        // ── AI Summaries ────────────────────────────────────────────────────
        SectionHeader("AI Summaries", c)
        Text(
            "When enabled, ZenDeck summarises each saved link using the on-device AI model. " +
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

        // ── Device Sync ─────────────────────────────────────────────────────
        SectionHeader("Device Sync", c)
        Text(
            "Share links instantly between two devices on the same WiFi. " +
            "Enter the IP address of the other device (shown in its LAN Access section).",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(12.dp))

        var peerIpDraft by remember(syncPeerIp) { mutableStateOf(syncPeerIp) }
        OutlinedTextField(
            value = peerIpDraft,
            onValueChange = { peerIpDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Peer device IP", style = MaterialTheme.typography.bodySmall) },
            placeholder = { Text("e.g. 192.168.1.42", style = MaterialTheme.typography.bodySmall, color = c.textSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = c.textSecondary.copy(alpha = 0.4f),
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = AccentTeal
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.setSyncPeerIp(peerIpDraft) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
            ) { Text("Save IP") }
            OutlinedButton(
                onClick = { viewModel.syncFromPeer() },
                enabled = syncStatus !is SettingsViewModel.SyncStatus.Syncing && syncPeerIp.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
            ) {
                if (syncStatus is SettingsViewModel.SyncStatus.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentTeal
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Syncing…")
                } else {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pull from peer")
                }
            }
        }
        when (val s = syncStatus) {
            is SettingsViewModel.SyncStatus.Done -> {
                Text(
                    "✓ Pulled ${s.count} link(s) from peer",
                    style = MaterialTheme.typography.bodySmall, color = AccentTeal,
                    modifier = Modifier.padding(top = 4.dp)
                )
                LaunchedEffect(s) { kotlinx.coroutines.delay(4_000); viewModel.clearSyncStatus() }
            }
            is SettingsViewModel.SyncStatus.Failed -> {
                Text(
                    "Sync failed: ${s.error}",
                    style = MaterialTheme.typography.bodySmall, color = UrgencyWarning,
                    modifier = Modifier.padding(top = 4.dp)
                )
                LaunchedEffect(s) { kotlinx.coroutines.delay(6_000); viewModel.clearSyncStatus() }
            }
            else -> {}
        }
        if (syncPeerIp.isNotBlank()) {
            Text(
                "New links you save will also be pushed to $syncPeerIp automatically.",
                style = MaterialTheme.typography.labelSmall, color = c.textDisabled,
                modifier = Modifier.padding(top = 6.dp)
            )
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
        Text("ZenDeck v1.0", style = MaterialTheme.typography.labelSmall, color = c.textDisabled)
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
