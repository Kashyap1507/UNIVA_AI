package com.kashyap.wizlyai.util

/**
 * Platform-agnostic clipboard manager for copying text to clipboard
 * Uses expect/actual pattern for platform-specific implementations
 */
expect object ClipboardManager {
    /**
     * Initialize the clipboard manager with context (Android only)
     * No-op on iOS
     */
    fun initialize(context: Any)

    /**
     * Copy text to clipboard
     * @param text Text to copy
     * @param label Optional label for the clipboard entry (Android only)
     * @return true if successful, false otherwise
     */
    fun copyToClipboard(text: String, label: String = "Univa AI"): Boolean

    /**
     * Show a native toast/snackbar message to user
     * @param message Message to display
     */
    fun showCopiedMessage(message: String)
}
