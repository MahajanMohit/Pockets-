package com.zendeck.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
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
    val ttlHours     by viewModel.ttlHours.collectAsStateWithLifecycle()
    val darkMode     by viewModel.darkMode.collectAsStateWithLifecycle()
    val fontScale    by viewModel.fontScale.collectAsStateWithLifecycle()
    val lanRunning   by viewModel.lanServerRunning.collectAsStateWithLifecycle()
    val customPrompt by viewModel.customSummaryPrompt.collectAsStateWithLifecycle()
    val c            = LocalZenDeckColors.current
    val clipboard    = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

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
                        BorderStroke(1.5.dp, AccentTeal)
                    else
                        BorderStroke(1.dp, c.cardBorder)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) AccentTeal else c.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        SettingsDivider(c)

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

        SettingsDivider(c)

        // ── Summary ─────────────────────────────────────────────────────────
        SectionHeader("Summary", c)
        Text(
            "Pockets extracts key sentences from any link, screenshot or note you share. " +
            "No AI model required — works instantly on-device.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Custom focus hint (optional)",
            style = MaterialTheme.typography.labelMedium, color = c.textPrimary
        )
        Text(
            "Describe what to emphasise (e.g. \"technical details\" or \"business impact\"). " +
            "Leave blank for balanced extraction.",
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
                    "e.g. Focus on key takeaways and practical steps.",
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
            minLines = 2, maxLines = 4
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.setCustomSummaryPrompt(promptDraft) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = BorderStroke(1.dp, AccentTeal)
            ) { Text("Save") }
            if (customPrompt.isNotBlank()) {
                TextButton(onClick = {
                    promptDraft = ""
                    viewModel.setCustomSummaryPrompt("")
                }) { Text("Reset", color = c.textSecondary) }
            }
        }

        SettingsDivider(c)

        // ── Backup & Restore ────────────────────────────────────────────────
        SectionHeader("Backup & Restore", c)
        Text(
            "Export your links as JSON to save or move them.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("pockets_backup.json") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = BorderStroke(1.dp, AccentTeal)
            ) { Text("Export") }
            OutlinedButton(
                onClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                border = BorderStroke(1.dp, c.cardBorder)
            ) { Text("Import") }
        }

        SettingsDivider(c)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(url)) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentTeal,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Copy", style = MaterialTheme.typography.labelSmall,
                            color = AccentTeal.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap to copy · Open on your laptop browser",
                    style = MaterialTheme.typography.labelSmall, color = c.textDisabled
                )
            } else {
                Text(
                    "No WiFi detected. Connect to WiFi for LAN access.",
                    style = MaterialTheme.typography.bodySmall, color = UrgencyWarning
                )
            }
        }

        SettingsDivider(c)

        // ── About ───────────────────────────────────────────────────────────
        Text("Pockets v1.0", style = MaterialTheme.typography.labelSmall, color = c.textDisabled)
        Text(
            "All data stored locally on your device. No tracking, no cloud sync, no ads.",
            style = MaterialTheme.typography.labelSmall, color = c.textDisabled
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap to expand · tap again to collapse · double-tap to open · long-press for actions",
            style = MaterialTheme.typography.labelSmall, color = c.textDisabled
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, c: ZenDeckColors) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
}

@Composable
private fun SettingsDivider(c: ZenDeckColors) {
    HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit,
    c: ZenDeckColors
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (checked) AccentTeal else c.textSecondary,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
            modifier = Modifier.weight(1f))
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
