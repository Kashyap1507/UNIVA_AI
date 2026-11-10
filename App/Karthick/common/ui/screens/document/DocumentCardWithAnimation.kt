package com.kashyap.wizlyai.ui.screens.document

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kashyap.wizlyai.ui.screens.scanning.CropBounds
import com.kashyap.wizlyai.ui.screens.scanning.ProcessingAnimationState
import com.kashyap.wizlyai.ui.screens.scanning.ProcessingPhase
import com.kashyap.wizlyai.ui.screens.scanning.rememberProcessingAnimationState
import com.kashyap.wizlyai.vision.DocumentEdges
import com.kashyap.wizlyai.vision.ImageSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlinx.coroutines.delay as delayCoroutine

/**
 * Document Card with optional animation on first appearance
 *
 * Features:
 * - Shows static photo when animationData is null
 * - Plays full animation sequence when animationData is provided
 * - Animation starts immediately on screen appearance (instant transition)
 * - After animation completes, transitions to static photo display
 */
@Composable
fun DocumentCardWithAnimation(
    imagePath: String,
    animationData: AnimationData? = null,
    viewSize: IntSize = IntSize.Zero,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAnimation by remember(animationData) { mutableStateOf(animationData != null) }
    val processingState = rememberProcessingAnimationState()

    // Animated corner radius for fullscreen morphing (clamped to prevent negative values)
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isFullscreen) 0.dp else 16.dp,
        animationSpec =
            spring(
                dampingRatio = 0.75f,
                stiffness = 300f,
            ),
        label = "cornerRadius",
    )
    // Clamp to prevent negative values during spring overshoot
    val cornerRadius = animatedCornerRadius.coerceAtLeast(0.dp)

    // Store initial animation parameters
    val initialAnimationData = remember(animationData) { animationData }

    // Trigger animation sequence when component appears
    LaunchedEffect(initialAnimationData) {
        if (initialAnimationData != null && showAnimation) {
            playAnimationSequence(
                processingState,
                initialAnimationData,
                viewSize,
            ) {
                showAnimation = false
            }
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    clip = false,
                ),
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Animation phases
            if (showAnimation && initialAnimationData != null) {
                // Phase 1-2: Original photo with perspective transform
                if (processingState.currentPhase >= ProcessingPhase.LOADING_PHOTO_QUAD &&
                    processingState.currentPhase < ProcessingPhase.MAGIC_BRUSH_APPLYING
                ) {
                    PhotoWithPerspectiveTransform(
                        imagePath = initialAnimationData.croppedImagePath,
                        alpha = processingState.photoAlpha,
                        straighteningProgress = processingState.straighteningProgress,
                        cropBounds = initialAnimationData.cropBounds,
                    )
                }

                // Phase 5: Dual-layer magic brush reveal
                if (processingState.currentPhase >= ProcessingPhase.MAGIC_BRUSH_APPLYING &&
                    initialAnimationData.enhancedImagePath != null
                ) {
                    DualLayerMagicBrush(
                        originalImagePath = initialAnimationData.croppedImagePath,
                        enhancedImagePath = initialAnimationData.enhancedImagePath,
                        revealProgress = processingState.enhancementRevealProgress,
                    )
                }
            } else {
                // Static photo display
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalPlatformContext.current)
                            .data(if (imagePath.startsWith("file://")) imagePath else "file://$imagePath")
                            .crossfade(true)
                            .build(),
                    contentDescription = "Document page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Fullscreen toggle button (bottom-right corner)
            FullscreenToggleButton(
                isFullscreen = isFullscreen,
                onClick = onToggleFullscreen,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
            )
        }
    }
}

/**
 * Fullscreen toggle button with animated icon rotation
 */
@Composable
private fun FullscreenToggleButton(
    isFullscreen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animated rotation for icon morphing
    val iconRotation by animateFloatAsState(
        targetValue = if (isFullscreen) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "iconRotation",
    )

    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(48.dp)
                .shadow(4.dp, CircleShape)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
    ) {
        Icon(
            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
            tint = Color.White,
            modifier =
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = iconRotation
                    },
        )
    }
}

/**
 * Animation data for the card
 */
data class AnimationData(
    val originalImagePath: String,
    val croppedImagePath: String,
    val enhancedImagePath: String?,
    val edges: DocumentEdges,
    val imageSize: ImageSize,
    val rotation: Int,
    val cropBounds: CropBounds,
    val isCropEnabled: Boolean = true, // Whether crop bounds were active
    val shouldApplyConstraintResize: Boolean = true, // True when coming from PhotoReview
)

/**
 * Play the complete animation sequence
 */
