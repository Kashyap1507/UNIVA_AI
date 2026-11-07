package com.kashyap.wizlyai.ui.screens.scanning

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Animation phases for document processing flow.
 * Each phase represents a visual state during the enhancement process.
 *
 * CARD-BASED ANIMATION FLOW:
 * The animation now happens within a fixed card matching DocumentViewerScreen's photo card.
 * This creates a seamless transition where the card remains in place during navigation.
 */
enum class ProcessingPhase {
    /**
     * Initial state - no processing active
     */
    IDLE,

    /**
     * Phase 1 (0-400ms): Load cropped photo in quadrilateral form into card
     * - Fade in photo with quadrilateral perspective
     * - Card is already positioned at final DocumentViewerScreen location
     */
    LOADING_PHOTO_QUAD,

    /**
     * Phase 2 (400-900ms): Straighten quadrilateral into rectangle
     * - Animate perspective transform from quad → rectangle
     * - Maintain card position (no movement)
     */
    STRAIGHTENING,

    /**
     * Phase 3 (900-1100ms): Dim background outside card
     * - Apply translucent dark overlay to everything except card
     * - Brings focus to the card content
     */
    DIMMING_BACKGROUND,

    /**
     * Phase 4 (1100-1300ms): Show magic brush starting indicator
     * - Brief sparkle/glow effect at top of card
     * - Indicates enhancement is about to begin
     */
    MAGIC_BRUSH_STARTING,

    /**
     * Phase 5 (1300-3500ms): Progressive top-to-bottom enhancement reveal
     * - Base layer: Original photo (already loaded)
     * - Overlay layer: Enhanced photo revealed progressively from top to bottom
     * - Visual effect: Magic brush "painting" enhancement onto photo
     * - Sparkle particles follow the brush line
     */
    MAGIC_BRUSH_APPLYING,

    /**
     * Phase 6 (3500-4000ms): Final sparkle burst and completion
     * - Full enhanced photo visible
     * - Final sparkle burst animation
     */
    COMPLETING,

    /**
     * Complete - ready for navigation transition
     * - Card remains in place
     * - DocumentViewerScreen fades in underneath
     * - Animation overlay fades out
     */
    COMPLETED,
}

/**
 * State holder for document processing animations.
 * Manages animation phases, progress, and visual effects.
 */
@Stable
class ProcessingAnimationState {
    /**
     * Current animation phase
     */
    var currentPhase by mutableStateOf(ProcessingPhase.IDLE)
        private set

    /**
     * Overall progress (0.0 to 1.0)
     */
    var progress by mutableStateOf(0f)
        private set

    /**
     * Magic brush vertical position (0.0 = top, 1.0 = bottom)
     */
    var magicBrushPosition by mutableStateOf(0f)
        private set

    /**
     * Blur radius for background (0f to 20f)
     */
    var blurRadius by mutableStateOf(0f)
        private set

    /**
     * Crop area scale (1.0 = normal, 1.05 = elevated)
     */
    var cropScale by mutableStateOf(1.0f)
        private set

    /**
     * Crop area elevation (0dp to 24dp)
     */
    var cropElevation by mutableStateOf(0f)
        private set

    /**
     * Crop area rotation for "lifting" effect (-3 to +3 degrees)
     */
    var cropRotation by mutableStateOf(0f)
        private set

    /**
     * Background dim alpha (0.0 = no dim, 1.0 = fully dimmed)
     */
    var backgroundDimAlpha by mutableStateOf(0f)
        private set

    /**
     * Quadrilateral perspective progress (0.0 = quad, 1.0 = rectangle)
     */
    var straighteningProgress by mutableStateOf(0f)
        private set

    /**
     * Photo fade-in alpha (0.0 = invisible, 1.0 = fully visible)
     */
    var photoAlpha by mutableStateOf(0f)
        private set

    /**
     * Enhanced photo reveal progress (0.0 = hidden, 1.0 = fully revealed)
     * Used for magic brush progressive reveal effect
     */
    var enhancementRevealProgress by mutableStateOf(0f)
        private set

    /**
     * Active sparkle particles
     */
    var sparkleParticles by mutableStateOf<List<SparkleParticle>>(emptyList())
        private set

