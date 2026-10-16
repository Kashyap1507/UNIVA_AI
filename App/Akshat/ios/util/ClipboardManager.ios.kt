package com.kashyap.wizlyai.util

import platform.UIKit.UIPasteboard
import platform.UIKit.UIApplication
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault

/**
 * iOS implementation of ClipboardManager using UIPasteboard
 */
actual object ClipboardManager {
    actual fun initialize(context: Any) {
        // No initialization needed for iOS
    }

    actual fun copyToClipboard(text: String, label: String): Boolean {
        return try {
            UIPasteboard.generalPasteboard.string = text
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun showCopiedMessage(message: String) {
        try {
            val alert = UIAlertController.alertControllerWithTitle(
                title = null,
                message = message,
                preferredStyle = UIAlertControllerStyleAlert
            )

            val okAction = UIAlertAction.actionWithTitle(
                title = "OK",
                style = UIAlertActionStyleDefault,
                handler = null
            )
            alert.addAction(okAction)

            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(alert, animated = true, completion = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
