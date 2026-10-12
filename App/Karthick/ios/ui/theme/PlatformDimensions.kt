package com.kashyap.wizlyai.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS-specific dimensions following Material Design 3 standards for cross-platform consistency
 */
actual object PlatformDimensions {
    /**
     * Bottom navigation bar height: 83dp (Material 3 80dp + 3dp extra top spacing)
     *
     * Note: This is the content height only. On modern iPhones without a home button,
     * WindowInsets.navigationBars will add the home indicator safe area (typically 34dp)
     * to ensure the tab bar sits above the home indicator.
     *
     * Total space:
     * - iPhone with home button: 83dp
     * - iPhone without home button (X, 11, 12+): 83dp + 34dp = 117dp
     *
     * Material 3 NavigationBar with both icons and labels requires 80dp base height.
     * Additional 3dp added for comfortable top spacing to prevent icon cutoff:
     * - Top padding (~15dp with extra space)
     * - Icon (24dp)
     * - Icon-to-label spacing (~4dp)
     * - Label text (~16-18dp)
     * - Bottom padding (~12-14dp)
     */
    actual val bottomNavHeight: Dp = 64.dp // Reduced for sleeker appearance

    /**
     * Bottom navigation icon size: 24dp (scaled for iOS tab bar icons)
     */
    actual val bottomNavIconSize: Dp = 24.dp
}
