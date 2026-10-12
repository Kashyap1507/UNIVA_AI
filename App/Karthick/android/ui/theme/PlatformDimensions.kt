package com.kashyap.wizlyai.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android-specific dimensions following Material Design 3 standards
 */
actual object PlatformDimensions {
    /**
     * Bottom navigation bar height: 83dp (Material 3 80dp + 3dp extra top spacing)
     *
     * Note: This is the content height only. On devices with gesture navigation,
     * WindowInsets.navigationBars will add additional padding (typically 32-48dp)
     * to ensure the tab bar sits above the gesture bar.
     *
     * Total space = 83dp (content) + 32-48dp (gesture bar insets) = ~115-131dp
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
     * Bottom navigation icon size: 24dp (Material 3 standard)
     */
    actual val bottomNavIconSize: Dp = 24.dp
}
