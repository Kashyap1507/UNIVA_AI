package com.kashyap.wizlyai.ios

import com.kashyap.wizlyai.BootstrapSyncCallbacks
import com.kashyap.wizlyai.CalendarCallbacks
import com.kashyap.wizlyai.CameraCallbacks
import com.kashyap.wizlyai.DocumentQueryCallbacks
import com.kashyap.wizlyai.DocumentStorageCallbacks
import com.kashyap.wizlyai.EdgeDetectorCallbacks
import com.kashyap.wizlyai.ExifReaderCallbacks
import com.kashyap.wizlyai.GalleryPickerCallbacks
import com.kashyap.wizlyai.ImageQualityProcessorCallbacks
import com.kashyap.wizlyai.NetworkDetectorCallbacks
import com.kashyap.wizlyai.NotificationCallbacks
import com.kashyap.wizlyai.PermissionManagerCallbacks

/**
 * Singleton bridge to hold iOS Swift callbacks
 * This allows Kotlin code to call Swift implementations
 */
internal object IOSCallbackBridge {
    private var bootstrapSyncCallbacks: BootstrapSyncCallbacks? = null
    private var documentStorageCallbacks: DocumentStorageCallbacks? = null
    private var documentQueryCallbacks: DocumentQueryCallbacks? = null
    private var networkDetectorCallbacks: NetworkDetectorCallbacks? = null
    private var imageQualityProcessorCallbacks: ImageQualityProcessorCallbacks? = null
    private var edgeDetectorCallbacks: EdgeDetectorCallbacks? = null
    private var cameraCallbacks: CameraCallbacks? = null
    private var permissionManagerCallbacks: PermissionManagerCallbacks? = null
    private var galleryPickerCallbacks: GalleryPickerCallbacks? = null
    private var exifReaderCallbacks: ExifReaderCallbacks? = null
    private var calendarCallbacks: CalendarCallbacks? = null
    private var notificationCallbacks: NotificationCallbacks? = null

    /**
     * Set bootstrap sync callbacks (called from MainViewController)
     */
    fun setBootstrapSyncCallbacks(callbacks: BootstrapSyncCallbacks) {
        bootstrapSyncCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Bootstrap sync callbacks set")
    }

    /**
     * Set document storage callbacks (called from MainViewController)
     */
    fun setDocumentStorageCallbacks(callbacks: DocumentStorageCallbacks) {
        documentStorageCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Document storage callbacks set")
    }

    /**
     * Set document query callbacks (called from MainViewController)
     */
    fun setDocumentQueryCallbacks(callbacks: DocumentQueryCallbacks) {
        documentQueryCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Document query callbacks set")
    }

    /**
     * Set network detector callbacks (called from MainViewController)
     */
    fun setNetworkDetectorCallbacks(callbacks: NetworkDetectorCallbacks) {
        networkDetectorCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Network detector callbacks set")
    }

    /**
     * Set image quality processor callbacks (called from MainViewController)
     */
    fun setImageQualityProcessorCallbacks(callbacks: ImageQualityProcessorCallbacks) {
        imageQualityProcessorCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Image quality processor callbacks set")
    }

    /**
     * Get bootstrap sync callbacks
     */
    fun getBootstrapSyncCallbacks(): BootstrapSyncCallbacks? {
        return bootstrapSyncCallbacks
    }

    /**
     * Get document storage callbacks
     */
    fun getDocumentStorageCallbacks(): DocumentStorageCallbacks? {
        return documentStorageCallbacks
    }

    /**
     * Get document query callbacks
     */
    fun getDocumentQueryCallbacks(): DocumentQueryCallbacks? {
        return documentQueryCallbacks
    }

    /**
     * Get network detector callbacks
     */
    fun getNetworkDetectorCallbacks(): NetworkDetectorCallbacks? {
        return networkDetectorCallbacks
    }

    /**
     * Get image quality processor callbacks
     */
    fun getImageQualityProcessorCallbacks(): ImageQualityProcessorCallbacks? {
        return imageQualityProcessorCallbacks
    }

    /**
     * Set edge detector callbacks (called from MainViewController)
     */
    fun setEdgeDetectorCallbacks(callbacks: EdgeDetectorCallbacks) {
        edgeDetectorCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Edge detector callbacks set")
    }

    /**
     * Get edge detector callbacks
     */
    fun getEdgeDetectorCallbacks(): EdgeDetectorCallbacks? {
        return edgeDetectorCallbacks
    }

    /**
     * Set camera callbacks (called from MainViewController)
     */
    fun setCameraCallbacks(callbacks: CameraCallbacks) {
        cameraCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Camera callbacks set")
    }

    /**
     * Get camera callbacks
     */
    fun getCameraCallbacks(): CameraCallbacks? {
        return cameraCallbacks
    }

    /**
     * Set permission manager callbacks (called from MainViewController)
     */
    fun setPermissionManagerCallbacks(callbacks: PermissionManagerCallbacks) {
        permissionManagerCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Permission manager callbacks set")
    }

    /**
     * Get permission manager callbacks
     */
    fun getPermissionManagerCallbacks(): PermissionManagerCallbacks? {
        return permissionManagerCallbacks
    }

    /**
     * Set gallery picker callbacks (called from MainViewController)
     */
    fun setGalleryPickerCallbacks(callbacks: GalleryPickerCallbacks) {
        galleryPickerCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Gallery picker callbacks set")
    }

    /**
     * Get gallery picker callbacks
     */
    fun getGalleryPickerCallbacks(): GalleryPickerCallbacks? {
        return galleryPickerCallbacks
    }

    /**
     * Set EXIF reader callbacks (called from MainViewController)
     */
    fun setExifReaderCallbacks(callbacks: ExifReaderCallbacks) {
        exifReaderCallbacks = callbacks
        println("🔗 IOSCallbackBridge: EXIF reader callbacks set")
    }

    /**
     * Get EXIF reader callbacks
     */
    fun getExifReaderCallbacks(): ExifReaderCallbacks? {
        return exifReaderCallbacks
    }

    /**
     * Set calendar callbacks (called from MainViewController)
     */
    fun setCalendarCallbacks(callbacks: CalendarCallbacks) {
        calendarCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Calendar callbacks set")
    }

    /**
     * Get calendar callbacks
     */
    fun getCalendarCallbacks(): CalendarCallbacks? {
        return calendarCallbacks
    }

    /**
     * Set notification callbacks (called from MainViewController)
     */
    fun setNotificationCallbacks(callbacks: NotificationCallbacks) {
        notificationCallbacks = callbacks
        println("🔗 IOSCallbackBridge: Notification callbacks set")
    }

    /**
     * Get notification callbacks
     */
    fun getNotificationCallbacks(): NotificationCallbacks? {
        return notificationCallbacks
    }

    /**
     * Clear all callbacks (called on app termination)
     */
    fun clear() {
        bootstrapSyncCallbacks = null
        documentStorageCallbacks = null
        documentQueryCallbacks = null
        networkDetectorCallbacks = null
        imageQualityProcessorCallbacks = null
        edgeDetectorCallbacks = null
        cameraCallbacks = null
        permissionManagerCallbacks = null
        galleryPickerCallbacks = null
        exifReaderCallbacks = null
        calendarCallbacks = null
        notificationCallbacks = null
        println("🧹 IOSCallbackBridge: All callbacks cleared")
    }
}
