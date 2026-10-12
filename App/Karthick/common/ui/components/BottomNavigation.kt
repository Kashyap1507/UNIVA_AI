package com.kashyap.wizlyai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kashyap.wizlyai.navigation.BottomNavTab
import com.kashyap.wizlyai.ui.theme.PlatformDimensions
import com.kashyap.wizlyai.util.rememberHapticFeedback
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Bottom navigation bar with 4 tabs + elevated center FAB
 * Tabs: Home, Calendar, [FAB], Progress, Profile
 *
 * The center FAB opens a bottom sheet with create actions.
 * Icon animates from "+" to "×" when sheet is open.
 *
 * Height follows Material 3 standard for NavigationBar with icon + label:
 * - Android: 83dp content height + gesture nav insets (~112-128dp total)
 * - iOS: 83dp content height + home indicator safe area (~114dp total)
 *
 * The parent Box in WizlyApp.kt handles safe area padding via windowInsetsPadding.
 * This component receives the full 83dp for proper Material 3 NavigationBar layout.
 */
@Composable
fun WizlyBottomNavigation(
    currentTab: BottomNavTab,
    currentPageIndex: Int = 0, // pagerState.currentPage for smooth indicator
    pageOffsetFraction: Float = 0f, // pagerState.currentPageOffsetFraction for smooth indicator
    isSheetOpen: Boolean = false,
    fabScale: Float = 1f, // Scale for FAB during swipe animations
    showResearch: Boolean = false, // Master's: Research replaces Profile (Profile opens from Today)
    onTabSelected: (BottomNavTab) -> Unit,
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Haptic feedback for tab changes
    val haptic = rememberHapticFeedback()

    // Sliding indicator animation
    // Map tabs to horizontal offset (0.0 to 1.0, excluding center FAB space)
    // Layout: Home (0-20%) | Calendar (20-40%) | FAB (40-60%) | Progress (60-80%) | Profile (80-100%)
    // Tab positions: HOME=0.1, CALENDAR=0.3, PROGRESS=0.7, PROFILE=0.9
    val tabPositions = listOf(0.1f, 0.3f, 0.7f, 0.9f)

    // Calculate smooth indicator position based on page + offset fraction
    // When swiping, interpolate between current and next tab position
    val indicatorPosition = if (pageOffsetFraction != 0f && currentPageIndex in 0..3) {
        val currentPos = tabPositions[currentPageIndex]
        val nextIndex = (currentPageIndex + if (pageOffsetFraction > 0) 1 else -1).coerceIn(0, 3)
        val nextPos = tabPositions[nextIndex]
        // Interpolate between current and next position based on swipe progress
        currentPos + (nextPos - currentPos) * kotlin.math.abs(pageOffsetFraction)
    } else {
        // Fallback to discrete position when not swiping (e.g., on bottom nav tap)
        tabPositions.getOrElse(currentPageIndex) { 0.1f }
    }

    Surface(
        modifier =
            modifier
                .navigationBarsPadding() // Push above system safe area (gesture bar/home indicator)
                .offset(y = 12.dp) // Move 12dp closer to bottom edge to reduce gap
                .padding(horizontal = 12.dp), // Horizontal margins for floating effect
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp, // Increased for prominent 3D effect
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp), // Add rounded corners
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PlatformDimensions.bottomNavHeight) // 83dp Material 3 standard
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(24.dp), // Match surface shape
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Sliding indicator overlay
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PlatformDimensions.bottomNavHeight)
                        .graphicsLayer {
                            // Position indicator based on animated offset
                            // Pill starts at center (0.5), translate to target position
                            translationX = size.width * (indicatorPosition - 0.5f)
                        }
                        .zIndex(0f), // Place behind icons
            ) {
                // Indicator pill
                Box(
                    modifier =
                        Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .align(Alignment.Center)
                            .offset(y = -8.dp) // Position to cover icon only
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                            ),
                )
            }

            // Base navigation bar with 4 items (excluding center)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                // Optimized internal padding for better touch targets
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Today tab
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.TODAY,
                    onClick = {
                        haptic.performLight()
                        onTabSelected(BottomNavTab.TODAY)
                    },
                    enabled = !isSheetOpen, // Disable when bottom sheet is open
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Today",
                            modifier = Modifier.size(PlatformDimensions.bottomNavIconSize),
                        )
                    },
                    label = {
                        Text("Today", style = MaterialTheme.typography.labelSmall)
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent, // Use custom sliding indicator instead
                        ),
                    modifier = Modifier.weight(1f),
                )

                // Courses tab
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.COURSES,
                    onClick = {
                        haptic.performLight()
                        onTabSelected(BottomNavTab.COURSES)
                    },
                    enabled = !isSheetOpen, // Disable when bottom sheet is open
                    icon = {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Courses",
                            modifier = Modifier.size(PlatformDimensions.bottomNavIconSize),
                        )
                    },
                    label = {
                        Text("Courses", style = MaterialTheme.typography.labelSmall)
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent, // Use custom sliding indicator instead
                        ),
                    modifier = Modifier.weight(1f),
                )

                // Center spacer for FAB
                Box(modifier = Modifier.weight(1f))

                // Tutor tab
                NavigationBarItem(
                    selected = currentTab == BottomNavTab.TUTOR,
                    onClick = {
                        haptic.performLight()
                        onTabSelected(BottomNavTab.TUTOR)
                    },
                    enabled = !isSheetOpen, // Disable when bottom sheet is open
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Tutor",
                            modifier = Modifier.size(PlatformDimensions.bottomNavIconSize),
                        )
                    },
                    label = {
                        Text("Tutor", style = MaterialTheme.typography.labelSmall)
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent, // Use custom sliding indicator instead
                        ),
                    modifier = Modifier.weight(1f),
                )

                // Profile tab (Research for Master's)
                val lastTab = if (showResearch) BottomNavTab.RESEARCH else BottomNavTab.PROFILE
                val lastLabel = if (showResearch) "Research" else "Profile"
                NavigationBarItem(
                    selected = currentTab == lastTab,
                    onClick = {
                        haptic.performLight()
                        onTabSelected(lastTab)
                    },
                    enabled = !isSheetOpen, // Disable when bottom sheet is open
                    icon = {
                        Icon(
                            imageVector = if (showResearch) Icons.Default.Science else Icons.Default.Person,
                            contentDescription = lastLabel,
                            modifier = Modifier.size(PlatformDimensions.bottomNavIconSize),
                        )
                    },
                    label = {
                        Text(lastLabel, style = MaterialTheme.typography.labelSmall)
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent, // Use custom sliding indicator instead
                        ),
                    modifier = Modifier.weight(1f),
                )
            }

            // Clean single FAB (centered) - no decorative rings for sleek modern design
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center,
            ) {
                // Single clean FAB with reduced elevation and swipe scale animation
                FloatingActionButton(
                    onClick = {
                        if (isSheetOpen) {
                            onDismissSheet()
                        } else {
                            onTabSelected(BottomNavTab.SCAN)
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    elevation =
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp, // Reduced for sleeker appearance
                            pressedElevation = 8.dp,
                            focusedElevation = 6.dp,
                            hoveredElevation = 7.dp,
                        ),
                    shape = CircleShape,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                // Apply scale animation during swipe
                                scaleX = fabScale
                                scaleY = fabScale
                            },
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isSheetOpen) 45f else 0f,
                        label = "FAB icon rotation",
                    )

                    Icon(
                        imageVector = if (isSheetOpen) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isSheetOpen) "Close" else "Create",
                        modifier =
                            Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = rotation
                                },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun WizlyBottomNavigationPreview() {
    WizlyBottomNavigation(
        currentTab = BottomNavTab.TODAY,
        isSheetOpen = false,
        onTabSelected = TODO(),
        onDismissSheet = TODO(),
        modifier =
            Modifier
                .zIndex(2f), // Ensure nav stays on top
    )
}
