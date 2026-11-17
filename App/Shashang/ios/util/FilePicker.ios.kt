package com.kashyap.wizlyai.util

/**
 * iOS document picker. Swift registers [IOSFilePickerHandler.handler] at
 * startup (ContentView.swift) with a UIDocumentPickerViewController-based
 * implementation that copies the file into the app's temp directory.
 */
actual class FilePicker {
    actual fun pickDocument(
        onPicked: (PickedFile) -> Unit,
        onCancelled: () -> Unit,
        mimeTypes: List<String>,
    ) {
        val handler = IOSFilePickerHandler.handler
        if (handler == null) {
            println("⚠️ iOS FilePicker: handler not registered")
            onCancelled()
            return
        }
        handler({ path, name, mime -> onPicked(PickedFile(path, name, mime)) }, onCancelled, mimeTypes)
    }
}

/** Set from Swift: `IOSFilePickerHandler.shared.handler = { onPicked, onCancelled, mimeTypes in ... }` */
object IOSFilePickerHandler {
    var handler: ((onPicked: (path: String, name: String, mimeType: String?) -> Unit, onCancelled: () -> Unit, mimeTypes: List<String>) -> Unit)? = null
}
