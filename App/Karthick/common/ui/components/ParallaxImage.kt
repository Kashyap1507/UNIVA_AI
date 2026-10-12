package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

/**
 * Parallax Image Component (UX-028)
 *
 * Creates a smooth parallax scrolling effect where the image scrolls slower than the content,
 * adding depth and visual interest to the UI.
 *
 * Features:
 * - Configurable parallax ratio (default 0.5x = half speed)
 * - Smooth 60fps animation using graphicsLayer
 * - Works with LazyColumn/LazyRow scroll states
 * - Minimal performance impact (GPU-accelerated)
 *
 * Usage:
 * ```kotlin
 * val scrollState = rememberLazyListState()
 * LazyColumn(state = scrollState) {
 *     item {
 *         ParallaxImage(
 *             imageUrl = collection.coverImageUrl,
 *             scrollState = scrollState,
 *             itemIndex = 0,
 *             parallaxRatio = 0.5f
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun ParallaxImage(
    imageUrl: String?,
    scrollState: LazyListState,
    itemIndex: Int = 0,
    parallaxRatio: Float = 0.5f,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    // Calculate parallax offset based on scroll position
    // Only calculate when this item is visible for performance
    val parallaxOffset by remember(scrollState) {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == itemIndex }

            if (visibleItem != null) {
                // Calculate offset relative to item position
                // Positive offset = scrolling down, negative = scrolling up
                val itemOffset = visibleItem.offset.toFloat()

                // Apply parallax ratio (0.5 = half speed, 0 = no parallax, 1 = same speed)
                itemOffset * parallaxRatio
            } else {
                0f
            }
        }
    }

    // Subtle scale effect on scroll (optional, adds depth)
    val scale by remember(scrollState) {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == itemIndex }

            if (visibleItem != null) {
                val itemOffset = visibleItem.offset.toFloat()
                val viewportHeight = layoutInfo.viewportSize.height.toFloat()

                // Scale from 1.0 to 1.1 as user scrolls
                val scrollProgress = (itemOffset / viewportHeight).coerceIn(-1f, 1f)
                1.0f + (scrollProgress * 0.05f) // Max 5% scale
            } else {
                1.0f
            }
        }
    }

    // Use AsyncImage with graphicsLayer for GPU-accelerated parallax
    coil3.compose.AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier =
            modifier
                .graphicsLayer {
                    // GPU-accelerated transformations (60fps smooth)
                    translationY = parallaxOffset
                    scaleX = scale
                    scaleY = scale
                },
    )
}

/**
 * Parallax Image for horizontal scrolling (LazyRow) with progressive loading (UX-028 + UX-019)
 * Combines smooth parallax effect with progressive image loading for best UX
 * Uses translationX instead of translationY
 */
@Composable
fun ParallaxImageHorizontal(
    imageUrl: String?,
    scrollState: LazyListState,
    itemIndex: Int = 0,
    parallaxRatio: Float = 0.3f,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val parallaxOffset by remember(scrollState) {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == itemIndex }

            if (visibleItem != null) {
                val itemOffset = visibleItem.offset.toFloat()
                itemOffset * parallaxRatio
            } else {
                0f
            }
        }
    }

    // Use ProgressiveThumbnail with parallax transformation (combines UX-019 + UX-028)
    ProgressiveThumbnail(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier =
            modifier
                .graphicsLayer {
                    translationX = parallaxOffset
                },
    )
}
