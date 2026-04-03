package com.zendeck.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.ZenDeckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZenDeckWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Required: set CANCELED as default result in case user backs out
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ZenDeckTheme(useDarkTheme = true) {
                WidgetLinkPicker(
                    appWidgetId = appWidgetId,
                    onPicked = { linkId ->
                        savePinnedLink(appWidgetId, linkId)
                        refreshWidget(appWidgetId)
                        setResult(RESULT_OK, resultIntent(appWidgetId))
                        finish()
                    },
                    onUseAutomatic = {
                        savePinnedLink(appWidgetId, null)
                        refreshWidget(appWidgetId)
                        setResult(RESULT_OK, resultIntent(appWidgetId))
                        finish()
                    }
                )
            }
        }
    }

    private fun savePinnedLink(appWidgetId: Int, linkId: String?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (linkId != null) prefs.edit().putString(pinnedKey(appWidgetId), linkId).apply()
        else prefs.edit().remove(pinnedKey(appWidgetId)).apply()
    }

    private fun refreshWidget(appWidgetId: Int) {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            try {
                val glanceIds = GlanceAppWidgetManager(applicationContext)
                    .getGlanceIds(ZenDeckWidget::class.java)
                glanceIds.forEach { ZenDeckWidget().update(applicationContext, it) }
            } catch (_: Exception) { }
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        const val PREFS_NAME = "zendeck_widget_prefs"
        fun pinnedKey(appWidgetId: Int) = "pinned_$appWidgetId"
    }
}

@Composable
private fun WidgetLinkPicker(
    appWidgetId: Int,
    onPicked: (String) -> Unit,
    onUseAutomatic: () -> Unit
) {
    val repository = LinkRepository.getInstance(androidx.compose.ui.platform.LocalContext.current)
    var links by remember { mutableStateOf<List<LinkItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        links = repository.getActiveLinks().first()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Pin a link to widget",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Choose which link the widget always shows, or let it pick the most urgent one automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // "Automatic" option at the top
                item {
                    ListItem(
                        headlineContent = { Text("Most urgent (automatic)") },
                        supportingContent = { Text("Widget always shows the link expiring soonest") },
                        modifier = Modifier.clickable { onUseAutomatic() }
                    )
                    HorizontalDivider()
                }

                if (links.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No saved links yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(links, key = { it.id }) { link ->
                        ListItem(
                            headlineContent = {
                                Text(link.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${link.domain}  ·  expires ${link.timeUntilExpiry}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { onPicked(link.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
