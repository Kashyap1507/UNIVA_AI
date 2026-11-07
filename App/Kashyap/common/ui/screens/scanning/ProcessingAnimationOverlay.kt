package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt

/**
 * CARD-BASED PROCESSING ANIMATION OVERLAY
 *
 * Key Concept: All animation happens within a fixed card positioned at the center
 * of the screen, matching DocumentViewerScreen's PhotoViewer card dimensions.
 *
 * Animation Flow:
 * 1. Load cropped photo in quadrilateral form into card (fade in)
 * 2. Straighten quadrilateral → rectangle within card
 * 3. Dim background outside card
 * 4. Progressive magic brush reveals enhanced photo (dual-layer)
 * 5. Navigation transition (card stays in place, DocumentViewerScreen fades in underneath)
 */
@Composable
fun ProcessingAnimationOverlay(
    processingState: ProcessingAnimationState,
    cropBounds: CropBounds,
    originalImagePath: String,
    enhancedImagePath: String?,
    imageWidth: Float,
    imageHeight: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background dim overlay (outside card area) - NOW STARTS AT PHASE 1
        if (processingState.currentPhase >= ProcessingPhase.LOADING_PHOTO_QUAD) {
            DimBackgroundOverlay(
                dimAlpha = processingState.backgroundDimAlpha,
                sparkles = processingState.sparkleParticles,
            )
        }

        // Central animation card (matches DocumentViewerScreen's PhotoViewer dimensions)
        // Positioned to match DocumentViewerScreen card location
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            // Header space matching DocumentViewerScreen (header + page count + carousel + divider + spacer)
            Spacer(modifier = Modifier.height(230.dp))

            // Card positioned to match PhotoViewer in DocumentViewerScreen
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
            ) {
                AnimationCard(
                    processingState = processingState,
                    cropBounds = cropBounds,
                    originalImagePath = originalImagePath,
                    enhancedImagePath = enhancedImagePath,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                )
            }
        }
    }
}

/**
 * Background dim overlay - darkens everything outside the card
 */
@Composable
private fun DimBackgroundOverlay(
    dimAlpha: Float,
    sparkles: List<SparkleParticle>,
    modifier: Modifier = Modifier,
) {
    val animatedDimAlpha by animateFloatAsState(
        targetValue = dimAlpha,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dimAlpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Dark translucent overlay
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedDimAlpha)),
        )

        // Sparkle particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            sparkles.forEach { particle ->
                drawSparkle(particle)
            }
        }
    }
}

/**
 * Draw individual sparkle particle
 */
private fun DrawScope.drawSparkle(particle: SparkleParticle) {
    val radius = particle.size * particle.scale

    // Outer glow
    drawCircle(
        color = particle.color.copy(alpha = particle.alpha * 0.3f),
        radius = radius * 2f,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    // Main sparkle
    drawCircle(
        color = particle.color.copy(alpha = particle.alpha),
        radius = radius,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    // Bright center
    drawCircle(
        color = Color.White.copy(alpha = particle.alpha * 0.8f),
        radius = radius * 0.4f,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    // Cross sparkle effect
    val lineLength = radius * 1.5f
    val lineWidth = radius * 0.2f

    // Horizontal line
    drawLine(
        color = particle.color.copy(alpha = particle.alpha * 0.6f),
        start = Offset(particle.position.x - lineLength, particle.position.y),
        end = Offset(particle.position.x + lineLength, particle.position.y),
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Screen,
    )

    // Vertical line
    drawLine(
        color = particle.color.copy(alpha = particle.alpha * 0.6f),
        start = Offset(particle.position.x, particle.position.y - lineLength),
        end = Offset(particle.position.x, particle.position.y + lineLength),
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Screen,
    )
}

/**
 * Central animation card - all processing animation happens within this card
 * Card dimensions and position match DocumentViewerScreen's PhotoViewer card
 */
@Composable
private fun AnimationCard(
    processingState: ProcessingAnimationState,
    cropBounds: CropBounds,
    originalImagePath: String,
    enhancedImagePath: String?,
    imageWidth: Float,
    imageHeight: Float,
    modifier: Modifier = Modifier,
) {
    // Animate photo fade-in (Phase 1)
    val photoAlpha by animateFloatAsState(
        targetValue = processingState.photoAlpha,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "photoAlpha",
    )

    // Animate straightening progress (Phase 2)
    val straighteningProgress by animateFloatAsState(
        targetValue = processingState.straighteningProgress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "straighteningProgress",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f) // Match typical document aspect ratio
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Phase 1-2: Original cropped photo (quadrilateral → rectangle)
            if (processingState.currentPhase >= ProcessingPhase.LOADING_PHOTO_QUAD &&
                processingState.currentPhase < ProcessingPhase.MAGIC_BRUSH_APPLYING
            ) {
                PhotoWithPerspectiveTransform(
                    imagePath = originalImagePath,
                    alpha = photoAlpha,
                    straighteningProgress = straighteningProgress,
                    cropBounds = cropBounds,
                )
            }

            // Phase 5: Dual-layer magic brush reveal
            if (processingState.currentPhase >= ProcessingPhase.MAGIC_BRUSH_APPLYING && enhancedImagePath != null) {
                DualLayerMagicBrush(
                    originalImagePath = originalImagePath,
                    enhancedImagePath = enhancedImagePath,
                    revealProgress = processingState.enhancementRevealProgress,
                )
            }
        }
    }
}

/**
 * Photo with perspective transform animation (quadrilateral → rectangle)
 * NOTE: The imagePath is already the cropped (straightened) image, but we apply
 * reverse perspective transform initially and animate to straightened state
 * for visual effect
 */
@Composable
private fun PhotoWithPerspectiveTransform(
    imagePath: String,
    alpha: Float,
    straighteningProgress: Float,
    cropBounds: CropBounds,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha

                    // Apply REVERSE perspective transform that animates to normal
                    // At progress = 0.0: Show skewed (simulating quadrilateral)
                    // At progress = 1.0: Show normal (rectangle)
                    // This creates the illusion of straightening even though image is already cropped

                    val skewAmount = (1f - straighteningProgress) * 0.15f
                    transformOrigin = TransformOrigin.Center

                    // Simulate unstraightening effect with 3D rotation
                    rotationX = skewAmount * 8f // Pitch
                    rotationY = skewAmount * -6f // Yaw
                    scaleX = 1f - (skewAmount * 0.08f)
                    scaleY = 1f - (skewAmount * 0.08f)
                },
    ) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(if (imagePath.startsWith("file://")) imagePath else "file://$imagePath")
                    .crossfade(true)
                    .build(),
            contentDescription = "Cropped photo (straightening animation)",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Dual-layer magic brush reveal animation
 * Base layer: Original photo (always visible)
 * Top layer: Enhanced photo revealed progressively from top to bottom
 */
