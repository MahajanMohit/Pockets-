package com.zendeck.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.zendeck.app.MainActivity
import com.zendeck.app.data.db.ZenDeckDatabase
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import kotlinx.coroutines.flow.first

class ZenDeckWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = LinkRepository.getInstance(context)

        // Check for a user-pinned link for this widget instance
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) { -1 }

        val displayLink: LinkItem? = if (appWidgetId != -1) {
            val prefs = context.getSharedPreferences(
                ZenDeckWidgetConfigActivity.PREFS_NAME, Context.MODE_PRIVATE
            )
            val pinnedId = prefs.getString(ZenDeckWidgetConfigActivity.pinnedKey(appWidgetId), null)
            if (pinnedId != null) {
                try {
                    val entity = ZenDeckDatabase.getInstance(context).linkDao().getLinkById(pinnedId)
                    if (entity != null && !entity.isArchived) {
                        entity.toDomain()
                    } else {
                        // Pinned link archived/deleted — fall back to automatic
                        prefs.edit().remove(ZenDeckWidgetConfigActivity.pinnedKey(appWidgetId)).apply()
                        repo.getMostUrgentActive().first()
                    }
                } catch (_: Exception) { repo.getMostUrgentActive().first() }
            } else {
                repo.getMostUrgentActive().first()
            }
        } else {
            try { repo.getMostUrgentActive().first() } catch (_: Exception) { null }
        }

        provideContent {
            WidgetContent(
                title = displayLink?.title,
                timeUntilExpiry = displayLink?.timeUntilExpiry,
                domain = displayLink?.domain,
                tags = displayLink?.tags
                    ?.filter { !it.startsWith("auto:") && !it.startsWith("llm:") }
                    ?.take(3) ?: emptyList(),
                linkId = displayLink?.id
            )
        }
    }

    @Composable
    private fun WidgetContent(
        title: String?,
        timeUntilExpiry: String?,
        domain: String?,
        tags: List<String>,
        linkId: String?
    ) {
        val context = LocalContext.current
        // Build an intent that carries the link ID so MainActivity can expand it directly
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            if (linkId != null) putExtra(MainActivity.EXTRA_OPEN_LINK_ID, linkId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = actionStartActivity(tapIntent))
                .padding(12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Text(
                    text = "Pockets",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF00897B)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))

                if (title != null) {
                    Text(
                        text = title,
                        maxLines = 3,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )

                    // Tags row (if any)
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = tags.joinToString("  ") { "#$it" },
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF00897B).copy(alpha = 0.85f)),
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.Bottom
                    ) {
                        Text(
                            text = domain ?: "",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF607D8B)),
                                fontSize = 10.sp
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = timeUntilExpiry ?: "",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFF9A825)),
                                fontSize = 10.sp
                            )
                        )
                    }
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "Inbox clear",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF388E3C)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }

    companion object {
        /** Triggers a data refresh on every widget instance. Call after inbox mutations. */
        suspend fun updateAll(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(ZenDeckWidget::class.java)
                ids.forEach { ZenDeckWidget().update(context, it) }
            } catch (_: Exception) { }
        }
    }
}
