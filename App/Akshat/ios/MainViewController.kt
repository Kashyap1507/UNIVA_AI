package com.kashyap.wizlyai

import androidx.compose.ui.window.ComposeUIViewController
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.repository.createAppSettingsRepository
import com.kashyap.wizlyai.data.repository.createIOSLocalDocumentStorage
import com.kashyap.wizlyai.ios.IOSCallbackBridge
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.WizlyApp
import com.kashyap.wizlyai.ui.screens.documents.createIOSDocumentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * Bootstrap sync callbacks passed from Swift layer
 */
data class BootstrapSyncCallbacks(
    val syncBootstrapData: (bootstrapData: com.kashyap.wizlyai.network.models.BootstrapData, userId: String) -> Unit,
    val clearSyncData: () -> Unit,
    val smartSync: (force: Boolean) -> Unit,
)

/**
 * Document storage callbacks passed from Swift layer
 * Using separate success/error callbacks instead of Result<T> for easier Swift interop
 */
data class DocumentStorageCallbacks(
    val saveDocument: (
        userId: String,
        title: String,
        type: com.kashyap.wizlyai.data.models.DocumentType,
        imagePaths: List<String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val addPage: (documentId: String, imagePath: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) -> Unit,
    val deletePage: (
        documentId: String,
        pageId: String,
        userId: String,
        pageNumber: Int,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val deleteDocument: (documentId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) -> Unit,
    val getDocument: (
        documentId: String,
        onSuccess: (com.kashyap.wizlyai.data.models.Document) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val markForSync: (documentId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) -> Unit,
    val getStorageUsed: (completion: (Long) -> Unit) -> Unit,
)

/**
 * Document query callbacks passed from Swift layer
 * Used for querying lists of documents
 */
data class DocumentQueryCallbacks(
    val getDocuments: (
        userId: String,
        onSuccess: (List<com.kashyap.wizlyai.data.models.Document>) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val searchDocuments: (
        userId: String,
        query: String,
        limit: Int,
        onSuccess: (List<com.kashyap.wizlyai.data.models.Document>) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val getDocumentCount: (
        userId: String,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    val getDocumentWithPages: (
        documentId: String,
        onSuccess: (com.kashyap.wizlyai.data.repository.DocumentWithPages) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
)

/**
 * Network detector callbacks passed from Swift layer
 * Used for checking network status and cellular connectivity
 */
data class NetworkDetectorCallbacks(
    val isOnline: (completion: (Boolean) -> Unit) -> Unit,
    val isOnWiFi: (completion: (Boolean) -> Unit) -> Unit,
    val isOnCellular: (completion: (Boolean) -> Unit) -> Unit,
    val isOnEthernet: (completion: (Boolean) -> Unit) -> Unit,
    val getNetworkType: (completion: (String) -> Unit) -> Unit,
    val isMeteredConnection: (completion: (Boolean) -> Unit) -> Unit,
)

/**
 * Image quality processor callbacks passed from Swift layer
 * Used for processing captured images with quality presets
 */
data class ImageQualityProcessorCallbacks(
    val processImage: (
        inputPath: String,
        quality: com.kashyap.wizlyai.data.models.ScanQuality,
        outputPath: String?,
        completion: (Result<String>) -> Unit,
    ) -> Unit,
    val shouldProcess: (
        imagePath: String,
        quality: com.kashyap.wizlyai.data.models.ScanQuality,
        completion: (Boolean) -> Unit,
    ) -> Unit,
    val getImageDimensions: (
        imagePath: String,
        completion: (Int, Int) -> Unit,
    ) -> Unit,
)

/**
 * Edge detector callbacks passed from Swift layer
 * Used for document edge detection and perspective transform using Vision framework
 */
data class EdgeDetectorCallbacks(
    val detectEdges: (
        imagePath: String,
        onSuccess: (
            topLeftX: Float,
            topLeftY: Float,
            topRightX: Float,
            topRightY: Float,
            bottomRightX: Float,
            bottomRightY: Float,
            bottomLeftX: Float,
            bottomLeftY: Float,
            confidence: String,
        ) -> Unit,
        onFailure: () -> Unit,
    ) -> Unit,
    val applyPerspectiveTransform: (
        imagePath: String,
        topLeftX: Double,
        topLeftY: Double,
        topRightX: Double,
        topRightY: Double,
        bottomRightX: Double,
        bottomRightY: Double,
        bottomLeftX: Double,
        bottomLeftY: Double,
        rotation: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    val getDefaultCorners: (
        imageWidth: Int,
        imageHeight: Int,
        onResult: (
            topLeftX: Float,
            topLeftY: Float,
            topRightX: Float,
            topRightY: Float,
            bottomRightX: Float,
            bottomRightY: Float,
            bottomLeftX: Float,
            bottomLeftY: Float,
        ) -> Unit,
    ) -> Unit,
    val getImageSize: (imagePath: String, onResult: (width: Int, height: Int) -> Unit) -> Unit,
)

/**
 * Camera callbacks passed from Swift layer
 * Used for camera operations using AVFoundation
 */
data class CameraCallbacks(
    val setupCameraPreview: (width: Double, height: Double) -> platform.UIKit.UIView,
    val startPreview: () -> Unit,
    val stopPreview: () -> Unit,
    val capturePhoto: (onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    val setFlashEnabled: (enabled: Boolean) -> Unit,
    val isFlashEnabled: () -> Boolean,
    val checkCameraPermission: (completion: (Boolean) -> Unit) -> Unit,
)

/**
 * Permission manager callbacks passed from Swift layer
 * Used for camera and photo library permissions
 */
data class PermissionManagerCallbacks(
    val isCameraPermissionGranted: () -> Boolean,
    val requestCameraPermission: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
    val wasCameraPermissionDenied: () -> Boolean,
    val isPhotoLibraryPermissionGranted: () -> Boolean,
    val requestPhotoLibraryPermission: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
    val wasPhotoLibraryPermissionDenied: () -> Boolean,
    val openAppSettings: () -> Unit,
)

/**
 * Gallery picker callbacks passed from Swift layer
 * Used for photo selection using PHPickerViewController
 */
data class GalleryPickerCallbacks(
    val pickImage: (onImageSelected: (String) -> Unit, onCancelled: () -> Unit) -> Unit,
    val pickMultipleImages: (maxImages: Int, onImagesSelected: (List<String>) -> Unit, onCancelled: () -> Unit) -> Unit,
    val isAvailable: () -> Boolean,
)

/**
 * EXIF reader callbacks passed from Swift layer
 * Used for reading EXIF orientation metadata from images
 */
data class ExifReaderCallbacks(
    val getOrientation: (filePath: String) -> Int,
)

/**
 * Calendar callbacks passed from Swift layer
 * Used for accessing device calendars (Google Calendar, Apple Calendar, etc.)
 */
data class CalendarCallbacks(
    val requestPermission: (completion: (Boolean) -> Unit) -> Unit,
    val hasPermission: (completion: (Boolean) -> Unit) -> Unit,
    val getDeviceCalendars: (
        type: String,
        completion: (List<Any>?) -> Unit,
    ) -> Unit,
    val getEvents: (
        calendarIds: List<String>,
        startDate: String,
        endDate: String,
        filterStudyRelated: Boolean,
        completion: (List<Any>?) -> Unit,
    ) -> Unit,
)

/**
 * Main entry point for iOS Compose UI
 * Creates and configures the iOS app with all necessary dependencies
 *
 * @param googleSignInHandler Function to handle Google Sign-In, passed from Swift layer
 *                            Returns Firebase ID token for backend authentication
 * @param bootstrapSyncCallbacks Callbacks for bootstrap sync operations (passed from Swift)
 * @param documentStorageCallbacks Callbacks for document storage operations (passed from Swift)
 * @param documentQueryCallbacks Callbacks for querying documents (passed from Swift)
 * @param networkDetectorCallbacks Callbacks for network detection (passed from Swift)
 * @param edgeDetectorCallbacks Callbacks for edge detection using Vision framework (passed from Swift)
 * @param cameraCallbacks Callbacks for camera operations using AVFoundation (passed from Swift)
 */
fun MainViewController(
    googleSignInHandler: ((onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    appleSignInHandler: ((onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    bootstrapSyncCallbacks: BootstrapSyncCallbacks? = null,
    documentStorageCallbacks: DocumentStorageCallbacks? = null,
    documentQueryCallbacks: DocumentQueryCallbacks? = null,
    networkDetectorCallbacks: NetworkDetectorCallbacks? = null,
    imageQualityProcessorCallbacks: ImageQualityProcessorCallbacks? = null,
    edgeDetectorCallbacks: EdgeDetectorCallbacks? = null,
    cameraCallbacks: CameraCallbacks? = null,
    permissionManagerCallbacks: PermissionManagerCallbacks? = null,
    galleryPickerCallbacks: GalleryPickerCallbacks? = null,
    exifReaderCallbacks: ExifReaderCallbacks? = null,
    calendarCallbacks: CalendarCallbacks? = null,
    notificationCallbacks: NotificationCallbacks? = null,
    signOutHandler: (() -> Unit)? = null,
): UIViewController {
    // Initialize PreferencesManager for iOS
    val preferencesManager = PreferencesManager()
    UserSessionManager.instance.initialize(preferencesManager)
    com.kashyap.wizlyai.data.local.LocalJson.store = com.kashyap.wizlyai.data.local.IOSFileJsonStore()

    // Initialize platform-specific utilities
    val permissionManager = com.kashyap.wizlyai.util.PermissionManager()
    val galleryPicker = com.kashyap.wizlyai.util.GalleryPicker()
    val filePicker = com.kashyap.wizlyai.util.FilePicker()
    val cameraController = com.kashyap.wizlyai.camera.CameraController()
    val imageCropper = com.kashyap.wizlyai.util.ImageCropper()
    val imageEnhancer = com.kashyap.wizlyai.util.ImageEnhancer()
    val calendarProvider = com.kashyap.wizlyai.calendar.CalendarProvider()

    // ✅ Initialize iOS callbacks for Swift bridge
    if (bootstrapSyncCallbacks != null) {
        IOSCallbackBridge.setBootstrapSyncCallbacks(bootstrapSyncCallbacks)
        println("✅ iOS: Bootstrap sync callbacks registered")
    }

    if (documentStorageCallbacks != null) {
        IOSCallbackBridge.setDocumentStorageCallbacks(documentStorageCallbacks)
        println("✅ iOS: Document storage callbacks registered")
    }

    if (documentQueryCallbacks != null) {
        IOSCallbackBridge.setDocumentQueryCallbacks(documentQueryCallbacks)
        println("✅ iOS: Document query callbacks registered")
    }

    if (networkDetectorCallbacks != null) {
        IOSCallbackBridge.setNetworkDetectorCallbacks(networkDetectorCallbacks)
        println("✅ iOS: Network detector callbacks registered")
    }

    if (imageQualityProcessorCallbacks != null) {
        IOSCallbackBridge.setImageQualityProcessorCallbacks(imageQualityProcessorCallbacks)
        println("✅ iOS: Image quality processor callbacks registered")
    }

    if (edgeDetectorCallbacks != null) {
        IOSCallbackBridge.setEdgeDetectorCallbacks(edgeDetectorCallbacks)
        println("✅ iOS: Edge detector callbacks registered")
    }

    if (cameraCallbacks != null) {
        IOSCallbackBridge.setCameraCallbacks(cameraCallbacks)
        println("✅ iOS: Camera callbacks registered")
    }

    if (permissionManagerCallbacks != null) {
        IOSCallbackBridge.setPermissionManagerCallbacks(permissionManagerCallbacks)
        println("✅ iOS: Permission manager callbacks registered")
    }

    if (galleryPickerCallbacks != null) {
        IOSCallbackBridge.setGalleryPickerCallbacks(galleryPickerCallbacks)
        println("✅ iOS: Gallery picker callbacks registered")
    }

    if (exifReaderCallbacks != null) {
        IOSCallbackBridge.setExifReaderCallbacks(exifReaderCallbacks)
        println("✅ iOS: EXIF reader callbacks registered")
    }

    if (calendarCallbacks != null) {
        IOSCallbackBridge.setCalendarCallbacks(calendarCallbacks)
        println("✅ iOS: Calendar callbacks registered")
    }

    if (notificationCallbacks != null) {
        IOSCallbackBridge.setNotificationCallbacks(notificationCallbacks)
        println("✅ iOS: Notification callbacks registered")
    }

    // ✅ Initialize iOS LocalDocumentStorage with CoreData (via callbacks)
    val localStorage: com.kashyap.wizlyai.data.repository.LocalDocumentStorage =
        createIOSLocalDocumentStorage()

    // ✅ Initialize iOS DocumentProvider with CoreData
    val documentProvider: com.kashyap.wizlyai.ui.screens.documents.DocumentProvider =
        createIOSDocumentProvider()

    // ✅ Initialize iOS AppSettingsRepository with NSUserDefaults
    val appSettingsRepository = createAppSettingsRepository()

    println("✅ iOS: Platform utilities initialized")
    println("   - PermissionManager")
    println("   - GalleryPicker")
    println("   - CameraController")
    println("   - ImageCropper")
    println("   - ImageEnhancer")
    println("   - LocalDocumentStorage (via Swift bridge)")
    println("   - AppSettingsRepository (NSUserDefaults)")
    println("✅ iOS: Bootstrap sync handled by BootstrapSyncCoordinator (via Swift bridge)")

    return ComposeUIViewController {
        WizlyApp(
            permissionManager = permissionManager,
            galleryPicker = galleryPicker,
            filePicker = filePicker,
            cameraController = cameraController,
            imageCropper = imageCropper,
            imageEnhancer = imageEnhancer,
            localStorage = localStorage, // ✅ Pass iOS CoreData implementation
            documentProvider = documentProvider, // ✅ Pass iOS CoreData implementation
            calendarProvider = calendarProvider, // ✅ Pass iOS calendar provider
            preferencesManager = preferencesManager, // ✅ Required for PhotoReviewScreen to show
            appSettingsRepository = appSettingsRepository, // ✅ Pass iOS NSUserDefaults implementation
            onProviderSignOut = { signOutHandler?.invoke() },
            // Note: Bootstrap sync is handled by AuthViewModel → BootstrapSyncCoordinator
            onGoogleSignIn = { authViewModel: com.kashyap.wizlyai.ui.viewmodel.AuthViewModel,
                              referralCode: String?,
                              onSuccess: (String) -> Unit ->
                println("🔵 iOS: Google Sign-In requested")

                if (googleSignInHandler != null) {
                    // Call the handler passed from Swift layer to get Firebase token
                    googleSignInHandler(
                        { firebaseToken: String ->
                            println("✅ iOS: Google Sign-In successful, got Firebase token")
                            println("   Token length: ${firebaseToken.length} characters")

                            // Authenticate with backend using Firebase token
                            println("🔐 iOS: Authenticating with backend...")
                            authViewModel.authenticateWithProvider(
                                firebaseToken = firebaseToken,
                                provider = "google",
                                referralCode = referralCode,
                            )

                            // Observe provider auth state
                            CoroutineScope(Dispatchers.Main).launch {
                                authViewModel.providerAuthState.collect { state: com.kashyap.wizlyai.ui.state.UiState<com.kashyap.wizlyai.network.models.ProviderAuthResponse> ->
                                    when (state) {
                                        is com.kashyap.wizlyai.ui.state.UiState.Success -> {
                                            println("✅ iOS: Backend authentication successful")
                                            val user: com.kashyap.wizlyai.network.models.AuthUser? = state.data?.user
                                            if (user != null) {
                                                println("   User ID: ${user.id}")
                                                println("   Email: ${user.email}")
                                                onSuccess(user.id)
                                            }
                                            return@collect // Stop collecting
                                        }
                                        is com.kashyap.wizlyai.ui.state.UiState.Error -> {
                                            println("❌ iOS: Backend authentication failed: ${state.message}")
                                            return@collect // Stop collecting
                                        }
                                        is com.kashyap.wizlyai.ui.state.UiState.Loading -> {
                                            println("⏳ iOS: Backend authentication in progress...")
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        { error: String ->
                            println("❌ iOS: Google Sign-In failed: $error")
                        },
                    )
                } else {
                    println("⚠️ iOS: Google Sign-In handler not provided")
                    println("   Note: Pass googleSignInHandler to MainViewController()")
                }
            },
            onAppleSignIn = { authViewModel: com.kashyap.wizlyai.ui.viewmodel.AuthViewModel,
                             referralCode: String?,
                             onSuccess: (String) -> Unit ->
                println("🍎 iOS: Apple Sign-In requested")

                if (appleSignInHandler != null) {
                    // Call the handler passed from Swift layer to get Firebase token
                    appleSignInHandler(
                        { firebaseToken: String ->
                            println("✅ iOS: Apple Sign-In successful, got Firebase token")
                            println("   Token length: ${firebaseToken.length} characters")

                            // Authenticate with backend using Firebase token
                            println("🔐 iOS: Authenticating with backend...")
                            authViewModel.authenticateWithProvider(
                                firebaseToken = firebaseToken,
                                provider = "apple",
                                referralCode = referralCode,
                            )

                            // Observe provider auth state
                            CoroutineScope(Dispatchers.Main).launch {
                                authViewModel.providerAuthState.collect { state: com.kashyap.wizlyai.ui.state.UiState<com.kashyap.wizlyai.network.models.ProviderAuthResponse> ->
                                    when (state) {
                                        is com.kashyap.wizlyai.ui.state.UiState.Success -> {
                                            println("✅ iOS: Backend authentication successful")
                                            val user: com.kashyap.wizlyai.network.models.AuthUser? = state.data?.user
                                            if (user != null) {
                                                println("   User ID: ${user.id}")
                                                println("   Email: ${user.email}")
                                                onSuccess(user.id)
                                            }
                                            return@collect // Stop collecting
                                        }
                                        is com.kashyap.wizlyai.ui.state.UiState.Error -> {
                                            println("❌ iOS: Backend authentication failed: ${state.message}")
                                            return@collect // Stop collecting
                                        }
                                        is com.kashyap.wizlyai.ui.state.UiState.Loading -> {
                                            println("⏳ iOS: Backend authentication in progress...")
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        { error: String ->
                            println("❌ iOS: Apple Sign-In failed: $error")
                        },
                    )
                } else {
                    println("⚠️ iOS: Apple Sign-In handler not provided")
                    println("   Note: Pass appleSignInHandler to MainViewController()")
                }
            },
        )
    }
}