@Composable
private fun DualLayerMagicBrush(
    originalImagePath: String,
    enhancedImagePath: String,
    revealProgress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedRevealProgress by animateFloatAsState(
        targetValue = revealProgress,
        animationSpec = tween(2200, easing = LinearEasing),
        label = "revealProgress",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Base layer: Original photo (always visible as background)
        AsyncImage(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(if (originalImagePath.startsWith("file://")) originalImagePath else "file://$originalImagePath")
                    .crossfade(false) // No crossfade for base layer
                    .build(),
            contentDescription = "Original photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Top layer: Enhanced photo revealed progressively
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape =
                            object : Shape {
                                override fun createOutline(
                                    size: Size,
                                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                    density: androidx.compose.ui.unit.Density,
                                ): Outline {
                                    return Outline.Rectangle(
                                        Rect(
                                            left = 0f,
                                            top = 0f,
                                            right = size.width,
                                            bottom = size.height * animatedRevealProgress,
                                        ),
                                    )
                                }
                            }
                    },
        ) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(if (enhancedImagePath.startsWith("file://")) enhancedImagePath else "file://$enhancedImagePath")
                        .crossfade(false)
                        .build(),
                contentDescription = "Enhanced photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Magic brush indicator line with glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val yPosition = size.height * animatedRevealProgress

            // Don't draw brush at position 0 (before animation starts)
            if (animatedRevealProgress > 0f && animatedRevealProgress < 1f) {
                // Glow effect
                drawLine(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color(0xFFFFD700).copy(alpha = 0.8f),
                                    Color.Transparent,
                                ),
                        ),
                    start = Offset(0f, yPosition - 20f),
                    end = Offset(size.width, yPosition - 20f),
                    strokeWidth = 40f,
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Screen,
                )

                // Main brush line
                drawLine(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.3f),
                                    Color(0xFFFFD700),
                                    Color(0xFFFFD700),
                                    Color(0xFFFFD700).copy(alpha = 0.3f),
                                ),
                        ),
                    start = Offset(0f, yPosition),
                    end = Offset(size.width, yPosition),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Screen,
                )

                // Sparkle trail effect (particles behind brush)
                val trailCount = 5
                for (i in 0 until trailCount) {
                    val trailOffset = i * 15f
                    val trailAlpha = 1f - (i.toFloat() / trailCount)

                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = trailAlpha * 0.6f),
                        radius = 3f * (1f - i.toFloat() / trailCount),
                        center =
                            Offset(
                                size.width * 0.5f + kotlin.math.cos(i.toFloat()) * 20f,
                                yPosition - trailOffset,
                            ),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
        }

        // Magic wand icon leading the brush
        if (animatedRevealProgress > 0f && animatedRevealProgress < 1f) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val containerWidth = with(density) { maxWidth.toPx() }
                val containerHeight = with(density) { maxHeight.toPx() }

                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    x = with(density) { (containerWidth * 0.5f - 20.dp.toPx()).roundToInt() },
                                    y = with(density) { (containerHeight * animatedRevealProgress - 30.dp.toPx()).roundToInt() },
                                )
                            }
                            .size(40.dp)
                            .shadow(8.dp, CircleShape)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                Color(0xFFFFD700),
                                                Color(0xFFFFA500),
                                            ),
                                    ),
                                shape = CircleShape,
                            ),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Magic brush",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Loading indicator for processing phase
 */
@Composable
fun ProcessingLoadingIndicator(
    currentPhase: ProcessingPhase,
    modifier: Modifier = Modifier,
) {
    val message =
        when (currentPhase) {
            ProcessingPhase.IDLE -> ""
            ProcessingPhase.LOADING_PHOTO_QUAD -> "Loading..."
            ProcessingPhase.STRAIGHTENING -> "Straightening..."
            ProcessingPhase.DIMMING_BACKGROUND -> "Focusing..."
            ProcessingPhase.MAGIC_BRUSH_STARTING -> "Preparing enhancement..."
            ProcessingPhase.MAGIC_BRUSH_APPLYING -> "Applying magic colors..."
            ProcessingPhase.COMPLETING -> "Finishing touches..."
            ProcessingPhase.COMPLETED -> "Complete!"
        }

    if (message.isNotEmpty()) {
        Surface(
            modifier =
                modifier
                    .padding(16.dp),
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
