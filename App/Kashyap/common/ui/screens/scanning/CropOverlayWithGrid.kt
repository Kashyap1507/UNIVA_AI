package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Crop overlay with rounded corner handles, white highlight, and center marker
 */
@Composable
fun CropOverlayWithGrid(
    topLeft: Offset,
    topRight: Offset,
    bottomLeft: Offset,
    bottomRight: Offset,
    onTopLeftDrag: (Offset) -> Unit,
    onTopRightDrag: (Offset) -> Unit,
    onBottomLeftDrag: (Offset) -> Unit,
    onBottomRightDrag: (Offset) -> Unit,
    imageWidth: Float,
    imageHeight: Float,
    showDarkOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Convert normalized coordinates to pixel coordinates
        val topLeftPx = Offset(topLeft.x * imageWidth, topLeft.y * imageHeight)
        val topRightPx = Offset(topRight.x * imageWidth, topRight.y * imageHeight)
        val bottomLeftPx = Offset(bottomLeft.x * imageWidth, bottomLeft.y * imageHeight)
        val bottomRightPx = Offset(bottomRight.x * imageWidth, bottomRight.y * imageHeight)

        // Calculate center point
        val centerX = (topLeftPx.x + topRightPx.x + bottomLeftPx.x + bottomRightPx.x) / 4f
        val centerY = (topLeftPx.y + topRightPx.y + bottomLeftPx.y + bottomRightPx.y) / 4f

        // Draw crop boundary and overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Always darken area outside crop bounds - no highlight inside for clear photo view
            // Create a path for the entire screen
            val screenPath =
                androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                }

            // Create a path for the crop area
            val cropPath =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(topLeftPx.x, topLeftPx.y)
                    lineTo(topRightPx.x, topRightPx.y)
                    lineTo(bottomRightPx.x, bottomRightPx.y)
                    lineTo(bottomLeftPx.x, bottomLeftPx.y)
                    close()
                }

            // Subtract crop area from screen to get darkened region
            val overlayPath =
                androidx.compose.ui.graphics.Path.combine(
                    operation = androidx.compose.ui.graphics.PathOperation.Difference,
                    path1 = screenPath,
                    path2 = cropPath,
                )

            // Draw the darkened overlay (everything except crop area)
            drawPath(
                path = overlayPath,
                color = Color.Black.copy(alpha = 0.6f),
            )

            // Draw rounded corner brackets (L-shaped corners)
            val strokeWidth = 3.dp.toPx()
            val cornerLength = 40.dp.toPx()
            val cornerRadius = 8.dp.toPx()

            // Top-left corner
            drawRoundedCornerBracket(
                center = topLeftPx,
                length = cornerLength,
                strokeWidth = strokeWidth,
                radius = cornerRadius,
                isTopLeft = true,
            )

            // Top-right corner
            drawRoundedCornerBracket(
                center = topRightPx,
                length = cornerLength,
                strokeWidth = strokeWidth,
                radius = cornerRadius,
                isTopRight = true,
            )

            // Bottom-left corner
            drawRoundedCornerBracket(
                center = bottomLeftPx,
                length = cornerLength,
                strokeWidth = strokeWidth,
                radius = cornerRadius,
                isBottomLeft = true,
            )

            // Bottom-right corner
            drawRoundedCornerBracket(
                center = bottomRightPx,
                length = cornerLength,
                strokeWidth = strokeWidth,
                radius = cornerRadius,
                isBottomRight = true,
            )

            // Draw center + marker
            val markerSize = 20.dp.toPx()
            val markerStrokeWidth = 2.dp.toPx()

            // Horizontal line
            drawLine(
                color = Color.White,
                start = Offset(centerX - markerSize / 2, centerY),
                end = Offset(centerX + markerSize / 2, centerY),
                strokeWidth = markerStrokeWidth,
                cap = StrokeCap.Round,
            )

            // Vertical line
            drawLine(
                color = Color.White,
                start = Offset(centerX, centerY - markerSize / 2),
                end = Offset(centerX, centerY + markerSize / 2),
                strokeWidth = markerStrokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // Draggable corner handles - pass normalized positions
        DraggableCornerHandle(
            normalizedPosition = topLeft,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onDrag = onTopLeftDrag,
            cornerLabel = "Top left",
        )

        DraggableCornerHandle(
            normalizedPosition = topRight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onDrag = onTopRightDrag,
            cornerLabel = "Top right",
        )

        DraggableCornerHandle(
            normalizedPosition = bottomLeft,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onDrag = onBottomLeftDrag,
            cornerLabel = "Bottom left",
        )

        DraggableCornerHandle(
            normalizedPosition = bottomRight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onDrag = onBottomRightDrag,
            cornerLabel = "Bottom right",
        )
    }
}

