package com.zendeck.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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

    // SAF launchers for export and import
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
            color = TextPrimary
        )

        Spacer(Modifier.height(24.dp))

        // ── Default link expiry ────────────────────────────────────────────
        Text(
            text = "Default Link Expiry",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Text(
            text = "Links auto-archive after this duration.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
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
                        color = if (ttlHours == hours) TextPrimary else TextSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 16.dp))

        // ── Backup & Restore ───────────────────────────────────────────────
        Text(
            text = "Backup & Restore",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Text(
            text = "Export your links as JSON. Save to Google Drive or local storage. Import to restore.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDefault)
            ) {
                Text("Import")
            }
        }

        Text(
            text = "Tip: Android automatically backs up your data to Google Drive. Reinstalling the app restores your links.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
            modifier = Modifier.padding(top = 8.dp)
        )

        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 16.dp))

        // ── App info ───────────────────────────────────────────────────────
        Text("ZenDeck v1.0", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
        Text(
            "All data stored locally. No tracking, no cloud AI, no ads.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Cards: tap to expand summary · tap again to open · long-press for actions",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled
        )
    }
}
