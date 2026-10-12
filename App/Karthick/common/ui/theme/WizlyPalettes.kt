package com.kashyap.wizlyai.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Centralized definition of Wizly light & dark palettes.
 * Calm productivity theme: neutral surfaces, one indigo accent, muted status colours
 */
object WizlyPalettes {
    val lightColorScheme =
        lightColorScheme(
            // Primary accent - Blue-Teal (use sparingly for critical CTAs)
            primary = BluePrimary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE6E8FA), // Very light indigo
            onPrimaryContainer = Color(0xFF1E2466), // Dark indigo
            // Secondary accent - Teal/Mint (success/positive states)
            secondary = Success, // Teal/mint #00C896
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD7F0E7), // Very light green
            onSecondaryContainer = Color(0xFF004D3D), // Dark teal
            // Tertiary accent - Purple (minimal use)
            tertiary = Color(0xFF7A5FC9), // Muted violet
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF3E8FF),
            onTertiaryContainer = Color(0xFF4C1D95),
            // Error colors - Salmon/Coral
            error = Error, // Salmon/coral #FF6B6B
            onError = Color.White,
            errorContainer = Color(0xFFF9E3E2),
            onErrorContainer = Color(0xFF8B0000),
            // Background - Very light gray for "floating card" effect
            background = BackgroundPrimary, // #F7F7F7
            onBackground = TextPrimary, // #1C1C1E
            // Surface - Pure white cards
            surface = CardBackground, // Pure white
            onSurface = TextPrimary, // #1C1C1E
            surfaceVariant = SurfaceGray, // #F7F7F7
            onSurfaceVariant = TextSecondary, // #8E8E93
            // Borders & Outlines - Subtle gray
            outline = BorderMedium, // #E5E5EA
            outlineVariant = Color(0xFFE5E5EA),
            scrim = Color(0x80000000), // 50% black
            inverseSurface = Color(0xFF1C1C1E),
            inverseOnSurface = Color(0xFFF5F5F7),
            inversePrimary = Color(0xFFA9B1F5), // Light indigo
        )

    val darkColorScheme =
        darkColorScheme(
            // Primary accent - Light blue for visibility on dark backgrounds
            primary = Color(0xFFA9B1F5), // Light indigo
            onPrimary = Color(0xFF151A4D),
            primaryContainer = Color(0xFF2F367F),
            onPrimaryContainer = Color(0xFFE0E3FF),
            // Secondary accent - Light teal (success/positive)
            secondary = Color(0xFF6CC9A8), // Soft green
            onSecondary = Color(0xFF023833),
            secondaryContainer = Color(0xFF1D5644),
            onSecondaryContainer = Color(0xFFD1FAE5),
            // Tertiary accent - Light purple
            tertiary = Color(0xFFC7B8FF),
            onTertiary = Color(0xFF1F1245),
            tertiaryContainer = Color(0xFF4C1D95),
            onTertiaryContainer = Color(0xFFF3E8FF),
            // Error colors - Light salmon/coral
            error = Color(0xFFF08A86), // Soft red
            onError = Color(0xFF680003),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            // Background - Very dark gray (almost black)
            background = Color(0xFF111216), // Deep slate background
            onBackground = Color(0xFFF5F5F7), // Very light text
            // Surface - Dark gray cards (elevated from background)
            surface = Color(0xFF1B1D23), // Slate cards
            onSurface = Color(0xFFF5F5F7), // Very light text
            surfaceVariant = Color(0xFF262932), // Slightly lighter surface
            onSurfaceVariant = Color(0xFF9EA3AE), // Gray text
            // Borders & Outlines - Subtle gray borders
            outline = Color(0xFF38383A), // Dark gray borders
            outlineVariant = Color(0xFF2C2C2E), // Very dark borders
            // Scrim & Inverse
            scrim = Color(0xCC000000), // 80% black
            inverseSurface = Color(0xFFF5F5F7),
            inverseOnSurface = Color(0xFF1C1C1E),
            inversePrimary = Color(0xFF4F5BD5), // Indigo primary
        )
}