    /**
     * Update animation phase
     */
    fun updatePhase(phase: ProcessingPhase) {
        currentPhase = phase
    }

    /**
     * Update overall progress
     */
    fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    /**
     * Update magic brush position
     */
    fun updateBrushPosition(value: Float) {
        magicBrushPosition = value.coerceIn(0f, 1f)
    }

    /**
     * Update blur radius
     */
    fun updateBlurRadius(value: Float) {
        blurRadius = value.coerceIn(0f, 20f)
    }

    /**
     * Update crop scale
     */
    fun updateCropScale(value: Float) {
        cropScale = value.coerceIn(0.8f, 1.2f)
    }

    /**
     * Update crop elevation
     */
    fun updateCropElevation(value: Float) {
        cropElevation = value.coerceIn(0f, 32f)
    }

    /**
     * Update crop rotation
     */
    fun updateCropRotation(value: Float) {
        cropRotation = value.coerceIn(-10f, 10f)
    }

    /**
     * Update background dim alpha
     */
    fun updateBackgroundDimAlpha(value: Float) {
        backgroundDimAlpha = value.coerceIn(0f, 1f)
    }

    /**
     * Update straightening progress
     */
    fun updateStraighteningProgress(value: Float) {
        straighteningProgress = value.coerceIn(0f, 1f)
    }

    /**
     * Update photo fade-in alpha
     */
    fun updatePhotoAlpha(value: Float) {
        photoAlpha = value.coerceIn(0f, 1f)
    }

    /**
     * Update enhancement reveal progress
     */
    fun updateEnhancementRevealProgress(value: Float) {
        enhancementRevealProgress = value.coerceIn(0f, 1f)
    }

    /**
     * Generate sparkle particles around crop bounds
     */
    fun generateSparkles(
        cropBounds: CropBounds,
        imageWidth: Float,
        imageHeight: Float,
        count: Int = 25,
    ) {
        val particles = mutableListOf<SparkleParticle>()
        val random = Random.Default

        // Convert normalized crop bounds to pixel coordinates
        val topLeftPx = Offset(cropBounds.topLeft.x * imageWidth, cropBounds.topLeft.y * imageHeight)
        val topRightPx = Offset(cropBounds.topRight.x * imageWidth, cropBounds.topRight.y * imageHeight)
        val bottomLeftPx = Offset(cropBounds.bottomLeft.x * imageWidth, cropBounds.bottomLeft.y * imageHeight)
        val bottomRightPx = Offset(cropBounds.bottomRight.x * imageWidth, cropBounds.bottomRight.y * imageHeight)

        // Calculate crop center
        val centerX = (topLeftPx.x + topRightPx.x + bottomLeftPx.x + bottomRightPx.x) / 4f
        val centerY = (topLeftPx.y + topRightPx.y + bottomLeftPx.y + bottomRightPx.y) / 4f

        // Generate particles around perimeter
        repeat(count) {
            // Random position around crop bounds
            val side = random.nextInt(4) // 0=top, 1=right, 2=bottom, 3=left
            val t = random.nextFloat() // Position along side

            val position =
                when (side) {
                    0 -> { // Top edge
                        val x = topLeftPx.x + (topRightPx.x - topLeftPx.x) * t
                        val y = topLeftPx.y + (topRightPx.y - topLeftPx.y) * t
                        Offset(x, y - random.nextFloat() * 50f) // Slightly outside
                    }
                    1 -> { // Right edge
                        val x = topRightPx.x + (bottomRightPx.x - topRightPx.x) * t
                        val y = topRightPx.y + (bottomRightPx.y - topRightPx.y) * t
                        Offset(x + random.nextFloat() * 50f, y)
                    }
                    2 -> { // Bottom edge
                        val x = bottomLeftPx.x + (bottomRightPx.x - bottomLeftPx.x) * t
                        val y = bottomLeftPx.y + (bottomRightPx.y - bottomLeftPx.y) * t
                        Offset(x, y + random.nextFloat() * 50f)
                    }
                    else -> { // Left edge
                        val x = topLeftPx.x + (bottomLeftPx.x - topLeftPx.x) * t
                        val y = topLeftPx.y + (bottomLeftPx.y - topLeftPx.y) * t
                        Offset(x - random.nextFloat() * 50f, y)
                    }
                }

            particles.add(
                SparkleParticle(
                    position = position,
                    color = SPARKLE_COLORS[random.nextInt(SPARKLE_COLORS.size)],
                    size = random.nextFloat() * 4f + 2f, // 2-6dp
                    lifetime = random.nextInt(400) + 800L, // 800-1200ms
                    delay = random.nextInt(200).toLong(), // 0-200ms stagger
                    pulseSpeed = random.nextFloat() * 0.5f + 0.5f, // 0.5-1.0
                ),
            )
        }

        sparkleParticles = particles
    }

