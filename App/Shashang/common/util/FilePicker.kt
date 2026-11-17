package com.kashyap.wizlyai.util

/** A file the student picked, copied into the app's cache directory. */
data class PickedFile(
    val path: String,
    val name: String,
    val mimeType: String?,
)

/** MIME types accepted for course materials (must match the server). */
val COURSE_MATERIAL_MIME_TYPES =
    listOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/markdown",
        "image/jpeg",
        "image/png",
    )

/** Calendar exports (.ics). Some file managers report them as octet-stream; the server checks the content. */
val CALENDAR_MIME_TYPES = listOf("text/calendar", "application/ics", "text/x-vcalendar", "application/octet-stream")

/**
 * System document picker (PDF, PowerPoint, text, images by default; [mimeTypes] narrows or widens it).
 * Android: Storage Access Framework. iOS: UIDocumentPickerViewController via Swift.
 */
expect class FilePicker {
    fun pickDocument(
        onPicked: (PickedFile) -> Unit,
        onCancelled: () -> Unit,
        mimeTypes: List<String> = COURSE_MATERIAL_MIME_TYPES,
    )
}
