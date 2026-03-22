package com.zendeck.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkCard(
    link: LinkItem,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val urgencyColor by animateColorAsState(
        targetValue = urgencyBorderColor(link.urgencyFraction),
        animationSpec = tween(durationMillis = 800),
        label = "urgency_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isExpanded) 1.5.dp else 1.dp,
                color = if (isExpanded) urgencyColor else urgencyColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Header: favicon · domain · pin · TTL badge ──────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = link.faviconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = link.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (link.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = AccentTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                TTLBadge(link.urgencyFraction, link.timeUntilExpiry)
            }

            Spacer(Modifier.height(8.dp))

            // ── Title ────────────────────────────────────────────────────────
            Text(
                text = link.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // ── Description (always visible) ─────────────────────────────────
            if (link.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = link.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = if (isExpanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── Tags (collapsed, shown below description) ─────────────────────
            if (!isExpanded && link.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    link.tags.take(3).forEach { TagChip(it) }
                }
            }

            // ── Expanded section ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))

                    val isXSite = link.domain == "x.com" || link.domain == "twitter.com"

                    when {
                        isXSite -> {
                            // X/Twitter can't be scraped — prompt to tag instead
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
                                    color = SlateGrayLight,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "No summary available",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDisabled
                            )
                        }
                    }

                    if (link.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            link.tags.take(3).forEach { TagChip(it) }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Tap again to open  →",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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

fun urgencyBorderColor(fraction: Float): Color = when {
    fraction < 0.5f -> UrgencyFresh
    fraction < 0.8f -> UrgencyWarning
    else -> UrgencyCritical
}