private suspend fun playAnimationSequence(
    processingState: ProcessingAnimationState,
    animationData: AnimationData,
    viewSize: androidx.compose.ui.unit.IntSize,
    onComplete: () -> Unit,
) {
    try {
        val imgWidth = viewSize.width.toFloat()
        val imgHeight = viewSize.height.toFloat()

        // Phase 1: Load cropped photo instantly (0ms)
        println("🎬 Phase 1: LOADING_PHOTO_QUAD (instant)")
        processingState.updatePhase(ProcessingPhase.LOADING_PHOTO_QUAD)
        processingState.updatePhotoAlpha(1f) // Show photo immediately

        // Phase 2: Straighten (0-300ms) - Faster straightening
        println("🎬 Phase 2: STRAIGHTENING (0-300ms)")
        processingState.updatePhase(ProcessingPhase.STRAIGHTENING)
        // Animate straightening progress
        animateToValueForCard(0f, 1f, 300) { value ->
            processingState.updateStraighteningProgress(value)
        }

        // Phase 3: Magic brush applying (300-700ms) - Faster brush reveal
        if (animationData.enhancedImagePath != null) {
            println("🎬 Phase 3: MAGIC_BRUSH_APPLYING (300-700ms)")
            processingState.updatePhase(ProcessingPhase.MAGIC_BRUSH_APPLYING)
            animateToValueForCard(0f, 1f, 400) { value ->
                processingState.updateEnhancementRevealProgress(value)
            }
        }

        // Phase 4: Complete (700-800ms)
        println("🎉 Animation Complete")
        processingState.updatePhase(ProcessingPhase.COMPLETED)
        delay(100)
        onComplete()
    } catch (e: Exception) {
        println("❌ Animation error: ${e.message}")
        e.printStackTrace()
        onComplete()
    }
}

/**
 * Animate from one value to another (synchronous animation helper)
 * Note: Using animateToValue from FullscreenDocumentCard.kt instead
 */
internal suspend fun animateToValueForCard(
    from: Float,
    to: Float,
    durationMillis: Int,
    onUpdate: (Float) -> Unit,
) {
    val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    while (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime < durationMillis) {
        val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
        val progress = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
        val value = from + (to - from) * progress
        onUpdate(value)
        delayCoroutine(16) // ~60fps
    }
    onUpdate(to)
}

/**
 * Background dim overlay for the card
 */
@Composable
private fun DimBackgroundOverlayForCard(
    dimAlpha: Float,
    sparkles: List<com.kashyap.wizlyai.ui.screens.scanning.SparkleParticle>,
    modifier: Modifier = Modifier,
) {
    val animatedDimAlpha by animateFloatAsState(
        targetValue = dimAlpha,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dimAlpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedDimAlpha)),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            sparkles.forEach { particle ->
                drawSparkle(particle)
            }
        }
    }
}

/**
 * Draw individual sparkle
 */
private fun DrawScope.drawSparkle(particle: com.kashyap.wizlyai.ui.screens.scanning.SparkleParticle) {
    val radius = particle.size * particle.scale

    drawCircle(
        color = particle.color.copy(alpha = particle.alpha * 0.3f),
        radius = radius * 2f,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    drawCircle(
        color = particle.color.copy(alpha = particle.alpha),
        radius = radius,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    drawCircle(
        color = Color.White.copy(alpha = particle.alpha * 0.8f),
        radius = radius * 0.4f,
        center = particle.position,
        blendMode = BlendMode.Screen,
    )

    val lineLength = radius * 1.5f
    val lineWidth = radius * 0.2f

    drawLine(
        color = particle.color.copy(alpha = particle.alpha * 0.6f),
        start = Offset(particle.position.x - lineLength, particle.position.y),
        end = Offset(particle.position.x + lineLength, particle.position.y),
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Screen,
    )

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
 * Photo with perspective transform animation
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
                    val skewAmount = (1f - straighteningProgress) * 0.15f
                    transformOrigin = TransformOrigin.Center
                    rotationX = skewAmount * 8f
                    rotationY = skewAmount * -6f
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
            contentDescription = "Cropped photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Dual-layer magic brush reveal using full card height
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
        animationSpec = tween(400, easing = LinearEasing), // Match optimized timing
        label = "revealProgress",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Base layer: Original photo
        AsyncImage(
            model =
                ImageRequest.Builder(LocalPlatformContext.current)
                    .data(if (originalImagePath.startsWith("file://")) originalImagePath else "file://$originalImagePath")
                    .crossfade(false)
                    .build(),
            contentDescription = "Original photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Enhanced image layer with progressive reveal
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
                                    // Simple top-to-bottom reveal using card height
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

        // Magic brush visual indicator
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calculate brush position based on card height
            val yPosition = size.height * animatedRevealProgress

            if (animatedRevealProgress > 0f && animatedRevealProgress < 1f) {
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

        // Magic brush icon indicator
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
                                    y =
                                        with(density) {
                                            val brushY = containerHeight * animatedRevealProgress
                                            (brushY - 30.dp.toPx()).roundToInt()
                                        },
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

// rememberProcessingAnimationState is imported from ProcessingAnimationState.kt
