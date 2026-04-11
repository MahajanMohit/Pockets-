package com.zendeck.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.*
import java.io.File

@Composable
fun LinkCard(
    link: LinkItem,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val c = LocalZenDeckColors.current
    val latestOnTap = remember { androidx.compose.runtime.mutableStateOf(onTap) }
    val latestOnDoubleTap = remember { androidx.compose.runtime.mutableStateOf(onDoubleTap) }
    val latestOnLongPress = remember { androidx.compose.runtime.mutableStateOf(onLongPress) }
    latestOnTap.value = onTap
    latestOnDoubleTap.value = onDoubleTap
    latestOnLongPress.value = onLongPress
    val urgencyColor by animateColorAsState(
        targetValue = urgencyBorderColor(link.urgencyFraction),
        animationSpec = tween(durationMillis = 800),
        label = "urgency_color"
    )

    val isTextCard = link.contentType == "text"
    val hasImage = link.contentType == "image" && link.localImagePath.isNotBlank()

    // For TEXT cards, wrap card in a Box with a left accent bar
    Box(modifier = modifier.fillMaxWidth()) {
        if (isTextCard) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .matchParentSize()
                    .background(AccentTeal.copy(alpha = 0.6f))
                    .align(Alignment.CenterStart)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTextCard) Modifier.padding(start = 3.dp) else Modifier)
                .border(
                    width = if (isExpanded) 1.5.dp else 1.dp,
                    color = if (isExpanded) urgencyColor else urgencyColor.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { latestOnTap.value() },
                        onDoubleTap = { latestOnDoubleTap.value() },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestOnLongPress.value()
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = c.cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── IMAGE thumbnail ────────────────────────────────────────────
                if (hasImage) {
                    val imageHeight by animateDpAsState(
                        targetValue = if (isExpanded) 180.dp else 72.dp,
                        animationSpec = tween(300),
                        label = "img_h"
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(imageHeight)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(link.localImagePath))
                                .crossfade(200)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .clickable {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            File(link.localImagePath)
                                        )
                                        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "image/*")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(viewIntent)
                                    } catch (_: Exception) { }
                                },
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "📷",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // ── Card body ─────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // ── Header: icon · domain · pin · TTL badge ────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTextCard) {
                            Icon(
                                Icons.Outlined.TextSnippet,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Note",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentTeal,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            AsyncImage(
                                model = link.faviconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).clip(CircleShape),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = link.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (link.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = AccentTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        val archiveCountdown = link.timeUntilArchiveDeletion
                        if (archiveCountdown != null) {
                            DeletionBadge(archiveCountdown)
                        } else {
                            TTLBadge(link.urgencyFraction, link.timeUntilExpiry)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Title ──────────────────────────────────────────────────
                    Text(
                        text = link.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // ── Description ────────────────────────────────────────────
                    if (link.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = link.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = if (isExpanded) 4 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ── Summary status (all types: show while pending or unavailable) ──
                    if (link.summaryStatus == "pending" || link.summaryStatus == "unavailable") {
                        Spacer(Modifier.height(6.dp))
                        SummaryStatusChip(link.summaryStatus)
                    }

                    // ── Tags (collapsed) ───────────────────────────────────────
                    val visibleTags = link.tags.filter { !it.startsWith("ai:") && !it.startsWith("llm:") }
                    if (!isExpanded && visibleTags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            visibleTags.take(3).forEach { TagChip(it) }
                        }
                    }

                    // ── Expanded section ───────────────────────────────────────
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        ) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                    ) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                            Spacer(Modifier.height(8.dp))

                            val isXSite = link.domain == "x.com" || link.domain == "twitter.com"

                            when {
                                isXSite && link.tags.isEmpty() -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Label,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Add a tag to remember what this post is about",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentTeal
                                        )
                                    }
                                }
                                link.summaryBullets.isNotEmpty() -> {
                                    link.summaryBullets.forEach { bullet ->
                                        Text(
                                            text = bullet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.slateGrayLight,
                                            modifier = Modifier.padding(bottom = 3.dp)
                                        )
                                    }
                                    val llmAttribution = link.tags.firstOrNull { it.startsWith("llm:") }?.removePrefix("llm:")
                                    if (llmAttribution != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Summarised using $llmAttribution",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = c.textDisabled,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                                else -> {
                                    val fallbackText = when {
                                        link.contentType != "link" && link.summaryStatus == "pending" -> "Generating summary..."
                                        link.contentType != "link" && link.summaryStatus == "unavailable" -> "Couldn't generate a summary"
                                        else -> "No summary available"
                                    }
                                    Text(
                                        text = fallbackText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textDisabled
                                    )
                                }
                            }

                            if (visibleTags.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    visibleTags.take(3).forEach { TagChip(it) }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Double-tap to open  →",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textDisabled,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionBadge(countdown: String) {
    val c = LocalZenDeckColors.current
    Surface(shape = RoundedCornerShape(4.dp), color = c.textDisabled.copy(alpha = 0.12f)) {
        Text(
            text = countdown,
            style = MaterialTheme.typography.labelSmall,
            color = c.textDisabled,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TTLBadge(urgencyFraction: Float, timeUntilExpiry: String) {
    val color = urgencyBorderColor(urgencyFraction)
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text = timeUntilExpiry,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = AccentTeal.copy(alpha = 0.12f)) {
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelSmall,
            color = AccentTeal,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SummaryStatusChip(status: String) {
    val c = LocalZenDeckColors.current
    when (status) {
        "pending" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = AccentTeal,
                    strokeWidth = 2.dp,
                    trackColor = AccentTeal.copy(alpha = 0.15f)
                )
                Text(
                    text = "Summarising…",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentTeal
                )
            }
        }
        "unavailable" -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = c.textDisabled.copy(alpha = 0.10f)
            ) {
                Text(
                    text = "Summary unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textDisabled,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        else -> {} // "done" — nothing shown
    }
}

fun urgencyBorderColor(fraction: Float): Color = when {
    fraction < 0.5f -> UrgencyFresh
    fraction < 0.8f -> UrgencyWarning
    else -> UrgencyCritical
}
