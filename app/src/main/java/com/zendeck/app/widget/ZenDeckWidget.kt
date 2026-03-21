package com.zendeck.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.zendeck.app.MainActivity
import com.zendeck.app.data.repository.LinkRepository
import kotlinx.coroutines.flow.first

class ZenDeckWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = LinkRepository.getInstance(context)
        val urgentLink = try {
            repo.getMostUrgentActive().first()
        } catch (e: Exception) {
            null
        }

        provideContent {
            WidgetContent(urgentLink?.title, urgentLink?.timeUntilExpiry, urgentLink?.domain)
        }
    }

    @Composable
    private fun WidgetContent(title: String?, timeUntilExpiry: String?, domain: String?) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Text(
                    text = "ZenDeck",
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
                        text = "Inbox clear ✓",
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
}
