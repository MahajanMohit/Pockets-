package com.zendeck.app.ui.screen

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
    val showSummary by viewModel.showSummary.collectAsStateWithLifecycle()

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

        // TTL Setting
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
            val options = listOf(
                24L to "1 day",
                48L to "2 days",
                72L to "3 days (default)",
                168L to "1 week"
            )
            options.forEach { (hours, label) ->
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

        // Show AI Summary toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show AI Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Show on-device TL;DR on cards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = showSummary,
                onCheckedChange = { viewModel.setShowSummary(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = AccentTeal)
            )
        }

        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 16.dp))

        // App info
        Text(
            text = "ZenDeck v1.0",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled
        )
        Text(
            text = "All data stored locally. No tracking, no ads.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled
        )
    }
}
