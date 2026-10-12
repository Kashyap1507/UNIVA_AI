package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton Loading States with Shimmer Effect (UX-016)
 *
 * Provides modern loading placeholders with shimmer animation
 * for better perceived performance during data loading.
 */

/**
 * Shimmer effect modifier for skeleton loading
 */
fun Modifier.shimmer(): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1500,
                            easing = LinearEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer",
        )

        val shimmerColors =
            listOf(
                Color.LightGray.copy(alpha = 0.3f),
                Color.LightGray.copy(alpha = 0.5f),
                Color.LightGray.copy(alpha = 0.3f),
            )

        background(
            brush =
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnimation - 300f, translateAnimation - 300f),
                    end = Offset(translateAnimation, translateAnimation),
                ),
        )
    }

/**
 * Base skeleton box with shimmer effect
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .shimmer(),
    )
}

/**
 * Skeleton for document cards (horizontal scrolling)
 */
@Composable
fun DocumentCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier =
            modifier
                .width(160.dp)
                .height(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Thumbnail placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                shape = RoundedCornerShape(8.dp),
            )

            // Title placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(16.dp),
                shape = RoundedCornerShape(4.dp),
            )

            // Subtitle placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp),
                shape = RoundedCornerShape(4.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Footer info placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}

/**
 * Skeleton for correction cards (horizontal scrolling)
 */
@Composable
fun CorrectionCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier =
            modifier
                .width(200.dp)
                .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Thumbnail placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .size(80.dp),
                shape = RoundedCornerShape(8.dp),
            )

            // Content column
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Title placeholder
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                )

                // Score badge placeholder
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(60.dp)
                            .height(24.dp),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                // Date placeholder
                SkeletonBox(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                )
            }
        }
    }
}

/**
 * Skeleton for study collection cards
 */
@Composable
fun StudyCollectionCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier =
            modifier
                .width(180.dp)
                .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .size(48.dp),
                shape = CircleShape,
            )

            // Title placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(18.dp),
                shape = RoundedCornerShape(4.dp),
            )

            // Subtitle placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp),
                shape = RoundedCornerShape(4.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Footer badges placeholder
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(60.dp)
                            .height(20.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                SkeletonBox(
                    modifier =
                        Modifier
                            .width(50.dp)
                            .height(20.dp),
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
    }
}

/**
 * Skeleton for list items (vertical list)
 */
@Composable
fun ListItemSkeleton(
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    thumbnailSize: Dp = 60.dp,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showThumbnail) {
            // Thumbnail placeholder
            SkeletonBox(
                modifier = Modifier.size(thumbnailSize),
                shape = RoundedCornerShape(8.dp),
            )
        }

        // Content column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp),
                shape = RoundedCornerShape(4.dp),
            )

            // Subtitle placeholder
            SkeletonBox(
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }

        // Trailing icon placeholder
        SkeletonBox(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
        )
    }
}

/**
 * Skeleton for horizontal scrolling row
 */
@Composable
fun SkeletonHorizontalRow(
    count: Int = 3,
    itemContent: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            itemContent()
        }
    }
}

/**
 * Skeleton for vertical list
 */
@Composable
fun SkeletonVerticalList(
    count: Int = 5,
    itemContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) {
            itemContent()
        }
    }
}
