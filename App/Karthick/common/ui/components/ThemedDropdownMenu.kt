package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kashyap.wizlyai.ui.theme.rememberDialogSurfaceGradient

/**
 * Themed DropdownMenu with gradient background matching bottom sheet styling.
 *
 * Drop-in replacement for Material 3 DropdownMenu with unified gradient background.
 *
 * Colors match the bottom sheet gradient:
 * - Light mode: #F7F7F7 → #EDEDED (subtle shadow + border for definition)
 * - Dark mode: #1C1C1E → #141414 (more prominent shadow for overlay feel)
 *
 * @param expanded Whether the menu is currently visible
 * @param onDismissRequest Called when the menu should be dismissed
 * @param modifier Modifier for the menu
 * @param offset Offset from the anchor
 * @param properties Popup properties
 * @param content The menu content (DropdownMenuItems)
 */
@Composable
fun ThemedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val gradientBrush = rememberDialogSurfaceGradient()
    val shape = RoundedCornerShape(12.dp)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Shadow and border styling based on theme
    val shadowElevation = if (isDarkTheme) 8.dp else 2.dp
    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    // Override Material3 surface colors to allow gradient background
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = Color.Transparent,
            surfaceContainer = Color.Transparent,
        ),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = if (isDarkTheme) Color.Black else Color.Black.copy(alpha = 0.15f),
                    spotColor = if (isDarkTheme) Color.Black else Color.Black.copy(alpha = 0.15f),
                )
                .clip(shape)
                .background(brush = gradientBrush, shape = shape)
                .border(width = 1.dp, color = borderColor, shape = shape),
            offset = offset,
            properties = properties,
            content = content,
        )
    }
}

/**
 * Extension function to calculate color luminance for dark mode detection
 */
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
