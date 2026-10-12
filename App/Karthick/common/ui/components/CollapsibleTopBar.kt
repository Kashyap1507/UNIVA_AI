package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kashyap.wizlyai.ui.theme.rememberPrimaryGradient

/**
 * Collapsible Top Bar Component (UX-027)
 * Modern scrolling behavior with smooth animations
 * - Stays sticky in safe area using system insets
 * - Content slides up/down based on scroll
 * - Smooth spring animations (150ms)
 */
@Composable
fun CollapsibleTopBar(
    title: String,
    isCollapsed: Boolean,
    showBackButton: Boolean = false,
    collapsible: Boolean = true, // When false, title stays visible regardless of isCollapsed
    onBackClick: () -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Animated elevation - 6dp when visible, 0dp when collapsed
    val elevation by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 6.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "topBarElevation",
    )

    // Animated alpha for content - only fade if collapsible is true
    val contentAlpha by animateFloatAsState(
        targetValue = if (collapsible && isCollapsed) 0f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "contentAlpha",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background, // Match HomeTopBar style - consistent across all tabs
        shadowElevation = elevation,
        tonalElevation = 0.dp, // Prevents visual separator in light mode
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(), // UX-027: Use system safe area insets
        ) {
            // Top bar content
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp)
                        .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left side - Back button (optional) + Title; long titles shorten so trailing content fits
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!showBackButton) Modifier.padding(start = 8.dp) else Modifier,
                    )
                }

                // Right side - Trailing content
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    trailingContent()
                }
            }
        }
    }
}

/**
 * Home Tab Top Bar (UX-027)
 * Shows app name "Univa AI" + the student's avatar (opens their profile)
 * Height animates with system safe area insets
 */
@Composable
fun HomeTopBar(
    isCollapsed: Boolean,
    studentName: String,
    onAvatarClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Animate elevation - 6dp when visible for prominent shadow
    val elevation by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 6.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "homeTopBarElevation",
    )

    // Animate content alpha
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "homeContentAlpha",
    )

    // Animate height: 56dp content height → 0dp when collapsed
    val contentHeight by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 56.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "homeContentHeight",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background, // Match home screen background (light gray)
        shadowElevation = elevation,
        tonalElevation = 0.dp, // Disable tonal elevation to prevent visual separator in light mode
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(), // UX-027: Use system safe area insets
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(contentHeight),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Univa AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 1.1f,
                        modifier = Modifier.padding(start = 8.dp),
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .background(brush = rememberPrimaryGradient(), shape = CircleShape)
                                .clickable(onClickLabel = "Open your profile") { onAvatarClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = studentName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Documents Tab Top Bar (UX-027)
 * Shows "Documents" title + controls (selection, view mode)
 */
@Composable
fun DocumentsTopBar(
    isCollapsed: Boolean,
    selectionMode: Boolean,
    viewMode: ViewMode,
    documentCount: Int = 0,
    searchVisible: Boolean = false,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onSelectionModeToggle: () -> Unit,
    onViewModeToggle: () -> Unit,
    onSearchToggle: () -> Unit = {},
    onGroupClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val elevation by animateDpAsState(
        targetValue = if (isCollapsed) 0.dp else 6.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "docsTopBarElevation",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "docsContentAlpha",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background, // Match HomeTopBar style - consistent across all tabs
        shadowElevation = elevation,
        tonalElevation = 0.dp, // Prevents visual separator in light mode
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(), // UX-027: Use system safe area insets
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp)
                        .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left side - Back button (optional) + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Text(
                        text = "All docs ($documentCount)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = if (!showBackButton) Modifier.padding(start = 8.dp) else Modifier,
                    )
                }

                // Right side - Controls
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Search toggle
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search",
                            tint = if (searchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Selection mode toggle
                    IconButton(onClick = onSelectionModeToggle) {
                        Icon(
                            imageVector = if (selectionMode) Icons.Default.Close else Icons.Default.CheckBox,
                            contentDescription = if (selectionMode) "Exit selection" else "Select items",
                            tint = if (selectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // View mode toggle
                    IconButton(onClick = onViewModeToggle) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.GRID) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Group by
                    IconButton(onClick = onGroupClick) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Group by",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Sort by
                    IconButton(onClick = onSortClick) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort by",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * View mode enum for Documents screen
 */
enum class ViewMode {
    GRID,
    LIST,
}
