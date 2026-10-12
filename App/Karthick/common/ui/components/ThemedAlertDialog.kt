package com.kashyap.wizlyai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kashyap.wizlyai.ui.theme.rememberDialogSurfaceGradient

/**
 * Themed AlertDialog with gradient background matching bottom sheet styling.
 *
 * Drop-in replacement for Material 3 AlertDialog with unified gradient background.
 *
 * Colors match the bottom sheet gradient:
 * - Light mode: #F7F7F7 → #EDEDED
 * - Dark mode: #1C1C1E → #141414
 *
 * @param onDismissRequest Called when the dialog should be dismissed
 * @param confirmButton Button shown on the right side
 * @param dismissButton Optional button shown on the left side
 * @param icon Optional icon shown at the top center
 * @param title Optional title text
 * @param text Optional body text
 * @param modifier Modifier for the dialog
 * @param shape Shape of the dialog (default: 28.dp rounded corners)
 * @param properties Dialog properties
 */
@Composable
fun ThemedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    properties: DialogProperties = DialogProperties(),
) {
    val gradientBrush = rememberDialogSurfaceGradient()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            modifier = modifier.wrapContentSize(),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = gradientBrush,
                        shape = shape,
                    ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 560.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Icon
                        if (icon != null) {
                            Box(
                                modifier = Modifier.padding(bottom = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                icon()
                            }
                        }

                        // Title
                        if (title != null) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .align(Alignment.Start),
                                ) {
                                    title()
                                }
                            }
                        }

                        // Text/Content
                        if (text != null) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .align(Alignment.Start),
                                ) {
                                    text()
                                }
                            }
                        }

                        // Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
