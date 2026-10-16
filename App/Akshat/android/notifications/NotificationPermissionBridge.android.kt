package com.kashyap.wizlyai.notifications

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Android implementation of NotificationPermissionBridge
 */
actual object NotificationPermissionBridge {
    private var activityRef: Activity? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    const val REQUEST_CODE_NOTIFICATION_PERMISSION = 3001

    /**
     * Set the activity reference for permission requests
     * Should be called from MainActivity
     */
    fun setActivity(activity: Activity?) {
        activityRef = activity
    }

    /**
     * Check if notification permission is granted
     */
    actual fun hasPermission(): Boolean {
        val context = activityRef ?: return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Request notification permission
     * For Android 13+ (API 33), requests POST_NOTIFICATIONS permission
     * For older versions, directs to app settings
     */
    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        val activity = activityRef
        if (activity == null) {
            onResult(false)
            return
        }

        permissionCallback = onResult

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Request POST_NOTIFICATIONS permission
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                onResult(true)
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION,
                )
            }
        } else {
            // Pre-Android 13 - Check if notifications are enabled
            if (hasPermission()) {
                onResult(true)
            } else {
                // Direct user to settings
                openAppSettings()
                // Can't determine result in this case
                onResult(false)
            }
        }
    }

    /**
     * Handle permission request result
     * Should be called from MainActivity.onRequestPermissionsResult
     */
    fun onPermissionResult(
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            val granted =
                grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            permissionCallback?.invoke(granted)
            permissionCallback = null
        }
    }

    /**
     * Open app notification settings
     */
    actual fun openAppSettings() {
        val activity = activityRef ?: return

        val intent =
            Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                    else -> {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        activity.startActivity(intent)
    }
}
