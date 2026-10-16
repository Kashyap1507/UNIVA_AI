package com.kashyap.wizlyai.util

import android.content.Context
import android.content.Intent

/**
 * Android implementation of ShareManager using Intent.ACTION_SEND
 */
actual object ShareManager {
    private var context: Context? = null

    actual fun initialize(context: Any) {
        if (context is Context) {
            this.context = context.applicationContext
        }
    }

    actual fun shareText(text: String, subject: String): Boolean {
        return try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share via").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context?.startActivity(chooserIntent)
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
