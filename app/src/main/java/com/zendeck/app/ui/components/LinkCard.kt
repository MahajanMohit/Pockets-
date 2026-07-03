package com.zendeck.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    // rememberUpdatedState: pointerInput(Unit) never restarts, so it must read
    // the latest lambdas without state writes during composition
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // Direct colour — urgency tier changes at most a few times a day; keeping a
    // live animation object per visible card costs frames during scroll
    val urgencyColor = urgencyBorderColor(link.urgencyFraction)

    // Tactile press feedback: card compresses under the finger and springs
    // back on release. Scale is read inside graphicsLayer (draw phase only),
    // so the animation never triggers recomposition or relayout.
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "press_scale"
    )

    val isTextCard = link.contentType == "text"
    val hasImage = link.contentType == "image" && link.localImagePath.isNotBlank()

    // For TEXT cards, wrap card in a Box with a left accent bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
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
                    color = if (isExpanded) urgencyColor else urgencyColor.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { currentOnTap() },
                        onDoubleTap = { currentOnDoubleTap() },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnLongPress()
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = c.cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── IMAGE thumbnail ────────────────────────────────────────────
                if (hasImage) {
                    val imageHeight by animateDpAsState(
                        targetValue = if (isExpanded) 180.dp else 72.dp,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
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
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                        .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                // Decode at display size — favicons/ICOs can be 256px+
                                model = ImageRequest.Builder(context)
                                    .data(link.faviconUrl)
                                    .size(48)
                                    .build(),
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
                    val visibleTags = link.tags.filter {
                        !it.startsWith("ai:") && !it.startsWith("llm:") && !it.startsWith("auto:")
                    }
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
                            spring(dampingRatio = 0.85f, stiffness = 420f)
                        ) + fadeIn(spring(stiffness = 700f)),
                        exit = shrinkVertically(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 550f)
                        ) + fadeOut(spring(stiffness = 1200f))
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
    Surface(shape = RoundedCornerShape(50), color = c.textDisabled.copy(alpha = 0.12f)) {
        Text(
            text = countdown,
            style = MaterialTheme.typography.labelSmall,
            color = c.textDisabled,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TTLBadge(urgencyFraction: Float, timeUntilExpiry: String) {
    val color = urgencyBorderColor(urgencyFraction)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text = timeUntilExpiry,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(shape = RoundedCornerShape(50), color = AccentTeal.copy(alpha = 0.12f)) {
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelSmall,
            color = AccentTeal,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
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
                shape = RoundedCornerShape(50),
                color = c.textDisabled.copy(alpha = 0.10f)
            ) {
                Text(
                    text = "Summary unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textDisabled,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
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
