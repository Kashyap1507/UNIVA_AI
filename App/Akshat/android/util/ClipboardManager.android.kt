package com.kashyap.wizlyai.util

import android.content.ClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Android implementation of ClipboardManager using Android ClipboardManager service
 */
actual object ClipboardManager {
    private var context: Context? = null
    private var clipboardManager: AndroidClipboardManager? = null

    actual fun initialize(context: Any) {
        if (context is Context) {
            this.context = context.applicationContext
            this.clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? AndroidClipboardManager
        }
    }

    actual fun copyToClipboard(text: String, label: String): Boolean {
        return try {
            val clip = ClipData.newPlainText(label, text)
            clipboardManager?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun showCopiedMessage(message: String) {
        context?.let { ctx ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}
