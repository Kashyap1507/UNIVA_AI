package com.kashyap.wizlyai.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kashyap.wizlyai.android.auth.AndroidGoogleAuthService
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.repository.LocalDocumentStorage
import com.kashyap.wizlyai.di.RepositoryFactory
import com.kashyap.wizlyai.session.UserSessionManager
import com.kashyap.wizlyai.ui.WizlyApp
import com.kashyap.wizlyai.ui.screens.documents.DocumentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// CompositionLocal for Google Auth Service
val LocalGoogleAuthService =
    compositionLocalOf<AndroidGoogleAuthService> {
        error("No GoogleAuthService provided")
    }

// CompositionLocal for Google Sign-In Launcher
val LocalGoogleSignInLauncher =
    compositionLocalOf<ActivityResultLauncher<Intent>> {
        error("No GoogleSignInLauncher provided")
    }

class MainActivity : ComponentActivity() {
    private lateinit var googleAuthService: AndroidGoogleAuthService
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var networkMonitor: com.kashyap.wizlyai.network.NetworkMonitor
    private lateinit var calendarProvider: com.kashyap.wizlyai.calendar.CalendarProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize PreferencesManager and UserSessionManager
        val preferencesManager = PreferencesManager(this)
        UserSessionManager.instance.initialize(preferencesManager)

        // Initialize NetworkConstants with PreferencesManager for server configuration
        com.kashyap.wizlyai.network.NetworkConstants.initialize(preferencesManager)
        android.util.Log.d("MainActivity", "🌐 NetworkConstants initialized with PreferencesManager")

        // Initialize AppRestarter with activity reference for app restart functionality
        com.kashyap.wizlyai.util.AppRestarter.setActivity(this)
        android.util.Log.d("MainActivity", "🔄 AppRestarter initialized")

        // Initialize Firebase Analytics
        com.kashyap.wizlyai.analytics.AnalyticsTracker.initialize(this)
        android.util.Log.d("MainActivity", "✅ Firebase Analytics initialized")

        // Initialize PdfShareManager for PDF sharing
        com.kashyap.wizlyai.util.PdfShareManager.initialize(this)
        android.util.Log.d("MainActivity", "✅ PdfShareManager initialized")

        // Set activity reference for notification permission requests
        com.kashyap.wizlyai.notifications.CalendarNotificationManagerBridge.setActivity(this)
        com.kashyap.wizlyai.notifications.NotificationPermissionBridge.setActivity(this)

        // Initialize BootstrapSyncCoordinator for data synchronization (Phase 2 refactoring)
        com.kashyap.wizlyai.sync.BootstrapSyncCoordinator.initialize(this)
        android.util.Log.d("MainActivity", "═══════════════════════════════════════════════════════")
        android.util.Log.d("MainActivity", "✅ BOOTSTRAP SYNC COORDINATOR CONFIGURED")
        android.util.Log.d("MainActivity", "═══════════════════════════════════════════════════════")
        android.util.Log.d("MainActivity", "🎯 Using new sync architecture (Phase 2)")
        android.util.Log.d("MainActivity", "📱 BootstrapSyncCoordinator → SyncCoordinator → SyncRepository → Room")
        android.util.Log.d("MainActivity", "═══════════════════════════════════════════════════════")

        // Initialize ClipboardManager and ShareManager for referral functionality
        com.kashyap.wizlyai.util.ClipboardManager.initialize(this)
        com.kashyap.wizlyai.util.ShareManager.initialize(this)
        android.util.Log.d("MainActivity", "✅ ClipboardManager and ShareManager initialized")

        // Initialize NetworkMonitor for automatic sync on connectivity changes
        networkMonitor = com.kashyap.wizlyai.network.NetworkMonitor(this)
        android.util.Log.d("MainActivity", "🌐 NetworkMonitor initialized - will auto-sync when online")

        // Initialize and schedule periodic document sync (every 15 minutes)
        com.kashyap.wizlyai.worker.DocumentSyncWorker.initialize(this)
        com.kashyap.wizlyai.worker.DocumentSyncWorker.schedule()
        android.util.Log.d("MainActivity", "📅 DocumentSyncWorker scheduled - syncs every 15 minutes")
        android.util.Log.d("MainActivity", "═══════════════════════════════════════════════════════")

        // Initialize Google Auth Service
        googleAuthService = AndroidGoogleAuthService(this)

