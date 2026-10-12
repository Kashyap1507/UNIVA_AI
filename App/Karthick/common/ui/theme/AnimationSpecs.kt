package com.kashyap.wizlyai.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*

/**
 * Standardized Animation Specifications (UX-020)
 *
 * Provides consistent animation timing, easing curves, and transition specs
 * across the entire application for a polished, cohesive UX.
 *
 * Usage:
 * ```kotlin
 * AnimatedVisibility(
 *     visible = isVisible,
 *     enter = AnimationSpecs.fadeIn(),
 *     exit = AnimationSpecs.fadeOut()
 * )
 * ```
 */
object AnimationSpecs {
    // ========== DURATIONS ==========

    /**
     * Fast duration for quick transitions (150ms)
     * Use for: Small UI elements, icon changes, subtle effects
     */
    const val DURATION_FAST = 150

    /**
     * Medium duration for standard transitions (300ms)
     * Use for: Most UI transitions, dialogs, bottom sheets
     */
    const val DURATION_MEDIUM = 300

    /**
     * Slow duration for emphasized transitions (500ms)
     * Use for: Screen transitions, large content changes
     */
    const val DURATION_SLOW = 500

    /**
     * Extra slow duration for special effects (700ms)
     * Use for: Celebration animations, onboarding
     */
    const val DURATION_EXTRA_SLOW = 700

    /**
     * Stagger delay between animated items (50ms)
     * Use for: List item animations, card reveals
     */
    const val STAGGER_DELAY = 50

    // ========== EASING CURVES ==========

    /**
     * Fast out, slow in - Material Design standard
     * Use for: Most animations, smooth deceleration
     */
    val EasingStandard = FastOutSlowInEasing

    /**
     * Linear easing - constant speed
     * Use for: Progress indicators, loading animations
     */
    val EasingLinear = LinearEasing

    /**
     * Emphasized easing - more dramatic
     * Use for: Important state changes, focus changes
     */
    val EasingEmphasized = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /**
     * Fast out, linear in - enters quickly
     * Use for: Exits, dismissals
     */
    val EasingExit = FastOutLinearInEasing

    /**
     * Linear out, slow in - exits slowly
     * Use for: Entrances, reveals
     */
    val EasingEnter = LinearOutSlowInEasing

    // ========== TWEEN SPECS ==========

    /**
     * Fast tween with standard easing
     */
    fun <T> fastTween() =
        tween<T>(
            durationMillis = DURATION_FAST,
            easing = EasingStandard,
        )

    /**
     * Medium tween with standard easing (default)
     */
    fun <T> mediumTween() =
        tween<T>(
            durationMillis = DURATION_MEDIUM,
            easing = EasingStandard,
        )

    /**
     * Slow tween with standard easing
     */
    fun <T> slowTween() =
        tween<T>(
            durationMillis = DURATION_SLOW,
            easing = EasingStandard,
        )

    // ========== SPRING SPECS ==========

