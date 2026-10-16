package com.kashyap.wizlyai.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Android implementation of PermissionManager
 */
actual class PermissionManager(
    private val context: Context,
    private val activity: ComponentActivity,
) {
    private var onGrantedCallback: (() -> Unit)? = null
    private var onDeniedCallback: (() -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                onGrantedCallback?.invoke()
            } else {
                onDeniedCallback?.invoke()
            }
        }

    /**
     * Check if camera permission is granted
     */
    actual fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request camera permission
     */
    actual fun requestCameraPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        onGrantedCallback = onGranted
        onDeniedCallback = onDenied

        if (isCameraPermissionGranted()) {
            onGranted()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Check if should show permission rationale
     */
    actual fun shouldShowCameraRationale(): Boolean {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    /**
     * Open app settings
     */
    actual fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }
}
