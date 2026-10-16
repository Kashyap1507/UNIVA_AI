package com.kashyap.wizlyai.util

import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of ShareManager using UIActivityViewController
 */
actual object ShareManager {
    actual fun initialize(context: Any) {
        // No initialization needed for iOS
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun shareText(text: String, subject: String): Boolean {
        return try {
            val items = listOf(text)
            val activityViewController = UIActivityViewController(
                activityItems = items,
                applicationActivities = null
            )

            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(
                activityViewController,
                animated = true,
                completion = null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun getReferralShareMessage(referralCode: String, userName: String?): String {
        val greeting = if (userName != null) {
            "Hi! $userName here."
        } else {
            "Hi there!"
        }

        return """
            $greeting

            I'm using Wizly AI for my courses. It turns my lecture notes and slides into a tutor, flashcards and practice exams.

            Use my referral code to join: $referralCode

            Download Wizly AI and study smarter this semester!
        """.trimIndent()
    }
}
