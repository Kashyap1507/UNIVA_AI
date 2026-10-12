package com.kashyap.wizlyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Wizly App Color Palette - Neutral Theme
 * Clean, minimalist design system with neutral grays and subtle accents
 * Inspired by modern fitness/wellness apps with card-based layouts
 */

// ============================================
// ACCENT COLORS (Minimal usage - critical actions only)
// ============================================

// Primary Accent (Blue-Teal) - Reserved for critical CTAs
val BluePrimary = Color(0xFF4F5BD5) // Calm indigo, the main accent
val TealPrimary = Color(0xFF2A9D8F) // Muted teal

// Primary Gradient Brushes - Use sparingly for key actions
val GradientBlueToTeal =
    Brush.linearGradient(
        colors = listOf(BluePrimary, TealPrimary),
    )

val GradientBlueToTealVertical =
    Brush.verticalGradient(
        colors = listOf(BluePrimary, TealPrimary),
    )

// Dark mode gradient colors (darker shades for auth screen backgrounds)
val BluePrimaryDark = Color(0xFF242B5C) // Dark indigo
val TealPrimaryDark = Color(0xFF14453F) // Dark teal

val GradientBlueToTealVerticalDark =
    Brush.verticalGradient(
        colors = listOf(BluePrimaryDark, TealPrimaryDark),
    )

/**
 * Theme-aware gradient for authentication screen backgrounds.
 * Returns a darker gradient in dark mode, vibrant gradient in light mode.
 */
@Composable
fun authBackgroundGradient(): Brush {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        GradientBlueToTealVerticalDark
    } else {
        GradientBlueToTealVertical
    }
}

// Secondary Gradient (Indigo to Purple) - Deprecated, use solid accents instead
@Deprecated("Use solid colors for cleaner neutral theme")
val IndigoPrimary = Color(0xFF6366F1) // Indigo #6366F1

@Deprecated("Use solid colors for cleaner neutral theme")
val PurplePrimary = Color(0xFF8B5CF6) // Purple #8B5CF6

@Deprecated("Use solid colors for cleaner neutral theme")
val GradientIndigoToPurple =
    Brush.linearGradient(
        colors = listOf(IndigoPrimary, PurplePrimary),
    )

@Deprecated("Use solid colors for cleaner neutral theme")
val GradientIndigoToPurpleVertical =
    Brush.verticalGradient(
        colors = listOf(IndigoPrimary, PurplePrimary),
    )

// ============================================
// NEUTRAL COLORS (Primary theme foundation)
// ============================================

// Background Colors - Light gray for "floating card" effect
val BackgroundPrimary = Color(0xFFF7F7F7) // Very light gray background (matches mocks)
val BackgroundLight = Color(0xFFF8F8F8) // Alternate light background
val CardBackground = Color(0xFFFFFFFF) // Pure white cards

// Text Colors - High contrast for readability
val Foreground = Color(0xFF1C1C1E) // Very dark gray/black (primary text)
val MutedText = Color(0xFF8E8E93) // Light gray (secondary text)
val TertiaryText = Color(0xFFAEAEB2) // Very light gray (tertiary text)

// Surface Colors
val InputBackground = Color(0xFFF2F2F7) // Light gray for input fields
val BorderLight = Color(0x1A000000) // rgba(0, 0, 0, 0.1) - 10% opacity
val DividerColor = Color(0xFFE5E5EA) // Subtle dividers

// ============================================
// SEMANTIC COLORS (Matching mock's style)
// ============================================

// Success/Positive - Teal/Mint (matches "Good" in mocks)
val Success = Color(0xFF2E9E7A) // Calm green
val SuccessLight = Color(0xFF34D399) // Lighter teal

// Error/Warning - Salmon/Coral (matches "Poor" in mocks)
val Error = Color(0xFFD9534F) // Muted red
val ErrorLight = Color(0xFFFF8787) // Lighter salmon

// Warning - Orange
val Warning = Color(0xFFF59E0B) // Amber-500 (unchanged)

