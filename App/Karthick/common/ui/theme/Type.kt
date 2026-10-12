package com.kashyap.wizlyai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Wizly App Typography - Neutral Theme
 * Clean, modern typography system for minimalist design
 *
 * Font Weights:
 * - Normal (400): Body text, descriptions, paragraphs
 * - Medium (500): Subheadings, labels, secondary headers
 * - SemiBold (600): Primary headers, screen titles, emphasis
 * - Bold (700): Large metrics, data values (use sparingly)
 *
 * Base size: 16sp | Line height: 1.5x | Tight letter spacing
 */

val WizlyTypography =
    Typography(
        // Display - Extra large text (rarely used, for splash/onboarding)
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600 - Strong visual impact
                fontSize = 57.sp,
                lineHeight = 64.sp, // Tighter leading for large text
                letterSpacing = (-0.5).sp, // Negative tracking for large sizes
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.25).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        // Headline - Screen titles and major section headers
        // H1 - Screen titles (e.g., "Biology", "Fitness")
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold, // 700 - Maximum emphasis for screen titles
                fontSize = 34.sp, // Increased from 32sp for better hierarchy
                lineHeight = 41.sp, // 1.2x for tight, impactful headers
                letterSpacing = 0.sp,
            ),
        // H2 - Major section headers
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600
                fontSize = 28.sp, // Increased from 24sp
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        // H3 - Subsection headers
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600
                fontSize = 22.sp, // Increased from 20sp
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        // Title - Card titles and component headers
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600 - Emphasis for card titles
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold, // 600
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium, // 500 - Subtle titles
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body - Main content text (paragraphs, descriptions)
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal, // 400 - Regular reading text
                fontSize = 17.sp, // Slightly larger for better readability
                lineHeight = 26.sp, // 1.53x for comfortable reading
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal, // 400 - Standard body text
                fontSize = 15.sp, // Slightly increased
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal, // 400 - Supporting text
                fontSize = 13.sp, // Slightly increased
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            ),
        // Label - UI elements, buttons, badges, metadata
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium, // 500 - Button text, active labels
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium, // 500 - Small UI labels
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium, // 500 - Tiny labels, timestamps
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * Custom text styles for specific use cases
 */

// Large numeric values (e.g., "31.9" in VO2 Max card)
val DisplayMetric =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold, // 700 - Maximum emphasis for data
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    )

// Status badges (e.g., "Good", "Poor", "Stabilizing")
val StatusBadgeText =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold, // 600 - Clear readability in badges
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
    )
