package com.kashyap.wizlyai.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of GalleryPicker using ActivityResultContracts
 */
actual class GalleryPicker(
    private val context: Context,
    private val activity: ComponentActivity,
) {
    private var onImageSelectedCallback: ((String) -> Unit)? = null
    private var onImagesSelectedCallback: ((List<String>) -> Unit)? = null
    private var onCancelledCallback: (() -> Unit)? = null

    private val singleImagePicker: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                val imagePath = copyUriToCache(uri)
                onImageSelectedCallback?.invoke(imagePath)
            } else {
                onCancelledCallback?.invoke()
            }
        }

    private val multipleImagePicker: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val imagePaths = uris.map { copyUriToCache(it) }
                onImagesSelectedCallback?.invoke(imagePaths)
            } else {
                onCancelledCallback?.invoke()
            }
        }

    actual fun pickImage(
        onImageSelected: (String) -> Unit,
        onCancelled: () -> Unit,
    ) {
        onImageSelectedCallback = onImageSelected
        onCancelledCallback = onCancelled
        singleImagePicker.launch("image/*")
    }

    actual fun pickMultipleImages(
        maxImages: Int,
        onImagesSelected: (List<String>) -> Unit,
        onCancelled: () -> Unit,
    ) {
        onImagesSelectedCallback = onImagesSelected
        onCancelledCallback = onCancelled
        multipleImagePicker.launch("image/*")
    }

    actual fun isAvailable(): Boolean {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Copy image from content URI to app cache directory
     * Returns absolute file path
     */
    private fun copyUriToCache(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val fileName = "gallery_${System.currentTimeMillis()}.jpg"
        val outputFile = File(context.cacheDir, fileName)

        inputStream?.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        return outputFile.absolutePath
    }
}
