package com.kashyap.wizlyai.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator

actual class HapticFeedback {
    actual fun performLight() {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
        generator.prepare()
        generator.impactOccurred()
    }

    actual fun performMedium() {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        generator.prepare()
        generator.impactOccurred()
    }

    actual fun performHeavy() {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
        generator.prepare()
        generator.impactOccurred()
    }

    actual fun performSuccess() {
        // Use notification feedback for success (matches iOS system patterns)
        val generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }
}

@Composable
actual fun rememberHapticFeedback(): HapticFeedback {
    return remember { HapticFeedback() }
}
