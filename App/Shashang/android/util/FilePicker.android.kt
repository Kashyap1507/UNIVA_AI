package com.kashyap.wizlyai.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * Android document picker using ACTION_OPEN_DOCUMENT.
 * Must be constructed before the activity is STARTED (like GalleryPicker).
 */
actual class FilePicker(
    private val context: Context,
    activity: ComponentActivity,
) {
    private var onPicked: ((PickedFile) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val picked = uri?.let { copyToCache(it) }
            if (picked != null) onPicked?.invoke(picked) else onCancelled?.invoke()
        }

    actual fun pickDocument(
        onPicked: (PickedFile) -> Unit,
        onCancelled: () -> Unit,
        mimeTypes: List<String>,
    ) {
        this.onPicked = onPicked
        this.onCancelled = onCancelled
        launcher.launch(mimeTypes.toTypedArray())
    }

    private fun copyToCache(uri: Uri): PickedFile? {
        return try {
            val resolver = context.contentResolver
            val name =
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "material"
            val dir = File(context.cacheDir, "picked").apply { mkdirs() }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(dir, "${System.currentTimeMillis()}_$safeName")
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return null
            PickedFile(path = target.absolutePath, name = name, mimeType = resolver.getType(uri))
        } catch (e: Exception) {
            println("❌ FilePicker: could not copy picked file: ${e.message}")
            null
        }
    }
}
