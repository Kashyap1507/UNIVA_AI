package com.kashyap.wizlyai.util

/**
 * Platform-specific app restart utility
 * Restarts the application to apply new settings
 */
expect object AppRestarter {
    /**
     * Restart the application
     * On Android: Restarts MainActivity
     * On iOS: User must manually restart the app
     */
    fun restartApp()
}
