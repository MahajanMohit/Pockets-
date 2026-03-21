package com.zendeck.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.*

@Composable
fun LinkCard(
    link: LinkItem,
    modifier: Modifier = Modifier,
    showSummary: Boolean = true
) {
    val urgencyColor by animateColorAsState(
        targetValue = urgencyBorderColor(link.urgencyFraction),
        animationSpec = tween(durationMillis = 800),
        label = "urgency_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = urgencyColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header row: favicon + domain + pin icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = link.faviconUrl,
                    contentDescription = "Favicon",
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = link.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (link.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = AccentTeal,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TTLBadge(
                    urgencyFraction = link.urgencyFraction,
                    timeUntilExpiry = link.timeUntilExpiry
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = link.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            if (link.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = link.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // AI Summary bullets
            if (showSummary && link.summaryBullets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                link.summaryBullets.forEach { bullet ->
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGrayLight,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            // Tags
            if (link.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    link.tags.take(3).forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun TTLBadge(urgencyFraction: Float, timeUntilExpiry: String) {
    val color = urgencyBorderColor(urgencyFraction)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
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
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = AccentTeal.copy(alpha = 0.12f)
    ) {
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