// Info - Blue (use sparingly)
val Info = Color(0xFF4F5BD5) // Indigo

// ============================================
// LEGACY COLORS (For backwards compatibility)
// ============================================

// Secondary Colors - Deprecated, use semantic colors
@Deprecated("Use Success/SuccessLight for positive states")
val BlueLight = Color(0xFF93C5FD) // Blue-300

@Deprecated("Use BluePrimary for accents")
val BlueDark = Color(0xFF1E40AF) // Blue-800

@Deprecated("Use Success/SuccessLight instead")
val GreenLight = Color(0xFF6EE7B7) // Green-300

@Deprecated("Use Success for positive states")
val GreenDark = Color(0xFF047857) // Green-800

// ============================================
// SUBJECT COLORS (Badges and category indicators)
// ============================================

val MathColor = Color(0xFF3B82F6) // Blue
val ScienceColor = Color(0xFF00C896) // Teal/mint (updated to match success color)
val EnglishColor = Color(0xFF8B5CF6) // Purple
val HistoryColor = Color(0xFFF97316) // Orange
val ArtColor = Color(0xFFEC4899) // Pink

// ============================================
// TEXT COLORS (Updated for neutral theme)
// ============================================

val TextPrimary = Color(0xFF1C1C1E) // Very dark gray/black (matches Foreground)
val TextSecondary = Color(0xFF8E8E93) // Light gray (matches MutedText)
val TextTertiary = Color(0xFFAEAEB2) // Very light gray

// ============================================
// SURFACE COLORS (Backgrounds and cards)
// ============================================

val SurfaceWhite = Color(0xFFFFFFFF) // Pure white (cards)
val SurfaceGray = Color(0xFFF7F7F7) // Very light gray (background)
val SurfaceDark = Color(0xFF1F2937) // Dark gray (for dark mode)

// ============================================
// BORDER COLORS
// ============================================

val BorderMedium = Color(0xFFE5E5EA) // Light gray borders (updated for cleaner look)

// Event Type Colors (for Calendar)
val EventTest = Color(0xFFD9534F) // Muted red (exams)
val EventAssignment = Color(0xFFD98E2B) // Muted amber (assignments)
val EventGeneral = Color(0xFF4F5BD5) // Indigo

/** Calm accents for courses; a course always gets the same one. */
val CourseAccents =
    listOf(
        Color(0xFF4F5BD5), // indigo
        Color(0xFF2A9D8F), // teal
        Color(0xFF7A5FC9), // violet
        Color(0xFFC07A3A), // ochre
        Color(0xFF3F7CAC), // steel blue
        Color(0xFFB0577A), // plum
    )

/** Stable accent for a course code or name. */
fun courseAccentColor(key: String): Color {
    val k = key.trim().lowercase()
    if (k.isEmpty()) return Color.Gray
    return CourseAccents[(k.hashCode() and Int.MAX_VALUE) % CourseAccents.size]
}

/** Old K-12 name, kept for the Reports screen until Phase 13. */
fun getSubjectColor(subject: String): Color = courseAccentColor(subject)

// Backward Compatibility Aliases (will be removed in future versions)
@Deprecated("Use TealPrimary instead", ReplaceWith("TealPrimary"))
val GreenPrimary = TealPrimary

@Deprecated("Use GradientBlueToTeal instead", ReplaceWith("GradientBlueToTeal"))
val GradientBlueTOGreen = GradientBlueToTeal

@Deprecated("Use GradientBlueToTealVertical instead", ReplaceWith("GradientBlueToTealVertical"))
val GradientBlueToGreenVertical = GradientBlueToTealVertical

@Deprecated("Use BackgroundPrimary (pure white) instead", ReplaceWith("BackgroundPrimary"))
val BackgroundGradientStart = BackgroundPrimary

@Deprecated("Use BackgroundPrimary (pure white) instead", ReplaceWith("BackgroundPrimary"))
val BackgroundGradientEnd = BackgroundPrimary
