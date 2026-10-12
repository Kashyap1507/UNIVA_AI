package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Unified Loading Stage - Generic loading stages for all workflows
 */
enum class LoadingStage(
    val progress: Float,
    val title: String,
    val subtitles: List<String>,
) {
    UPLOADING(
        progress = 0.25f,
        title = "Uploading",
        subtitles =
            listOf(
                "Preparing images...",
                "Uploading to server...",
                "Processing pages...",
            ),
    ),
    ANALYZING(
        progress = 0.50f,
        title = "Analyzing",
        subtitles =
            listOf(
                "Reading content...",
                "Analyzing structure...",
                "Processing information...",
            ),
    ),
    PROCESSING(
        progress = 0.75f,
        title = "Processing",
        subtitles =
            listOf(
                "Generating content...",
                "Applying corrections...",
                "Preparing results...",
            ),
    ),
    FINALIZING(
        progress = 1.0f,
        title = "Finalizing",
        subtitles =
            listOf(
                "Almost ready...",
                "Finishing up...",
                "Preparing to display...",
            ),
    ),
}

/**
 * Unified Loading Screen
 *
 * Standard loading screen component used across workflows (check my work, scanning,
 * course material uploads). Provides consistent UX with Material 3 design,
 * proper safe area handling, and smooth animations.
 *
 * Features:
 * - Circular progress indicator with percentage
 * - Linear progress bar with smooth animations
 * - Stage-based or custom message loading
 * - Cancel button (top-right) with safe area padding
 * - Material 3 design guidelines
 * - Accessibility support
 *
 * @param currentStage Current loading stage (null for indeterminate loading)
 * @param customMessage Optional custom message instead of stage title
 * @param showProgress Whether to show percentage and progress bar (default: true)
 * @param onCancel Optional cancel callback (shows X button when provided)
 * @param safeAreaTopPadding Top safe area padding for iOS notch/Dynamic Island (default: 48.dp)
 * @param modifier Optional modifier
 */
@Composable
fun UnifiedLoadingScreen(
    currentStage: LoadingStage? = null,
    customMessage: String? = null,
    showProgress: Boolean = true,
    onCancel: (() -> Unit)? = null,
    safeAreaTopPadding: androidx.compose.ui.unit.Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    // Default to ANALYZING stage if null
    val stage = currentStage ?: LoadingStage.ANALYZING

    // Rotating subtitle index
    var subtitleIndex by remember(stage) { mutableStateOf(0) }

    // Rotate subtitles every 2 seconds
    LaunchedEffect(stage) {
        while (true) {
            delay(2000)
            subtitleIndex = (subtitleIndex + 1) % stage.subtitles.size
        }
    }

    // Animated progress for smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = if (currentStage != null) stage.progress else 0.5f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_animation",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // Cancel button in top-right corner with safe area padding
        if (onCancel != null) {
            IconButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = safeAreaTopPadding, end = 16.dp)
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            // Circular Progress Indicator
            if (showProgress) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp),
                ) {
                    // Background circle
                    Box(
                        modifier =
                            Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    )

                    // Circular progress indicator
                    if (currentStage != null) {
                        // Determinate progress
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    } else {
                        // Indeterminate progress
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    }

                    // Percentage text (only for determinate)
                    if (currentStage != null) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Simple circular progress without percentage
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Title
            Text(
                text = customMessage ?: stage.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            // Rotating Subtitle with Animation (only if no custom message)
            if (customMessage == null) {
                AnimatedContent(
                    targetState = stage.subtitles[subtitleIndex],
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                    },
                    label = "subtitle_animation",
                ) { subtitle ->
                    Text(
                        text = subtitle,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.height(24.dp),
                    )
                }
            }

            // Linear Progress Bar (only if showing progress)
            if (showProgress && currentStage != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stage indicator text
                    Text(
                        text = "Step ${stage.ordinal + 1} of ${LoadingStage.entries.size}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Convenience composable for simple message-based loading
 */
@Composable
fun SimpleLoadingScreen(
    message: String,
    onCancel: (() -> Unit)? = null,
    safeAreaTopPadding: androidx.compose.ui.unit.Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    UnifiedLoadingScreen(
        currentStage = null,
        customMessage = message,
        showProgress = false,
        onCancel = onCancel,
        safeAreaTopPadding = safeAreaTopPadding,
        modifier = modifier,
    )
}

/**
 * Convenience composable for generic scanning workflow
 */
@Composable
fun ScanningLoading(
    message: String = "Processing scan",
    onCancel: (() -> Unit)? = null,
    safeAreaTopPadding: androidx.compose.ui.unit.Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    SimpleLoadingScreen(
        message = message,
        onCancel = onCancel,
        safeAreaTopPadding = safeAreaTopPadding,
        modifier = modifier,
    )
}
