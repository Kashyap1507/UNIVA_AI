package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.BackgroundPrimary

/**
 * Custom Bottom Sheet that slides up from behind the bottom navigation bar.
 *
 * Unlike Material 3 ModalBottomSheet, this component allows precise z-index control,
 * enabling the bottom navigation bar to remain visible on top.
 *
 * Features:
 * - Slide-in/out animations from nav bar position
 * - Drag handle for swipe-to-dismiss
 * - Drag gesture detection
 * - Customizable content
 *
 * @param visible Whether the sheet is visible
 * @param onDismiss Callback when the sheet should be dismissed (drag down or scrim tap)
 * @param bottomOffset Offset from bottom of screen to start animation (usually nav bar height)
 * @param modifier Modifier for the sheet container
 * @param content The content to display in the sheet
 */
@Composable
fun CustomBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bottomOffset: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Track drag offset for dismiss gesture
    var dragOffset by remember { mutableStateOf(0f) }
    val dismissThreshold = 200f // Drag down 200dp to dismiss

    // Check dark theme using MaterialTheme background luminance for reliability
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradientColors = if (isDarkTheme) {
        listOf(
            Color(0xFF1C1C1E),    // Dark gray at top
            Color(0xFF141414)     // Darker gray at bottom
        )
    } else {
        listOf(
            BackgroundPrimary,    // #F7F7F7 - same as home screen top
            Color(0xFFEDEDED)     // Silver grey at bottom
        )
    }

    // Calculate animation start position accounting for bottom nav bar
    // Sheet should slide in from bottom nav bar position, not screen bottom
    val bottomOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { bottomOffset.toPx() }

    AnimatedVisibility(
        visible = visible,
        enter =
            slideInVertically(
                initialOffsetY = { fullHeight -> (fullHeight - bottomOffsetPx).toInt() },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ),
        exit =
            slideOutVertically(
                targetOffsetY = { fullHeight -> (fullHeight - bottomOffsetPx).toInt() },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            ),
        modifier =
            modifier
                .padding(horizontal = 12.dp) // Match nav bar horizontal margins
                .padding(bottom = 48.dp), // Increased gap to prevent visibility below tab bar
    ) {

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > dismissThreshold) {
                                    onDismiss()
                                }
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                // Only allow dragging downward (positive values)
                                if (dragAmount > 0) {
                                    dragOffset += dragAmount
                                }
                            },
                        )
                    }
                    .offset(y = if (dragOffset > 0f) (dragOffset / 2).dp else 0.dp),
            // Resistance effect
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), // Match nav bar corner radius
            color = Color.Transparent, // Use gradient background instead
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(gradientColors),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                ) {
                    // Drag Handle
                    DragHandle()

                    // Content
                    content()
                }
            }
        }
    }
}

/**
 * Material 3 compliant drag handle for bottom sheet.
 *
 * Visual indicator that the sheet can be dragged to dismiss.
 */
@Composable
private fun DragHandle() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .width(32.dp)
                    .height(4.dp),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ) {}
    }
}
