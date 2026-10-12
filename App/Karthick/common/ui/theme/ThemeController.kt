package com.kashyap.wizlyai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.kashyap.wizlyai.data.preferences.PreferenceKeys
import com.kashyap.wizlyai.data.preferences.PreferencesManager

/**
 * Enum representing the theme mode preference.
 */
enum class ThemeMode(val storageValue: String) {
    Auto("auto"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ThemeMode {
            if (value.isNullOrBlank()) return Auto
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

internal const val DEFAULT_THEME_ID = "wizly_default"

/**
 * Data class describing a theme definition that bundles together the
 * light and dark color schemes for a given look.
 */
data class WizlyThemeDefinition(
    val id: String,
    val lightColors: androidx.compose.material3.ColorScheme,
    val darkColors: androidx.compose.material3.ColorScheme,
) {
    fun resolveColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme {
        return if (isDark) darkColors else lightColors
    }
}

/**
 * Registry of built-in Wizly themes. Designed to scale when new themes are introduced.
 */
object WizlyThemeCatalog {
    val Default: WizlyThemeDefinition by lazy {
        WizlyThemeDefinition(
            id = DEFAULT_THEME_ID,
            lightColors = WizlyPalettes.lightColorScheme,
            darkColors = WizlyPalettes.darkColorScheme,
        )
    }

    val all: List<WizlyThemeDefinition> by lazy {
        listOf(Default)
    }

    fun findById(id: String?): WizlyThemeDefinition {
        return all.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Controller that holds the current theme mode and definition. Persisted through PreferencesManager.
 */
@Stable
class ThemeController internal constructor(
    initialMode: ThemeMode,
    initialDefinition: WizlyThemeDefinition,
    private val preferencesManager: PreferencesManager?,
) {
    var themeMode by mutableStateOf(initialMode)
        private set

    var themeDefinition by mutableStateOf(initialDefinition)
        private set

    fun updateThemeMode(mode: ThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        preferencesManager?.putString(PreferenceKeys.APP_THEME_MODE, mode.storageValue)
    }

    fun updateThemeDefinition(definition: WizlyThemeDefinition) {
        if (definition == themeDefinition) return
        themeDefinition = definition
        preferencesManager?.putString(PreferenceKeys.APP_THEME_ID, definition.id)
    }
}

/**
 * remember helper to build a ThemeController that survives recompositions.
 */
@Composable
fun rememberThemeController(preferencesManager: PreferencesManager?): ThemeController {
    // Migrate old dark_mode boolean preference to new APP_THEME_MODE if needed
    preferencesManager?.let { prefs ->
        val storedMode = prefs.getString(PreferenceKeys.APP_THEME_MODE, "")
        if (storedMode.isEmpty()) {
            // No new preference exists - check for old dark_mode preference
            // Using a sentinel value to detect if the old preference was set
            val oldDarkMode = prefs.getBoolean("dark_mode", false)
            // Only migrate if the user had explicitly set dark mode
            // We can't distinguish between default false and user-set false,
            // so we'll migrate conservatively (false -> auto, true -> dark)
            val migratedMode = if (oldDarkMode) ThemeMode.Dark else ThemeMode.Auto
            prefs.putString(PreferenceKeys.APP_THEME_MODE, migratedMode.storageValue)
            // Remove old preference
            prefs.remove("dark_mode")
        }
    }

    val storedMode =
        preferencesManager?.getString(
            PreferenceKeys.APP_THEME_MODE,
            ThemeMode.Auto.storageValue,
        )
    val initialMode = ThemeMode.fromStorageValue(storedMode)
    val storedThemeId =
        preferencesManager?.getString(
            PreferenceKeys.APP_THEME_ID,
            DEFAULT_THEME_ID,
        )
    val initialDefinition = WizlyThemeCatalog.findById(storedThemeId)
    return remember(preferencesManager) {
        ThemeController(
            initialMode = initialMode,
            initialDefinition = initialDefinition,
            preferencesManager = preferencesManager,
        )
    }
}

/**
 * CompositionLocal exposing the ThemeController to the UI tree.
 */
val LocalThemeController =
    staticCompositionLocalOf<ThemeController> {
        error("ThemeController not provided. Make sure to wrap your content in WizlyTheme.")
    }
