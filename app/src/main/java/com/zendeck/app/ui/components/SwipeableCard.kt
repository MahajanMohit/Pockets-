package com.zendeck.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.ui.theme.SwipeGreenBackground
import com.zendeck.app.ui.theme.SwipeRedBackground
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableCard(
    link: LinkItem,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showSummary: Boolean = true
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val swipeThreshold = 180f
    var actionTriggered by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (actionTriggered) 0f else offsetX,
        label = "swipe_offset"
    )

    val backgroundAlpha = (offsetX.absoluteValue / swipeThreshold).coerceIn(0f, 1f)
    val isSwipingRight = offsetX > 0
    val bgColor by animateColorAsState(
        targetValue = when {
            offsetX > 10f -> SwipeGreenBackground.copy(alpha = backgroundAlpha)
            offsetX < -10f -> SwipeRedBackground.copy(alpha = backgroundAlpha)
            else -> Color.Transparent
        },
        label = "bg_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Reveal background
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End
        ) {
            if (isSwipingRight && offsetX > 10f) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Open", color = Color.White, fontSize = 13.sp)
            } else if (!isSwipingRight && offsetX < -10f) {
                Text("Archive", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color.White)
            }
        }

        // Swipeable + long-pressable card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .pointerInput(link.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX > swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionTriggered = true
                                    onOpen()
                                }
                                offsetX < -swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionTriggered = true
                                    onArchive()
                                }
                            }
                            offsetX = 0f
                            actionTriggered = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount)
                                .coerceIn(-swipeThreshold * 1.5f, swipeThreshold * 1.5f)
                        }
                    )
                }
        ) {
            LinkCard(
                link = link,
                modifier = Modifier.fillMaxWidth(),
                showSummary = showSummary
            )
        }
    }
}
