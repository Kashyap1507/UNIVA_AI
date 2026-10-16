package com.kashyap.wizlyai.util

import android.app.Activity
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

/**
 * Android implementation of AppRestarter
 * Restarts the app by killing the process and relaunching MainActivity
 */
actual object AppRestarter {
    private var activity: Activity? = null

    /**
     * Set the current activity reference
     * Call this from MainActivity.onCreate()
     */
    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    /**
     * Restart the application
     * Kills the current process and launches a new instance
     */
    actual fun restartApp() {
        activity?.let { context ->
            // Log restart event
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("🔄 [APP RESTART] Restarting application...")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Create intent to restart the app
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

            // Start the new activity
            context.startActivity(intent)

            // Kill the current process to ensure clean restart
            Process.killProcess(Process.myPid())
            exitProcess(0)
        } ?: run {
            println("⚠️ [APP RESTART] Cannot restart - activity reference not set")
        }
    }
}