        // Setup Activity Result Launcher for Google Sign-In
        googleSignInLauncher = AndroidGoogleAuthService.setupResultHandler(this, googleAuthService)

        // Initialize Permission Manager for camera access
        val permissionManager =
            com.kashyap.wizlyai.util.PermissionManager(
                context = this,
                activity = this,
            )

        // Initialize Gallery Picker for photo selection
        val galleryPicker =
            com.kashyap.wizlyai.util.GalleryPicker(
                context = this,
                activity = this,
            )

        // Document picker for course materials (PDF, PowerPoint, text, images)
        val filePicker =
            com.kashyap.wizlyai.util.FilePicker(
                context = this,
                activity = this,
            )

        // Offline cache for courses and materials (Room, DB v5)
        com.kashyap.wizlyai.data.local.AndroidCourseCache.initialize(applicationContext)

        // Initialize Camera Controller for camera functionality
        val cameraController =
            com.kashyap.wizlyai.camera.CameraController(
                context = this,
                activity = this,
            )

        // Initialize Image Cropper for document cropping
        val imageCropper =
            com.kashyap.wizlyai.util.ImageCropper(
                context = this,
            )

        // Initialize Image Enhancer for document processing animations
        val imageEnhancer = com.kashyap.wizlyai.util.ImageEnhancer(context = this)

        // Initialize local document storage and provider (offline-first)
        val localStorage: LocalDocumentStorage = RepositoryFactory.createLocalDocumentStorage(this)
        val documentProvider: DocumentProvider = RepositoryFactory.createDocumentProvider(this)

        // Initialize app settings repository for cloud sync settings
        val appSettingsRepository = RepositoryFactory.createAppSettingsRepository(this)

        // Initialize Calendar Provider for device calendar access
        calendarProvider =
            com.kashyap.wizlyai.calendar.CalendarProvider(
                context = this,
                activity = this,
            )

        // Initialize Calendar Notification Manager Bridge for scheduling notifications
        com.kashyap.wizlyai.notifications.CalendarNotificationManagerBridge.initialize(applicationContext)
        android.util.Log.d("MainActivity", "🔔 CalendarNotificationManagerBridge initialized")

        // REMOVED in Phase 3: Old BootstrapSyncManager and BootstrapSync
        // Sync is now handled automatically by AuthViewModel → BootstrapSyncCoordinator
        // Document sync is triggered via BootstrapSyncCoordinator.syncDocumentsAndWait()
        android.util.Log.d("MainActivity", "✅ Using new sync architecture (BootstrapSyncCoordinator)")

