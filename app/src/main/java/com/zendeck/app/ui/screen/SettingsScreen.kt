package com.zendeck.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zendeck.app.ui.theme.*
import com.zendeck.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val ttlHours by viewModel.ttlHours.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val c = LocalZenDeckColors.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
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
