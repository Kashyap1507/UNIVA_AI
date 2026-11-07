package com.kashyap.wizlyai.ui.viewmodel

import com.kashyap.wizlyai.data.repository.LocalDocumentStorage
import com.kashyap.wizlyai.data.repository.NoOpLocalDocumentStorage
import com.kashyap.wizlyai.ui.screens.scanning.CapturedImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Holds the pages captured for "Check my work" until they are sent:
 * camera/gallery → ScanReview (reorder, delete, add more) → upload.
 */
class ScanningViewModel(
    @Suppress("unused") private val localStorage: LocalDocumentStorage = NoOpLocalDocumentStorage(),
    @Suppress("unused") private val viewModelScope: CoroutineScope,
) {
    private val _reviewImages = MutableStateFlow<List<CapturedImageData>>(emptyList())
    val reviewImages: StateFlow<List<CapturedImageData>> = _reviewImages.asStateFlow()

    /**
     * Add captured image to review images list
     * @param imagePath Local file path of captured image
     */
    fun addReviewImage(imagePath: String) {
        val currentImages = _reviewImages.value
        val newImage =
            CapturedImageData(
                id = "${Clock.System.now().toEpochMilliseconds()}_${currentImages.size}",
                imagePath = imagePath,
                pageNumber = currentImages.size + 1,
            )
        _reviewImages.value = currentImages + newImage
    }

    /**
     * Remove image from review images list
     * @param index Index of image to remove
     */
    fun removeReviewImage(index: Int) {
        val currentImages = _reviewImages.value
        if (index in currentImages.indices) {
            _reviewImages.value =
                currentImages.filterIndexed { i, _ -> i != index }
                    .mapIndexed { newIndex, image -> image.copy(pageNumber = newIndex + 1) }
        }
    }

    /**
     * Reorder images in review images list
     * @param fromIndex Original index
     * @param toIndex Target index
     */
    fun reorderReviewImages(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val currentImages = _reviewImages.value.toMutableList()
        if (fromIndex in currentImages.indices && toIndex in currentImages.indices) {
            val item = currentImages.removeAt(fromIndex)
            currentImages.add(toIndex, item)
            _reviewImages.value = currentImages.mapIndexed { index, image -> image.copy(pageNumber = index + 1) }
        }
    }

    /**
     * Clear all review images
     */
    fun clearAllReviewImages() {
        _reviewImages.value = emptyList()
    }

    /**
     * Title from today's date, e.g. "Work - Oct 30, 2025"
     */
    fun generateAutoTitle(documentType: String = "Work"): String {
        val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthName = months.getOrElse(localDateTime.monthNumber - 1) { "Jan" }
        return "$documentType - $monthName ${localDateTime.dayOfMonth}, ${localDateTime.year}"
    }

    /**
     * Get current review images as image paths for document creation
     */
    fun getReviewImagePaths(): List<String> = _reviewImages.value.map { it.imagePath }
}
