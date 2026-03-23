package com.zendeck.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val ttlHours by viewModel.ttlHours.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val lanRunning by viewModel.lanServerRunning.collectAsStateWithLifecycle()
    val c = LocalZenDeckColors.current
    val clipboard = LocalClipboardManager.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary
        )

        Spacer(Modifier.height(24.dp))

        // ── Appearance ─────────────────────────────────────────────────────
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Dark mode",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentTeal,
                    checkedTrackColor = AccentTeal.copy(alpha = 0.4f)
                )
            )
        }

        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))

        // ── Default link expiry ────────────────────────────────────────────
        Text(
            text = "Default Link Expiry",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary
        )
        Text(
            text = "Links auto-archive after this duration.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.selectableGroup()) {
            listOf(
                24L to "1 day",
                48L to "2 days",
                72L to "3 days (default)",
                168L to "1 week"
            ).forEach { (hours, label) ->
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
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ttlHours == hours) c.textPrimary else c.textSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))

        // ── Backup & Restore ───────────────────────────────────────────────
        Text(
            text = "Backup & Restore",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary
        )
        Text(
            text = "Export your links as JSON. Save to Google Drive or local storage. Import to restore.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("zendeck_backup.json") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal)
            ) {
                Text("Export")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)
            ) {
                Text("Import")
            }
        }

        Text(
            text = "Tip: Android automatically backs up your data to Google Drive. Reinstalling the app restores your links.",
            style = MaterialTheme.typography.labelSmall,
            color = c.textDisabled,
            modifier = Modifier.padding(top = 8.dp)
        )

        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))

        // ── LAN Access ────────────────────────────────────────────────────
        Text(
            text = "LAN Access",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary
        )
        Text(
            text = "Open your saved links in any laptop browser on the same WiFi network.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (lanRunning) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (lanRunning) AccentTeal else c.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (lanRunning) "Server running" else "Server off",
                style = MaterialTheme.typography.bodyMedium,
                color = if (lanRunning) c.textPrimary else c.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = lanRunning,
                onCheckedChange = { viewModel.toggleLanServer(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentTeal,
                    checkedTrackColor = AccentTeal.copy(alpha = 0.4f)
                )
            )
        }
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
                            text = url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentTeal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentTeal.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap to copy · Open on your laptop browser · Refresh auto-updates every 30s",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textDisabled
                )
            } else {
                Text(
                    text = "No WiFi detected. Connect to WiFi for LAN access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UrgencyWarning
                )
            }
        }

        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 16.dp))

        // ── App info ───────────────────────────────────────────────────────
        Text("ZenDeck v1.0", style = MaterialTheme.typography.labelSmall, color = c.textDisabled)
        Text(
            "All data stored locally. No tracking, no cloud AI, no ads.",
            style = MaterialTheme.typography.labelSmall,
            color = c.textDisabled
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Cards: tap to expand · tap again to collapse · double-tap to open · long-press for actions",
            style = MaterialTheme.typography.labelSmall,
            color = c.textDisabled
        )
    }
}
