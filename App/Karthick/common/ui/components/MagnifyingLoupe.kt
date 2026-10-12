package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Magnifying loupe that renders magnified preview content above the touch position.
 *
 * @param touchPosition Touch position in view coordinates (pixels) - used for both positioning and magnification.
 * @param viewSize Size of the view containing the content (pixels).
 * @param loupeScale Magnification factor applied to the preview content.
 * @param modifier Optional modifier for outer container.
 * @param loupeDiameter Diameter of the circular loupe.
 * @param verticalOffset Distance above the touch point where the loupe should appear.
 * @param content Composable that renders the preview content to magnify (should use ContentScale.Fit like main view).
 */
@Composable
fun MagnifyingLoupe(
    touchPosition: Offset,
    viewSize: IntSize,
    loupeScale: Float,
    modifier: Modifier = Modifier,
    loupeDiameter: Dp = 120.dp,
    verticalOffset: Dp = 72.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    if (viewSize.width <= 0 || viewSize.height <= 0) return

    val density = LocalDensity.current

    val loupeDiameterPx = with(density) { loupeDiameter.toPx() }
    val loupeRadiusPx = loupeDiameterPx / 2f
    val verticalOffsetPx = with(density) { verticalOffset.toPx() }

    // CRITICAL FIX: Use pixel size for content rendering to match coordinate system
    // Touch position is in PIXELS, so content box must also be sized in PIXELS
    val viewWidthPx = viewSize.width.toFloat()
    val viewHeightPx = viewSize.height.toFloat()

    // Position the loupe overlay in screen coordinates
    val desiredX = touchPosition.x - loupeRadiusPx
    val desiredY = touchPosition.y - loupeRadiusPx - verticalOffsetPx

    val maxX = kotlin.math.max(0f, viewWidthPx - loupeDiameterPx)
    val maxY = kotlin.math.max(0f, viewHeightPx - loupeDiameterPx)

    val clampedX = desiredX.coerceIn(0f, maxX)
    val clampedY = desiredY.coerceIn(0f, maxY)

    // Convert to DP for Compose (but keep pixel-based calculations for translation)
    val viewWidthDp = with(density) { viewWidthPx.toDp() }
    val viewHeightDp = with(density) { viewHeightPx.toDp() }

    // Calculate translation for magnification
    // The goal: center the touch point in the loupe
    // graphicsLayer transformations happen around the content's center point (transformOrigin = Center by default)
    // After scaling, we need to translate so the touch point ends up at the loupe center

    val densityValue = density.density

    // Convert touch position to DP (since graphicsLayer works in DP)
    val touchXDp = touchPosition.x / densityValue
    val touchYDp = touchPosition.y / densityValue

    // Content center in DP/px
    val contentCenterXDp = viewWidthDp.value / 2f
    val contentCenterYDp = viewHeightDp.value / 2f
    val contentCenterXPx = viewWidthPx / 2f
    val contentCenterYPx = viewHeightPx / 2f

    // Loupe center in DP/px (relative to content)
    val loupeCenterXPx = clampedX + loupeRadiusPx
    val loupeCenterYPx = clampedY + loupeRadiusPx
    val loupeCenterXDp = loupeCenterXPx / densityValue
    val loupeCenterYDp = loupeCenterYPx / densityValue

    // Translation needed to center the touch point in the loupe
    // After scaling around center, we need to move the content so:
    // (touchPoint - contentCenter) * scale + contentCenter + translation = loupeCenter
    // Therefore: translation = loupeCenter - (touchPoint - contentCenter) * scale - contentCenter
    val translationXPx = loupeCenterXPx - (touchPosition.x - contentCenterXPx) * loupeScale - contentCenterXPx
    val translationYPx = loupeCenterYPx - (touchPosition.y - contentCenterYPx) * loupeScale - contentCenterYPx
    val translationXDp = translationXPx / densityValue
    val translationYDp = translationYPx / densityValue

    // DEBUG LOGGING
    println("🔍 MagnifyingLoupe: ========== LOUPE DISPLAYED ==========")
    println(
        "🔍 MagnifyingLoupe: Touch position: (${touchPosition.x.toInt()}px, ${touchPosition.y.toInt()}px) = (${touchXDp.toInt()}dp, ${touchYDp.toInt()}dp)",
    )
    println(
        "🔍 MagnifyingLoupe: View size: ${viewSize.width}x${viewSize.height}px = ${viewWidthDp.value.toInt()}dp x ${viewHeightDp.value.toInt()}dp",
    )
    println(
        "🔍 MagnifyingLoupe: Loupe diameter: ${loupeDiameter.value}dp (${loupeDiameterPx.toInt()}px), radius=${loupeRadiusPx.toInt()}px",
    )
    println("🔍 MagnifyingLoupe: Loupe scale: ${loupeScale}x")
    println("🔍 MagnifyingLoupe: Density: $densityValue")
    println("🔍 MagnifyingLoupe: Content center: (${contentCenterXDp.toInt()}dp, ${contentCenterYDp.toInt()}dp)")
    println("🔍 MagnifyingLoupe: Loupe center: (${loupeCenterXDp.toInt()}dp, ${loupeCenterYDp.toInt()}dp)")
    println(
        "🔍 MagnifyingLoupe: Translation: X=${translationXDp.toInt()}dp (${translationXPx.toInt()}px), Y=${translationYDp.toInt()}dp (${translationYPx.toInt()}px)",
    )
    println("🔍 MagnifyingLoupe: Expected: Touch area should appear at center of loupe")
    println("🔍 MagnifyingLoupe: ==========================================")

    Box(
        modifier =
            modifier
                .offset {
                    IntOffset(
                        clampedX.roundToInt(),
                        clampedY.roundToInt(),
                    )
                }
                .size(loupeDiameter)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false,
                )
                .clip(CircleShape)
                .border(width = 3.dp, color = Color.White, shape = CircleShape)
                .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        Box(
            modifier =
                Modifier
                    .requiredSize(viewWidthDp, viewHeightDp)
                    .graphicsLayer {
                        scaleX = loupeScale
                        scaleY = loupeScale
                        // Translation calculated to center the touch point in the loupe
                        // Transformation happens around content center (default transformOrigin = Center)
                        this.translationX = translationXPx
                        this.translationY = translationYPx
                        clip = false
                    },
            content = content,
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val crosshairLength = 16f
            val crosshairThickness = 3f

            drawLine(
                color = Color.Cyan,
                start = Offset(center.x - crosshairLength, center.y),
                end = Offset(center.x + crosshairLength, center.y),
                strokeWidth = crosshairThickness,
            )
            drawLine(
                color = Color.Cyan,
                start = Offset(center.x, center.y - crosshairLength),
                end = Offset(center.x, center.y + crosshairLength),
                strokeWidth = crosshairThickness,
            )

            drawCircle(
                color = Color.Cyan,
                radius = 3f,
                center = center,
            )

            drawCircle(
                color = Color.Cyan.copy(alpha = 0.35f),
                radius = size.width / 2f - 6f,
                center = center,
                style = Stroke(width = 2f),
            )
        }
    }
}