        setContent {
            CompositionLocalProvider(
                LocalGoogleAuthService provides googleAuthService,
                LocalGoogleSignInLauncher provides googleSignInLauncher,
            ) {
                WizlyApp(
                    permissionManager = permissionManager,
                    galleryPicker = galleryPicker,
                    filePicker = filePicker,
                    cameraController = cameraController,
                    imageCropper = imageCropper,
                    imageEnhancer = imageEnhancer,
                    localStorage = localStorage,
                    documentProvider = documentProvider,
                    calendarProvider = calendarProvider,
                    preferencesManager = preferencesManager,
                    appSettingsRepository = appSettingsRepository,
                    onBootstrapSync = { bootstrapData, userId ->
                        // REMOVED in Phase 3: Old bootstrapSyncManager.processBootstrapData()
                        // Sync is now handled automatically by AuthViewModel → BootstrapSyncCoordinator
                        android.util.Log.d("MainActivity", "📦 onBootstrapSync callback (no action needed)")
                        android.util.Log.d("MainActivity", "   Sync handled by BootstrapSyncCoordinator automatically")
                        android.util.Log.d("MainActivity", "   userId: $userId, sync: ${bootstrapData.syncTimestamp}")
                    },
                    onProviderSignOut = {
                        CoroutineScope(Dispatchers.Main).launch { googleAuthService.signOut() }
                    },
                    onGoogleSignIn = { authViewModel, referralCode, onSuccess ->
                        googleAuthService.startSignIn(googleSignInLauncher) { user ->
                            if (user != null) {
                                // Set loading state immediately for instant UI feedback
                                authViewModel.setProviderAuthLoading()

                                // Get Firebase token and exchange for JWT
                                CoroutineScope(Dispatchers.Main).launch {
                                    val firebaseToken = googleAuthService.getCurrentFirebaseToken()
                                    if (firebaseToken != null) {
                                        // Observe provider auth state for navigation
                                        // UPDATED in Phase 3: Removed old bootstrapSyncManager.processBootstrapData()
                                        // Sync is now handled automatically by AuthViewModel → BootstrapSyncCoordinator
                                        val providerAuthStateJob =
                                            launch {
                                                authViewModel.providerAuthState.collect { state ->
                                                    if (state is com.kashyap.wizlyai.ui.state.UiState.Success) {
                                                        val currentUser = authViewModel.currentUser.value
                                                        if (currentUser != null) {
                                                            android.util.Log.d("MainActivity", "✅ Google Sign-In successful")
                                                            android.util.Log.d(
                                                                "MainActivity",
                                                                "   Sync handled by BootstrapSyncCoordinator automatically",
                                                            )

                                                            // Register FCM token for push notifications
                                                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener {
                                                                    task ->
                                                                if (task.isSuccessful && task.result != null) {
                                                                    val token = task.result
                                                                    android.util.Log.d(
                                                                        "MainActivity",
                                                                        "📲 FCM Token retrieved: ${token.substring(0, 20)}...",
                                                                    )

                                                                    // Register token with backend
                                                                    CoroutineScope(Dispatchers.IO).launch {
                                                                        try {
                                                                            val prefs = getSharedPreferences("wizly_prefs", MODE_PRIVATE)
                                                                            val deviceId =
                                                                                prefs.getString("device_id", null) ?: "android_${java.util.UUID.randomUUID()}".also {
                                                                                    prefs.edit().putString("device_id", it).apply()
                                                                                }

                                                                            val result =
                                                                                com.kashyap.wizlyai.sync.FcmManager.instance.registerToken(
                                                                                    deviceId = deviceId,
                                                                                    token = token,
                                                                                    deviceName = android.os.Build.MODEL,
                                                                                    platform = "android",
                                                                                    appVersion =
                                                                                        try {
                                                                                            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                                                                                        } catch (e: Exception) {
                                                                                            "1.0.0"
                                                                                        },
                                                                                )

                                                                            result.fold(
                                                                                onSuccess = { response ->
                                                                                    android.util.Log.i(
                                                                                        "MainActivity",
                                                                                        "✅ FCM token registered: ${response.message}",
                                                                                    )
                                                                                },
                                                                                onFailure = { error ->
                                                                                    android.util.Log.e(
                                                                                        "MainActivity",
                                                                                        "❌ FCM token registration failed: ${error.message}",
                                                                                    )
                                                                                },
                                                                            )
                                                                        } catch (e: Exception) {
                                                                            android.util.Log.e(
                                                                                "MainActivity",
                                                                                "❌ Error registering FCM token: ${e.message}",
                                                                                e,
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    android.util.Log.w("MainActivity", "⚠️ Failed to retrieve FCM token")
                                                                }
                                                            }

                                                            // Navigate immediately - sync happens in background
                                                            onSuccess(currentUser.id)
                                                        }
                                                        // Cancel collection after successful navigation
                                                        this.cancel()
                                                    }
                                                }
                                            }

                                        // Call backend to exchange Firebase token for JWT
                                        authViewModel.authenticateWithProvider(
                                            firebaseToken = firebaseToken,
                                            provider = "google",
                                            referralCode = referralCode,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Handle calendar permission result
        if (::calendarProvider.isInitialized) {
            calendarProvider.handlePermissionResult(requestCode, grantResults)
        }

        // Handle notification permission result
        when (requestCode) {
            1001 -> { // NOTIFICATION_PERMISSION_REQUEST_CODE
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("MainActivity", "✅ Notification permission granted")
                } else {
                    android.util.Log.d("MainActivity", "❌ Notification permission denied")
                }
            }
            com.kashyap.wizlyai.notifications.NotificationPermissionBridge.REQUEST_CODE_NOTIFICATION_PERMISSION -> {
                // Handle NotificationPermissionBridge permission result
                com.kashyap.wizlyai.notifications.NotificationPermissionBridge.onPermissionResult(requestCode, grantResults)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup network monitor to prevent memory leaks
        if (::networkMonitor.isInitialized) {
            networkMonitor.cleanup()
            android.util.Log.d("MainActivity", "🧹 NetworkMonitor cleaned up")
        }

        // Clear activity reference to prevent memory leaks
        com.kashyap.wizlyai.notifications.CalendarNotificationManagerBridge.setActivity(null)
    }
}

@Preview
@Composable
fun DefaultPreview() {
    WizlyApp()
}
