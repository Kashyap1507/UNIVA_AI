package com.kashyap.wizlyai.util

/**
 * iOS implementation of AppRestarter
 * Note: iOS apps cannot programmatically restart themselves
 * User must manually close and reopen the app
 */
actual object AppRestarter {
    /**
     * On iOS, apps cannot restart themselves programmatically
     * This function logs a message - user must manually restart
     */
    actual fun restartApp() {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔄 [APP RESTART] iOS - User must manually restart the app")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        // iOS doesn't allow programmatic app restart
        // User needs to manually close and reopen the app
    }
}
