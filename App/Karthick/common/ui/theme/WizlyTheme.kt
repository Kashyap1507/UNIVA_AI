package com.kashyap.wizlyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.kashyap.wizlyai.data.preferences.PreferencesManager

/**
 * Root theme wrapper for the Wizly app.
 *
 * The theme is driven by two independent levers:
 *  - ThemeMode: Auto / Light / Dark (user preference)
 *  - ThemeDefinition: actual palette to use (currently a single default palette)
 *
 * The structure makes it easy to plug in additional theme definitions in the future
 * while keeping the mode handling centralized.
 */
@Composable
fun WizlyTheme(
    themeController: ThemeController,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val themeMode = themeController.themeMode
    val themeDefinition = themeController.themeDefinition
    val resolvedDark =
        remember(systemDark, themeMode) {
            when (themeMode) {
                ThemeMode.Auto -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
        }
    val colorScheme =
        remember(themeDefinition, resolvedDark) {
            themeDefinition.resolveColorScheme(isDark = resolvedDark)
        }

    CompositionLocalProvider(
        LocalThemeController provides themeController,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WizlyTypography,
            content = content,
        )
    }
}

/**
 * Convenience overload to allow callers to supply an optional PreferencesManager directly.
 * If none is provided, the theme will default to auto-following the system appearance.
 */
@Composable
fun WizlyTheme(
    preferencesManager: PreferencesManager? = null,
    content: @Composable () -> Unit,
) {
    val controller = rememberThemeController(preferencesManager)
    WizlyTheme(
        themeController = controller,
        content = content,
    )
}
