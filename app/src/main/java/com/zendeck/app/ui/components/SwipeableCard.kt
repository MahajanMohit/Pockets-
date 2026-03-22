package com.zendeck.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ArchiveColor = Color(0xFFF59E0B)   // amber — swipe to archive
private val RestoreColor = Color(0xFF10B981)   // teal — swipe to restore

/**
 * Wraps [content] with a horizontal swipe-to-dismiss gesture.
 *
 * @param onSwipeToArchive Called when the user swipes left (EndToStart) — archive the item.
 *                         Pass null to disable this direction.
 * @param onSwipeToRestore Called when the user swipes right (StartToEnd) — restore the item.
 *                         Pass null to disable this direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCard(
    onSwipeToArchive: (() -> Unit)? = null,
    onSwipeToRestore: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onSwipeToArchive != null
                SwipeToDismissBoxValue.StartToEnd -> onSwipeToRestore != null
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> onSwipeToArchive?.invoke()
            SwipeToDismissBoxValue.StartToEnd -> onSwipeToRestore?.invoke()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = onSwipeToRestore != null,
        enableDismissFromEndToStart = onSwipeToArchive != null,
        backgroundContent = {
            val direction = state.dismissDirection
            val isArchiving = direction == SwipeToDismissBoxValue.EndToStart
            val isRestoring = direction == SwipeToDismissBoxValue.StartToEnd

            val targetColor = when {
                isArchiving -> ArchiveColor.copy(alpha = 0.85f)
                isRestoring -> RestoreColor.copy(alpha = 0.85f)
                else -> Color.Transparent
            }
            val bgColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(150),
                label = "swipe_bg"
            )
            val scale by animateFloatAsState(
                targetValue = if (direction == SwipeToDismissBoxValue.Settled) 0.8f else 1f,
                animationSpec = tween(150),
                label = "swipe_icon_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isArchiving) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                when {
                    isArchiving -> Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = "Archive",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).scale(scale)
                    )
                    isRestoring -> Icon(
                        imageVector = Icons.Default.Unarchive,
                        contentDescription = "Restore to inbox",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).scale(scale)
                    )
                }
            }
        }
    ) {
        content()
    }
}