/**
 * Draggable corner handle - invisible touch target (no visual indicator)
 * Works with normalized coordinates (0-1 range) for smooth dragging
 */
@Composable
private fun DraggableCornerHandle(
    normalizedPosition: Offset,
    imageWidth: Float,
    imageHeight: Float,
    onDrag: (Offset) -> Unit,
    cornerLabel: String,
    modifier: Modifier = Modifier,
) {
    val handleSize = 80.dp // Larger touch target for easier dragging
    val density = LocalDensity.current
    val handleSizePx = with(density) { handleSize.toPx() }

    // Track cumulative drag offset during gesture
    val dragOffset = remember { mutableStateOf(Offset.Zero) }
    val isDragging = remember { mutableStateOf(false) }

    // Calculate current pixel position (either base position or base + drag offset)
    val currentPosition =
        if (isDragging.value) {
            val basePx =
                Offset(
                    normalizedPosition.x * imageWidth,
                    normalizedPosition.y * imageHeight,
                )
            basePx + dragOffset.value
        } else {
            Offset(
                normalizedPosition.x * imageWidth,
                normalizedPosition.y * imageHeight,
            )
        }

    Box(
        modifier =
            modifier
                .offset {
                    IntOffset(
                        x = (currentPosition.x - handleSizePx / 2).toInt(),
                        y = (currentPosition.y - handleSizePx / 2).toInt(),
                    )
                }
                .size(handleSize)
                .semantics {
                    contentDescription = "$cornerLabel corner handle. Drag to adjust document boundary"
                    role = Role.Button
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging.value = true
                            dragOffset.value = Offset.Zero
                        },
                        onDragEnd = {
                            isDragging.value = false
                            dragOffset.value = Offset.Zero
                        },
                        onDragCancel = {
                            isDragging.value = false
                            dragOffset.value = Offset.Zero
                        },
                    ) { change, delta ->
                        change.consume()

                        // Accumulate drag offset
                        dragOffset.value += delta

                        // Calculate new position in pixels
                        val basePx =
                            Offset(
                                normalizedPosition.x * imageWidth,
                                normalizedPosition.y * imageHeight,
                            )
                        val newPx = basePx + dragOffset.value

                        // Convert to normalized coordinates and clamp
                        val newNormalized =
                            Offset(
                                (newPx.x / imageWidth).coerceIn(0f, 1f),
                                (newPx.y / imageHeight).coerceIn(0f, 1f),
                            )

                        // Update state
                        onDrag(newNormalized)
                    }
                },
    ) {
        // No visual indicator - corners themselves are the visual cue
    }
}

/**
 * Extension function to draw rounded corner bracket
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedCornerBracket(
    center: Offset,
    length: Float,
    strokeWidth: Float,
    radius: Float,
    isTopLeft: Boolean = false,
    isTopRight: Boolean = false,
    isBottomLeft: Boolean = false,
    isBottomRight: Boolean = false,
) {
    val path = androidx.compose.ui.graphics.Path()

    when {
        isTopLeft -> {
            // Horizontal line (pointing right)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x + length, center.y)
            // Arc at corner
            path.moveTo(center.x, center.y)
            // Vertical line (pointing down)
            path.lineTo(center.x, center.y + length)
        }
        isTopRight -> {
            // Horizontal line (pointing left)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x - length, center.y)
            // Vertical line (pointing down)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x, center.y + length)
        }
        isBottomLeft -> {
            // Horizontal line (pointing right)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x + length, center.y)
            // Vertical line (pointing up)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x, center.y - length)
        }
        isBottomRight -> {
            // Horizontal line (pointing left)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x - length, center.y)
            // Vertical line (pointing up)
            path.moveTo(center.x, center.y)
            path.lineTo(center.x, center.y - length)
        }
    }

    drawPath(
        path = path,
        color = Color.White,
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
            ),
    )
}
