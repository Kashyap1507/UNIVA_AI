package com.kashyap.wizlyai.ui.theme

import androidx.compose.ui.unit.Dp

/**
 * Platform-specific dimensions following iOS HIG and Android Material Design guidelines
 */
expect object PlatformDimensions {
    /**
     * Bottom navigation bar height
     * - iOS: 49dp (Apple HIG standard for tab bars)
     * - Android: 56dp (Material 3 compact standard)
     */
    val bottomNavHeight: Dp

    /**
     * Bottom navigation icon size
     */
    val bottomNavIconSize: Dp
}