    /**
     * Update sparkle particles (called each frame during animation)
     */
    fun updateSparkles(currentTimeMillis: Long) {
        sparkleParticles =
            sparkleParticles.map { particle ->
                particle.update(currentTimeMillis)
            }.filter { it.isAlive }
    }

    /**
     * Reset all animation state
     */
    fun reset() {
        currentPhase = ProcessingPhase.IDLE
        progress = 0f
        magicBrushPosition = 0f
        blurRadius = 0f
        cropScale = 1.0f
        cropElevation = 0f
        cropRotation = 0f
        backgroundDimAlpha = 0f
        straighteningProgress = 0f
        photoAlpha = 0f
        enhancementRevealProgress = 0f
        sparkleParticles = emptyList()
    }

    companion object {
        /**
         * Sparkle particle colors (white, light blue, gold)
         */
        private val SPARKLE_COLORS =
            listOf(
                Color.White,
                Color(0xFF87CEEB), // Sky blue
                Color(0xFFFFD700), // Gold
                Color(0xFFE0F7FA), // Very light blue
                Color(0xFFFFF8DC), // Cornsilk (light gold)
            )
    }
}

/**
 * Crop bounds for animation calculations
 */
data class CropBounds(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomLeft: Offset,
    val bottomRight: Offset,
)

/**
 * Individual sparkle particle for background animation
 */
@Stable
data class SparkleParticle(
    val position: Offset,
    val color: Color,
    val size: Float,
    val lifetime: Long, // Total lifetime in milliseconds
    val delay: Long, // Delay before starting animation
    val pulseSpeed: Float, // Speed multiplier for pulse animation
    private val birthTime: Long = Clock.System.now().toEpochMilliseconds(),
    private val currentAlpha: Float = 0f,
    private val currentScale: Float = 0.5f,
) {
    /**
     * Current age in milliseconds (accounting for delay)
     */
    private val age: Long
        get() = (Clock.System.now().toEpochMilliseconds() - birthTime - delay).coerceAtLeast(0L)

    /**
     * Whether particle is still alive
     */
    val isAlive: Boolean
        get() = age < lifetime

    /**
     * Animated alpha value (fade in -> sustain -> fade out)
     */
    val alpha: Float
        get() {
            if (age < 0) return 0f // Not started yet (delay)

            val progress = age.toFloat() / lifetime.toFloat()
            return when {
                progress < 0.2f -> progress / 0.2f // Fade in
                progress > 0.8f -> (1f - progress) / 0.2f // Fade out
                else -> 1f // Full brightness
            }
        }

    /**
     * Animated scale value (pulse effect)
     */
    val scale: Float
        get() {
            if (age < 0) return 0.5f // Not started yet

            // Sinusoidal pulse
            val cycles = age.toFloat() / 600f * pulseSpeed
            return 0.8f + kotlin.math.sin(cycles * kotlin.math.PI.toFloat() * 2f) * 0.2f
        }

    /**
     * Update particle state (called each frame)
     */
    fun update(currentTimeMillis: Long): SparkleParticle {
        // Immutable data class - return updated copy
        return copy(
            currentAlpha = alpha,
            currentScale = scale,
        )
    }
}

/**
 * Remember processing animation state across recompositions
 */
@Composable
fun rememberProcessingAnimationState(): ProcessingAnimationState {
    return remember { ProcessingAnimationState() }
}
