package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard App Bar Component (UX-003, UX-027)
 *
 * Standardized top app bar with consistent styling across all screens.
 * Addresses header inconsistencies identified in UX polish audit.
 *
 * Features:
 * - Consistent title styling (24sp, SemiBold, Material color)
 * - Standardized navigation icon (Back/Close)
 * - Optional styled navigation icon background
 * - Safe area inset handling for iOS notch/Dynamic Island
 * - Action button support
 * - Customizable background color
 *
 * Design Specification:
 * - Title: 24sp, SemiBold, onSurface color
 * - Navigation Icon: 48dp touch target
 * - Styled Navigation: CircleShape background, 48dp size
 * - Safe Area: Automatic system safe area insets (iOS notch/Dynamic Island)
 * - Background: Transparent default, customizable
 * - Actions: 48dp touch targets, 12dp spacing
 *
 * @param title Screen title text
 * @param onNavigationClick Navigation action (back/close)
 * @param navigationType Type of navigation icon (BACK, CLOSE, NONE)
 * @param styleNavigationIcon Whether to add background styling to navigation icon
 * @param navigationIconColor Color for styled navigation icon background
 * @param backgroundColor App bar background color (transparent default)
 * @param applySafeAreaInsets Whether to apply system safe area insets (default true)
 * @param actions Optional action buttons (share, menu, etc.)
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    navigationType: NavigationType = NavigationType.BACK,
    styleNavigationIcon: Boolean = false,
    navigationIconColor: Color = Color(0xFF6B7280), // Default gray
    backgroundColor: Color = Color.Transparent,
    applySafeAreaInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            when (navigationType) {
                NavigationType.BACK -> {
                    if (styleNavigationIcon) {
                        // Styled navigation icon with background
                        IconButton(
                            onClick = onNavigationClick,
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .size(48.dp)
                                    .background(
                                        color = navigationIconColor,
                                        shape = CircleShape,
                                    ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    } else {
                        // Simple navigation icon
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                }
                NavigationType.CLOSE -> {
                    if (styleNavigationIcon) {
                        // Styled close icon with background
                        IconButton(
                            onClick = onNavigationClick,
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .size(48.dp)
                                    .background(
                                        color = navigationIconColor,
                                        shape = CircleShape,
                                    ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                            )
                        }
                    } else {
                        // Simple close icon
                        IconButton(onClick = onNavigationClick) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                            )
                        }
                    }
                }
                NavigationType.NONE -> {
                    // No navigation icon
                }
            }
        },
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = backgroundColor,
            ),
        modifier =
            if (applySafeAreaInsets) {
                modifier.statusBarsPadding()
            } else {
                modifier
            },
    )
}

/**
 * Navigation icon type
 */
enum class NavigationType {
    BACK, // Back arrow
    CLOSE, // Close X
    NONE, // No navigation icon
}

/**
 * Convenience composable for standard back navigation
 */
@Composable
fun StandardAppBarWithBack(
    title: String,
    onBack: () -> Unit,
    backgroundColor: Color = Color.Transparent,
    applySafeAreaInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StandardAppBar(
        title = title,
        onNavigationClick = onBack,
        navigationType = NavigationType.BACK,
        styleNavigationIcon = false,
        backgroundColor = backgroundColor,
        applySafeAreaInsets = applySafeAreaInsets,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Convenience composable for styled close button (used in scan/review flows)
 */
@Composable
fun StandardAppBarWithStyledClose(
    title: String,
    onClose: () -> Unit,
    backgroundColor: Color = Color.Transparent,
    applySafeAreaInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StandardAppBar(
        title = title,
        onNavigationClick = onClose,
        navigationType = NavigationType.CLOSE,
        styleNavigationIcon = true,
        navigationIconColor = Color(0xFF6B7280),
        backgroundColor = backgroundColor,
        applySafeAreaInsets = applySafeAreaInsets,
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Convenience composable for styled back button
 */
@Composable
fun StandardAppBarWithStyledBack(
    title: String,
    onBack: () -> Unit,
    backgroundColor: Color = Color.Transparent,
    applySafeAreaInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StandardAppBar(
        title = title,
        onNavigationClick = onBack,
        navigationType = NavigationType.BACK,
        styleNavigationIcon = true,
        navigationIconColor = Color(0xFF6B7280),
        backgroundColor = backgroundColor,
        applySafeAreaInsets = applySafeAreaInsets,
        actions = actions,
        modifier = modifier,
    )
}