    /**
     * Gentle spring animation - subtle bounce
     * Use for: Most spring animations, cards, buttons
     */
    fun <T> gentleSpring() =
        spring<T>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    /**
     * Bouncy spring animation - noticeable bounce
     * Use for: Playful animations, celebrations
     */
    fun <T> bouncySpring() =
        spring<T>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /**
     * Stiff spring animation - minimal bounce
     * Use for: Precise movements, snapping
     */
    fun <T> stiffSpring() =
        spring<T>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        )

    // ========== FADE ANIMATIONS ==========

    /**
     * Fade in animation (medium duration)
     */
    fun fadeIn(
        duration: Int = DURATION_MEDIUM,
        delay: Int = 0,
    ) = fadeIn(
        animationSpec =
            tween(
                durationMillis = duration,
                delayMillis = delay,
                easing = EasingEnter,
            ),
    )

    /**
     * Fade out animation (medium duration)
     */
    fun fadeOut(
        duration: Int = DURATION_MEDIUM,
        delay: Int = 0,
    ) = fadeOut(
        animationSpec =
            tween(
                durationMillis = duration,
                delayMillis = delay,
                easing = EasingExit,
            ),
    )

    // ========== SLIDE ANIMATIONS ==========

    /**
     * Slide in from top
     */
    fun slideInFromTop(duration: Int = DURATION_MEDIUM) =
        slideInVertically(
            animationSpec = tween(duration, easing = EasingEnter),
            initialOffsetY = { -it },
        )

    /**
     * Slide in from bottom
     */
    fun slideInFromBottom(duration: Int = DURATION_MEDIUM) =
        slideInVertically(
            animationSpec = tween(duration, easing = EasingEnter),
            initialOffsetY = { it },
        )

    /**
     * Slide in from start (left in LTR, right in RTL)
     */
    fun slideInFromStart(duration: Int = DURATION_MEDIUM) =
        slideInHorizontally(
            animationSpec = tween(duration, easing = EasingEnter),
            initialOffsetX = { -it },
        )

    /**
     * Slide in from end (right in LTR, left in RTL)
     */
    fun slideInFromEnd(duration: Int = DURATION_MEDIUM) =
        slideInHorizontally(
            animationSpec = tween(duration, easing = EasingEnter),
            initialOffsetX = { it },
        )

    /**
     * Slide out to top
     */
    fun slideOutToTop(duration: Int = DURATION_MEDIUM) =
        slideOutVertically(
            animationSpec = tween(duration, easing = EasingExit),
            targetOffsetY = { -it },
        )

    /**
     * Slide out to bottom
     */
    fun slideOutToBottom(duration: Int = DURATION_MEDIUM) =
        slideOutVertically(
            animationSpec = tween(duration, easing = EasingExit),
            targetOffsetY = { it },
        )

    // ========== SCALE ANIMATIONS ==========

    /**
     * Scale in animation (from 0.8x to 1.0x)
     */
    fun scaleIn(
        duration: Int = DURATION_MEDIUM,
        initialScale: Float = 0.8f,
    ) = scaleIn(
        animationSpec = tween(duration, easing = EasingEnter),
        initialScale = initialScale,
    )

    /**
     * Scale out animation (from 1.0x to 0.8x)
     */
    fun scaleOut(
        duration: Int = DURATION_MEDIUM,
        targetScale: Float = 0.8f,
    ) = scaleOut(
        animationSpec = tween(duration, easing = EasingExit),
        targetScale = targetScale,
    )

    // ========== EXPAND/SHRINK ANIMATIONS ==========

    /**
     * Expand vertically animation
     */
    fun expandVertically(duration: Int = DURATION_MEDIUM) =
        expandVertically(
            animationSpec = tween(duration, easing = EasingEnter),
        )

    /**
     * Shrink vertically animation
     */
    fun shrinkVertically(duration: Int = DURATION_MEDIUM) =
        shrinkVertically(
            animationSpec = tween(duration, easing = EasingExit),
        )

    /**
     * Expand horizontally animation
     */
    fun expandHorizontally(duration: Int = DURATION_MEDIUM) =
        expandHorizontally(
            animationSpec = tween(duration, easing = EasingEnter),
        )

    /**
     * Shrink horizontally animation
     */
    fun shrinkHorizontally(duration: Int = DURATION_MEDIUM) =
        shrinkHorizontally(
            animationSpec = tween(duration, easing = EasingExit),
        )

    // ========== COMBINED ANIMATIONS ==========

    /**
     * Fade + slide in from bottom (dialog/bottom sheet entrance)
     */
    fun fadeInSlideUp(duration: Int = DURATION_MEDIUM) = fadeIn(duration) + slideInFromBottom(duration)

    /**
     * Fade + slide out to bottom (dialog/bottom sheet exit)
     */
    fun fadeOutSlideDown(duration: Int = DURATION_MEDIUM) = fadeOut(duration) + slideOutToBottom(duration)

    /**
     * Fade + scale in (pop-in effect)
     */
    fun fadeInScale(duration: Int = DURATION_MEDIUM) = fadeIn(duration) + scaleIn(duration)

    /**
     * Fade + scale out (pop-out effect)
     */
    fun fadeOutScale(duration: Int = DURATION_MEDIUM) = fadeOut(duration) + scaleOut(duration)

    // ========== STAGGER HELPERS ==========

    /**
     * Calculate stagger delay for index
     * Use for list item animations
     */
    fun staggerDelay(
        index: Int,
        baseDelay: Int = STAGGER_DELAY,
    ): Int {
        return index * baseDelay
    }

    /**
     * Fade in with stagger delay
     */
    fun fadeInStaggered(
        index: Int,
        duration: Int = DURATION_MEDIUM,
    ) = fadeIn(
        duration = duration,
        delay = staggerDelay(index),
    )

    /**
     * Slide in from bottom with stagger delay
     */
    fun slideInStaggered(
        index: Int,
        duration: Int = DURATION_MEDIUM,
    ) = slideInFromBottom(duration) +
        fadeIn(
            duration = duration,
            delay = staggerDelay(index),
        )

    // ========== CROSSFADE SPECS ==========

    /**
     * Crossfade animation spec for content transitions
     */
    fun crossfadeSpec() =
        tween<Float>(
            durationMillis = DURATION_MEDIUM,
            easing = EasingStandard,
        )
}
