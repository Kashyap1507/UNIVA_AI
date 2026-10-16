package com.kashyap.wizlyai.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kashyap.wizlyai.data.models.*
import com.kashyap.wizlyai.data.preferences.PreferencesManager
import com.kashyap.wizlyai.data.preferences.hasShownFirstLaunchNotificationDialog
import com.kashyap.wizlyai.data.preferences.setFirstLaunchNotificationDialogShown
import com.kashyap.wizlyai.data.repository.AppSettingsRepository
import com.kashyap.wizlyai.navigation.BottomNavTab
import com.kashyap.wizlyai.navigation.NavigationController
import com.kashyap.wizlyai.navigation.Screen
import com.kashyap.wizlyai.ui.components.WizlyBottomNavigation
import com.kashyap.wizlyai.ui.screens.auth.*
import com.kashyap.wizlyai.ui.screens.onboarding.StudentOnboardingScreen
import com.kashyap.wizlyai.ui.screens.documents.DocumentsScreen
import com.kashyap.wizlyai.ui.screens.scanning.ScanningFlowScreen
import com.kashyap.wizlyai.ui.screens.settings.AppSettingsScreenWrapper
import com.kashyap.wizlyai.ui.screens.settings.DeveloperSettingsScreen
import com.kashyap.wizlyai.ui.screens.settings.NotificationSettingsScreen
import com.kashyap.wizlyai.ui.screens.settings.SettingsProfileScreen
import com.kashyap.wizlyai.ui.screens.subscription.CurrentPlanScreen
import com.kashyap.wizlyai.ui.screens.subscription.PremiumPlansScreen
import com.kashyap.wizlyai.ui.state.UiState
import com.kashyap.wizlyai.util.rememberHapticFeedback
import com.kashyap.wizlyai.ui.theme.PlatformDimensions
import com.kashyap.wizlyai.ui.theme.WizlyTheme
import com.kashyap.wizlyai.ui.theme.rememberThemeController
import com.kashyap.wizlyai.ui.viewmodel.*
import kotlinx.coroutines.launch

/**
 * CompositionLocal for providing bottom navigation height to all screens
 * This includes both the content height and system insets (gesture bar, home indicator)
 * Screens should use this value for bottom content padding to avoid cutoff
 */
val LocalBottomNavHeight = compositionLocalOf { 0.dp }

/**
 * Main Wizly App Composable
 * Handles navigation between all screens
 */
@Composable
fun WizlyApp(
    navigationController: NavigationController = remember { NavigationController() },
    onGoogleSignIn: (authViewModel: AuthViewModel, referralCode: String?, onSuccess: (String) -> Unit) -> Unit = { _, _, _ -> },
    onAppleSignIn: (authViewModel: AuthViewModel, referralCode: String?, onSuccess: (String) -> Unit) -> Unit = { _, _, _ -> },
    onBootstrapSync: (suspend (bootstrapData: com.kashyap.wizlyai.network.models.BootstrapData, userId: String) -> Unit)? = null,
    permissionManager: com.kashyap.wizlyai.util.PermissionManager? = null,
    galleryPicker: com.kashyap.wizlyai.util.GalleryPicker? = null,
    filePicker: com.kashyap.wizlyai.util.FilePicker? = null,
    cameraController: com.kashyap.wizlyai.camera.CameraController? = null,
    imageCropper: com.kashyap.wizlyai.util.ImageCropper? = null,
    imageEnhancer: com.kashyap.wizlyai.util.ImageEnhancer? = null,
    localStorage: com.kashyap.wizlyai.data.repository.LocalDocumentStorage? = null,
    documentProvider: com.kashyap.wizlyai.ui.screens.documents.DocumentProvider? = null,
    calendarProvider: com.kashyap.wizlyai.calendar.CalendarProvider? = null,
    preferencesManager: PreferencesManager? = null,
    appSettingsRepository: AppSettingsRepository? = null,
    // Signs out of Google/Apple on the platform side so the next sign-in shows the account picker
    onProviderSignOut: () -> Unit = {},
) {
    val currentScreen by navigationController.currentScreen.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Create ViewModels (in production, use DI like Koin)
    val authViewModel = remember { AuthViewModel(viewModelScope = coroutineScope) }
    val scanningViewModel =
        remember {
            ScanningViewModel(
                localStorage = localStorage ?: com.kashyap.wizlyai.data.repository.NoOpLocalDocumentStorage(),
                viewModelScope = coroutineScope,
            )
        }
    val coursesViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.CoursesViewModel(viewModelScope = coroutineScope) }
    val flashcardRepository = remember { com.kashyap.wizlyai.data.repository.FlashcardRepository() }
    var flashcardsDue by remember { mutableStateOf(0) }
    val courseTabs = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val deadlinesViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.DeadlinesViewModel(viewModelScope = coroutineScope) }
    val todayViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.TodayViewModel(viewModelScope = coroutineScope) }
    val coursesProgressViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.CoursesProgressViewModel(viewModelScope = coroutineScope) }
    val researchHomeViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.ResearchHomeViewModel(viewModelScope = coroutineScope) }
    val workFeedbackViewModel =
        remember { com.kashyap.wizlyai.ui.viewmodel.WorkFeedbackViewModel(viewModelScope = coroutineScope, localStorage = localStorage) }
    val themeController = rememberThemeController(preferencesManager)

    WizlyTheme(themeController = themeController) {
        // Calculate total bottom navigation height including system insets
        val density = LocalDensity.current
        val bottomNavBaseHeight = PlatformDimensions.bottomNavHeight
        val navigationBarsInsets = WindowInsets.navigationBars.getBottom(density)
        val navigationBarsInsetsDp = with(density) { navigationBarsInsets.toDp() }
        val totalBottomNavHeight = bottomNavBaseHeight + navigationBarsInsetsDp

        // Provide total height to all screens via CompositionLocal
        // This allows screens to add proper padding to avoid content being cut off
        CompositionLocalProvider(LocalBottomNavHeight provides totalBottomNavHeight) {
            // Check if we should show bottom navigation
            // Scanning screen should be full-screen (no bottom nav)
            // Master's students get a Research tab in place of Profile (Profile opens from Today)
            val studentProfile by com.kashyap.wizlyai.session.UserSessionManager.instance.studentProfile.collectAsState()
            val isMaster = studentProfile.degree == com.kashyap.wizlyai.network.models.DegreeLevel.MASTER
            val showBottomNav =
                when (currentScreen) {
                    is Screen.Today,
                    is Screen.Courses,
                    is Screen.TutorHome,
                    -> true
                    is Screen.ResearchHome -> isMaster
                    is Screen.Profile -> !isMaster
                    else -> false
                }

            // Open a study-plan task (Today and the study plan screen): cards, practice, tutor or the course
            val coursesForTasks by coursesViewModel.courses.collectAsState()
            val openStudyTask: (String, String) -> Unit = { type, courseId ->
                val course = (coursesForTasks as? UiState.Success)?.data?.firstOrNull { it.id == courseId }
                val label = course?.code?.takeIf { it.isNotBlank() } ?: course?.name ?: "Course"
                when (type) {
                    "REVIEW_CARDS" -> navigationController.navigateTo(Screen.FlashcardReview(courseId, label))
                    "PRACTICE", "MOCK_EXAM" -> {
                        courseTabs[courseId] = 3
                        navigationController.navigateTo(Screen.CourseDetail(courseId))
                    }
                    "LEARN" -> navigationController.navigateTo(Screen.TutorChat(courseId, course?.name ?: label))
                    else -> {
                        courseTabs[courseId] = 0
                        navigationController.navigateTo(Screen.CourseDetail(courseId))
                    }
                }
            }

            // State for bottom nav and sheet (hoisted to this level for proper layering)
            val currentTab = navigationController.getCurrentBottomTab() ?: BottomNavTab.TODAY
            var showCreateSheet by remember { mutableStateOf(false) }

            // Swipeable tabs for HorizontalPager (excluding SCAN which opens bottom sheet)
            val swipeableTabs = remember(isMaster) {
                listOf(BottomNavTab.TODAY, BottomNavTab.COURSES, BottomNavTab.TUTOR, if (isMaster) BottomNavTab.RESEARCH else BottomNavTab.PROFILE)
            }

            val profileContent: @Composable () -> Unit = {
                SettingsProfileScreen(
                    onBack = { navigationController.navigateToBottomTab(BottomNavTab.TODAY) },
                    onEditProfile = { navigationController.navigateTo(Screen.EditProfile) },
                    onNavigateToAppSettings = { navigationController.navigateTo(Screen.AppSettings) },
                    onNavigateToDeveloperSettings = { navigationController.navigateTo(Screen.DeveloperSettings) },
                    onNavigateToDocuments = { navigationController.navigateTo(Screen.Documents) },
                    onNavigateToNotificationSettings = { navigationController.navigateTo(Screen.NotificationSettings) },
                    onNavigateToReferrals = { navigationController.navigateTo(Screen.Referrals) },
                    onNavigateToCurrentPlan = { navigationController.navigateTo(Screen.CurrentPlan) },
                    onNavigateToPremiumPlans = { navigationController.navigateTo(Screen.PremiumPlans) },
                    onSignOut = {
                        authViewModel.logout()
                        onProviderSignOut()
                        navigationController.navigateTo(Screen.Login)
                    },
                    themeMode = themeController.themeMode,
                    onThemeModeSelected = { mode -> themeController.updateThemeMode(mode) },
                    preferencesManager = preferencesManager,
                    scrollToNotifications = false, // Default to false, deep link handling can be added later
                    showBackButton = isMaster,
                )
            }

            // Haptic feedback for tab changes
            val haptic = rememberHapticFeedback()

            // Circular paging: Use virtual infinite pages with modulo mapping
            val actualPageCount = swipeableTabs.size
            val initialActualIndex = swipeableTabs.indexOf(currentTab).coerceAtLeast(0)
            // Start at a large number divisible by pageCount + initial offset for circular swiping
            val initialPage = remember(initialActualIndex) {
                (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % actualPageCount) + initialActualIndex
            }

            // Helper to get actual tab index from virtual page
            fun getActualTabIndex(page: Int) = page.mod(actualPageCount)

            // PagerState for tab navigation with circular swipe behavior
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { Int.MAX_VALUE }
            )

            // Bidirectional sync: When pager settles on a new page, update NavigationController
            // Only while the tab pager is on screen: a pushed screen reports no tab (currentTab falls back to TODAY)
            LaunchedEffect(pagerState.settledPage) {
                if (!showBottomNav) return@LaunchedEffect
                val newTab = swipeableTabs.getOrNull(getActualTabIndex(pagerState.settledPage))
                if (newTab != null && newTab != currentTab) {
                    haptic.performLight()
                    navigationController.navigateToBottomTab(newTab)
                }
            }

            // Bidirectional sync: When bottom nav is tapped, animate pager to that page
            LaunchedEffect(currentTab, showBottomNav) {
                if (showBottomNav && currentTab in swipeableTabs) {
                    val targetActualIndex = swipeableTabs.indexOf(currentTab)
                    val currentActualIndex = getActualTabIndex(pagerState.currentPage)
                    if (targetActualIndex >= 0 && targetActualIndex != currentActualIndex) {
                        // Calculate nearest virtual page for target (shortest path)
                        val currentPage = pagerState.currentPage
                        val delta = targetActualIndex - currentActualIndex
                        // Choose shortest path (e.g., for 4 tabs: -3 becomes +1, +3 becomes -1)
                        val adjustedDelta = if (kotlin.math.abs(delta) > actualPageCount / 2) {
                            if (delta > 0) delta - actualPageCount else delta + actualPageCount
                        } else delta
                        pagerState.animateScrollToPage(currentPage + adjustedDelta)
                    } else if (pagerState.currentPageOffsetFraction != 0f && !pagerState.isScrollInProgress) {
                        // Left between pages (e.g. a scroll cut off by a pushed screen): snap back
                        pagerState.scrollToPage(pagerState.currentPage)
                    }
                }
            }

            // FAB scale animation based on pager scroll
            val fabScale by animateFloatAsState(
                targetValue = if (pagerState.isScrollInProgress) 0.8f else 1f,
                animationSpec = tween(durationMillis = 150),
                label = "fabScale",
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // HorizontalPager for tab screens provides ViewPager-like swipe with edge previews
                // Non-tab screens are rendered separately below
                if (showBottomNav) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1, // Preload 1 adjacent screen for smooth preview
                        key = { swipeableTabs[getActualTabIndex(it)].route },
                    ) { virtualPageIndex ->
                        // Render tab content based on actual page index (circular mapping)
                        val actualPageIndex = getActualTabIndex(virtualPageIndex)
                        when (swipeableTabs[actualPageIndex]) {
                            BottomNavTab.TODAY -> {
                                // TODAY TAB (university migration Phase 8; replaces the K-12 Home dashboard)
                                var showNotificationPermissionDialog by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    coursesViewModel.load()
                                    if (preferencesManager != null && !preferencesManager.hasShownFirstLaunchNotificationDialog()) {
                                        showNotificationPermissionDialog = true
                                    }
                                }
                                val coursesState by coursesViewModel.courses.collectAsState()
                                val studentName =
                                    com.kashyap.wizlyai.session.UserSessionManager.instance.userName
                                        ?.substringBefore(" ")
                                        ?.takeIf { it.isNotBlank() } ?: "there"
                                val knownCourses = (coursesState as? UiState.Success)?.data.orEmpty()
                                com.kashyap.wizlyai.ui.screens.today.TodayScreen(
                                    viewModel = todayViewModel,
                                    studentName = studentName,
                                    hasCourses = coursesState !is UiState.Success || knownCourses.isNotEmpty(),
                                    onOpenTask = { task -> openStudyTask(task.type, task.courseId) },
                                    onStudyCards = { navigationController.navigateTo(Screen.FlashcardReview(null, "Flashcards")) },
                                    onOpenDeadlines = { navigationController.navigateTo(Screen.Deadlines) },
                                    onPracticeConcept = { courseId -> openStudyTask("PRACTICE", courseId) },
                                    onAskTutor = { courseId ->
                                        if (courseId.isBlank()) navigationController.navigateToBottomTab(BottomNavTab.TUTOR) else openStudyTask("LEARN", courseId)
                                    },
                                    onCheckWork = { navigationController.navigateTo(Screen.Scanning) },
                                    onOpenCourses = { navigationController.navigateToBottomTab(BottomNavTab.COURSES) },
                                    onOpenProfile = { navigationController.navigateToBottomTab(BottomNavTab.PROFILE) },
                                )

                                if (showNotificationPermissionDialog && preferencesManager != null) {
                                    com.kashyap.wizlyai.ui.components.NotificationPermissionDialog(
                                        onAllow = {
                                            showNotificationPermissionDialog = false
                                            preferencesManager.setFirstLaunchNotificationDialogShown(true)
                                            com.kashyap.wizlyai.notifications.CalendarNotificationManagerBridge.requestPermission()
                                        },
                                        onNotNow = {
                                            showNotificationPermissionDialog = false
                                            preferencesManager.setFirstLaunchNotificationDialogShown(true)
                                        },
                                    )
                                }
                            }

                            BottomNavTab.COURSES -> {
                                // COURSES TAB: courses with progress; grades and deadlines from the header
                                val progress by coursesProgressViewModel.progress.collectAsState()
                                LaunchedEffect(Unit) { coursesProgressViewModel.load() }
                                com.kashyap.wizlyai.ui.screens.courses.CoursesScreen(
                                    viewModel = coursesViewModel,
                                    onBack = null,
                                    onOpenCourse = { courseId -> navigationController.navigateTo(Screen.CourseDetail(courseId)) },
                                    progress = progress,
                                    onOpenGrades = { navigationController.navigateTo(Screen.Grades) },
                                    onOpenDeadlines = { navigationController.navigateTo(Screen.Deadlines) },
                                    onOpenGroups = { navigationController.navigateTo(Screen.Groups) },
                                )
                            }

                            BottomNavTab.TUTOR -> {
                                val coursesState by coursesViewModel.courses.collectAsState()
                                LaunchedEffect(Unit) { coursesViewModel.load() }
                                com.kashyap.wizlyai.ui.screens.today.TutorHomeScreen(
                                    courses = coursesState,
                                    onOpenTutor = { courseId, courseName -> navigationController.navigateTo(Screen.TutorChat(courseId, courseName)) },
                                    onOpenCourses = { navigationController.navigateToBottomTab(BottomNavTab.COURSES) },
                                )
                            }

                            BottomNavTab.PROFILE -> profileContent()

                            BottomNavTab.RESEARCH -> {
                                com.kashyap.wizlyai.ui.screens.research.ResearchHomeScreen(
                                    viewModel = researchHomeViewModel,
                                    onOpenProject = { navigationController.navigateTo(Screen.ResearchProject(it.id)) },
                                    onOpenWriting = { navigationController.navigateTo(Screen.WritingFeedbackTool) },
                                )
                            }

                            else -> {}
                        }
                    }
                } else {
                    // Non-tab screens use Crossfade for transitions
                    Crossfade(
                        targetState = currentScreen,
                        modifier = Modifier.fillMaxSize(),
                        label = "screenTransition",
                    ) { screen ->
                        when (screen) {
                        is Screen.Splash -> {
                            SplashScreen(
                                onNavigateNext = {
                                    // Check if onboarding was seen
                                    val hasSeenOnboarding = com.kashyap.wizlyai.session.UserSessionManager.instance.hasSeenOnboarding()
                                    if (hasSeenOnboarding) {
                                        navigationController.navigateTo(Screen.AuthLanding)
                                    } else {
                                        navigationController.navigateTo(Screen.Onboarding)
                                    }
                                },
                                onNavigateToHome = {
                                    // Auto-login - finish onboarding first if the profile is incomplete
                                    val needsOnboarding = com.kashyap.wizlyai.session.UserSessionManager.instance.storedNeedsOnboarding()
                                    navigationController.navigateAndClearHistory(
                                        if (needsOnboarding) Screen.StudentOnboarding else Screen.Today,
                                    )
                                },
                            )
                        }

                        is Screen.Onboarding -> {
                            OnboardingScreen(
                                onComplete = {
                                    // Mark onboarding as seen
                                    com.kashyap.wizlyai.session.UserSessionManager.instance.setHasSeenOnboarding(true)
                                    navigationController.navigateTo(Screen.AuthLanding)
                                },
                                onSkip = {
                                    // Mark onboarding as seen
                                    com.kashyap.wizlyai.session.UserSessionManager.instance.setHasSeenOnboarding(true)
                                    navigationController.navigateTo(Screen.AuthLanding)
                                },
                            )
                        }

                        is Screen.AuthLanding -> {
                            AuthLandingScreen(
                                onHaveAccount = {
                                    navigationController.navigateTo(Screen.Login)
                                },
                                onGetStarted = {
                                    navigationController.navigateTo(Screen.Signup())
                                },
                            )
                        }

                        is Screen.EditProfile -> {
                            StudentOnboardingScreen(
                                isEditing = true,
                                onComplete = { navigationController.navigateToBottomTab(BottomNavTab.PROFILE) },
                                onCancel = { navigationController.navigateToBottomTab(BottomNavTab.PROFILE) },
                            )
                        }

                        is Screen.StudentOnboarding -> {
                            StudentOnboardingScreen(
                                onComplete = { navigationController.handleOnboardingCompleted() },
                                onSignOut = {
                                    authViewModel.logout()
                                    onProviderSignOut()
                                    navigationController.navigateAndClearHistory(Screen.Login)
                                },
                            )
                        }

                        is Screen.Signup -> {
                            SignupScreen(
                                viewModel = authViewModel,
                                referralCode = screen.referralCode,
                                onNavigateToOTP = { email, action, refCode ->
                                    navigationController.navigateTo(
                                        Screen.OTPVerification(
                                            email = email,
                                            action = action,
                                            referralCode = refCode,
                                        ),
                                    )
                                },
                                onNavigateToLogin = {
                                    navigationController.navigateTo(Screen.Login)
                                },
                                onGoogleSignIn = {
                                    onGoogleSignIn(authViewModel, screen.referralCode) { userId ->
                                        navigationController.handleSuccessfulAuth(userId, authViewModel.needsOnboarding.value)
                                    }
                                },
                                onAppleSignIn = {
                                    onAppleSignIn(authViewModel, screen.referralCode) { userId ->
                                        navigationController.handleSuccessfulAuth(userId, authViewModel.needsOnboarding.value)
                                    }
                                },
                            )
                        }

                        is Screen.Login -> {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToOTP = { email, action ->
                                    navigationController.navigateTo(
                                        Screen.OTPVerification(
                                            email = email,
                                            action = action,
                                        ),
                                    )
                                },
                                onNavigateToSignup = {
                                    navigationController.navigateTo(Screen.Signup())
                                },
                                onGoogleSignIn = {
                                    onGoogleSignIn(authViewModel, null) { userId ->
                                        navigationController.handleSuccessfulAuth(userId, authViewModel.needsOnboarding.value)
                                    }
                                },
                                onAppleSignIn = {
                                    onAppleSignIn(authViewModel, null) { userId ->
                                        navigationController.handleSuccessfulAuth(userId, authViewModel.needsOnboarding.value)
                                    }
                                },
                                onNavigateToHome = { userId, needsOnboarding ->
                                    navigationController.handleSuccessfulAuth(userId, needsOnboarding)
                                },
                            )
                        }

                        is Screen.OTPVerification -> {
                            val verifyState by authViewModel.verifyState.collectAsState()

                            // Handle bootstrap sync after verification success
                            LaunchedEffect(verifyState) {
                                println("═══════════════════════════════════════════════════════")
                                println("🔍 WIZLY_APP: LaunchedEffect(verifyState) triggered")
                                println("   Verify state: ${verifyState::class.simpleName}")

                                if (verifyState is UiState.Success) {
                                    println("   ✅ Verify state is Success")
                                    val data = (verifyState as UiState.Success).data
                                    val userId = authViewModel.currentUser.value?.id

                                    println("   👤 User ID: ${userId ?: "NULL"}")
                                    println("   📦 Bootstrap data: ${if (data.bootstrapData != null) "PRESENT" else "NULL"}")

                                    if (userId != null && data.bootstrapData != null) {
                                        println("   🚀 CALLING onBootstrapSync callback...")
                                        println("═══════════════════════════════════════════════════════")
                                        onBootstrapSync?.invoke(data.bootstrapData, userId)
                                    } else {
                                        if (userId == null) {
                                            println("   ❌ CANNOT call onBootstrapSync: userId is NULL")
                                        }
                                        if (data.bootstrapData == null) {
                                            println("   ❌ CANNOT call onBootstrapSync: bootstrapData is NULL")
                                        }
                                        println("═══════════════════════════════════════════════════════")
                                    }
                                } else {
                                    println("   ⏳ Verify state is NOT Success yet: ${verifyState::class.simpleName}")
                                    println("═══════════════════════════════════════════════════════")
                                }
                            }

                            OTPVerificationScreen(
                                viewModel = authViewModel,
                                email = screen.email,
                                action = screen.action,
                                referralCode = screen.referralCode,
                                onVerificationSuccess = {
                                    val userId =
                                        com.kashyap.wizlyai.session.UserSessionManager.instance.userId
                                            ?: authViewModel.currentUser.value?.id
                                            ?: return@OTPVerificationScreen

                                    navigationController.handleSuccessfulAuth(userId, authViewModel.needsOnboarding.value)
                                },
                                onBack = {
                                    navigationController.navigateBack()
                                },
                            )
                        }

                        is Screen.Scanning -> {
                            if (permissionManager != null && galleryPicker != null && cameraController != null) {
                                ScanningFlowScreen(
                                    onBack = {
                                        // Adding more pages goes back to the review; a first scan closes the flow
                                        if (scanningViewModel.reviewImages.value.isNotEmpty()) {
                                            navigationController.navigateTo(Screen.ScanReview)
                                        } else {
                                            navigationController.navigateToBottomTab(BottomNavTab.TODAY)
                                        }
                                    },
                                    onNavigateToScanReview = { navigationController.navigateTo(Screen.ScanReview) },
                                    permissionManager = permissionManager,
                                    galleryPicker = galleryPicker,
                                    cameraController = cameraController,
                                    imageEnhancer = imageEnhancer,
                                    localStorage = localStorage,
                                    preferencesManager = preferencesManager,
                                    externalViewModel = scanningViewModel,
                                )
                            } else {
                                PlaceholderScreen("Camera and gallery not available. Please restart the app.")
                            }
                        }

                        is Screen.Documents -> {
                            DocumentsScreen(
                                onDocumentClick = { docId -> navigationController.navigateTo(Screen.DocumentDetail(docId)) },
                                onBack = { navigationController.navigateBack() },
                                documentProvider = documentProvider,
                                userId = com.kashyap.wizlyai.session.UserSessionManager.instance.userId.orEmpty(),
                            )
                        }

                        is Screen.DocumentDetail -> {
                            // Load document with pages and show DocumentViewerScreen
                            var document by remember { mutableStateOf<Document?>(null) }
                            var documentPages by remember { mutableStateOf<List<String>>(emptyList()) }
                            var documentTitle by remember { mutableStateOf("Document") }
                            var loadError by remember { mutableStateOf<String?>(null) }
                            var isLoading by remember { mutableStateOf(true) }

                            // Reload when the screen changes, to pick up saved data
                            LaunchedEffect(screen.documentId, currentScreen) {
                                if (documentProvider != null) {
                                    try {
                                        val doc = documentProvider.getDocumentWithPages(screen.documentId)
                                        document = doc
                                        documentTitle = doc.title
                                        documentPages = doc.pages?.map { page ->
                                            // DocumentPage.imageUrl contains the local file path when loaded from Room
                                            // or the remote URL when loaded from API
                                            page.imageUrl
                                        } ?: emptyList()
                                        isLoading = false
                                    } catch (e: Exception) {
                                        println("❌ Failed to load document: ${e.message}")
                                        loadError = e.message
                                        isLoading = false
                                    }
                                } else {
                                    loadError = "No document provider available"
                                    isLoading = false
                                }
                            }

                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            } else if (loadError != null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    androidx.compose.material3.Text(
                                        text = loadError ?: "Error loading document",
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else if (documentPages.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    com.kashyap.wizlyai.ui.screens.document.DocumentViewerScreen(
                                        documentPages = documentPages,
                                        documentTitle = documentTitle,
                                        onBack = {
                                            navigationController.navigateBack()
                                        },
                                        onSave = { /* Already saved */ },
                                        onNavigateToDocumentsGallery = {
                                            // Close button navigates to Documents tab/screen
                                            navigationController.navigateTo(Screen.Documents)
                                        },
                                        onDeleteDocument = {
                                            coroutineScope.launch {
                                                val currentDoc = document
                                                if (currentDoc != null && localStorage != null && localStorage !is com.kashyap.wizlyai.data.repository.NoOpLocalDocumentStorage) {
                                                    try {
                                                        println("🗑️ Deleting document: ${screen.documentId}")
                                                        // Only this device's copy; the server keeps the scan for Check my work history
                                                        val result = localStorage.deleteDocumentLocally(screen.documentId, currentDoc.userId)
                                                        result.onSuccess {
                                                            println("✅ Document deleted successfully: ${screen.documentId}")
                                                            navigationController.navigateTo(Screen.Documents)
                                                        }.onFailure { error ->
                                                            println("❌ Failed to delete document: ${error.message}")
                                                            navigationController.navigateTo(Screen.Documents)
                                                        }
                                                    } catch (e: Exception) {
                                                        println("❌ Error deleting document: ${e.message}")
                                                        navigationController.navigateTo(Screen.Documents)
                                                    }
                                                } else {
                                                    println("⚠️ Cannot delete: document or localStorage not available")
                                                    navigationController.navigateTo(Screen.Documents)
                                                }
                                            }
                                        },
                                    )

                                }
                            } else {
                                // No pages found - show error message with back button
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = "No pages found in document",
                                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                        androidx.compose.material3.Button(
                                            onClick = { navigationController.navigateBack() },
                                        ) {
                                            androidx.compose.material3.Text("Go Back")
                                        }
                                    }
                                }
                            }
                        }

                        // Note: Screen.Profile is now handled by HorizontalPager above (when showBottomNav is true)

                        is Screen.AppSettings -> {
                            AppSettingsScreenWrapper(
                                onNavigateBack = {
                                    navigationController.navigateBack()
                                },
                            )
                        }

                        is Screen.DeveloperSettings -> {
                            if (preferencesManager != null) {
                                DeveloperSettingsScreen(
                                    onBack = {
                                        navigationController.navigateBack()
                                    },
                                    preferencesManager = preferencesManager,
                                    onRestartApp = {
                                        com.kashyap.wizlyai.util.AppRestarter.restartApp()
                                    },
                                )
                            } else {
                                PlaceholderScreen("PreferencesManager not available")
                            }
                        }

                        is Screen.NotificationSettings -> {
                            if (preferencesManager != null) {
                                NotificationSettingsScreen(
                                    onBack = {
                                        navigationController.navigateBack()
                                    },
                                    preferencesManager = preferencesManager,
                                    onNavigateToProfile = {
                                        navigationController.navigateTo(Screen.Profile(scrollToNotifications = true))
                                    },
                                )
                            } else {
                                PlaceholderScreen("PreferencesManager not available")
                            }
                        }

                        is Screen.Referrals -> {
                            com.kashyap.wizlyai.ui.screens.referral.ReferralScreen(
                                onBack = {
                                    navigationController.navigateBack()
                                }
                            )
                        }

                        is Screen.PremiumPlans -> {
                            val subscriptionViewModel = remember { SubscriptionViewModel() }
                            PremiumPlansScreen(
                                viewModel = subscriptionViewModel,
                                onNavigateBack = {
                                    navigationController.navigateBack()
                                }
                            )
                        }

                        is Screen.CurrentPlan -> {
                            val subscriptionViewModel = remember { SubscriptionViewModel() }
                            CurrentPlanScreen(
                                viewModel = subscriptionViewModel,
                                onNavigateBack = {
                                    navigationController.navigateBack()
                                },
                                onUpgrade = {
                                    navigationController.navigateTo(Screen.PremiumPlans)
                                }
                            )
                        }

                        is Screen.ScanReview -> {
                            // Scan review: reorder or drop pages, then "Check my work" (pick a course and practice or graded)
                            val reviewImages by scanningViewModel.reviewImages.collectAsState()
                            var showCheckWorkBottomSheet by remember { mutableStateOf(false) }
                            val coursesForCheck by coursesViewModel.courses.collectAsState()

                            if (showCheckWorkBottomSheet) {
                                LaunchedEffect(Unit) { coursesViewModel.load() }
                                com.kashyap.wizlyai.ui.screens.work.CheckWorkBottomSheet(
                                    courses = (coursesForCheck as? UiState.Success)?.data.orEmpty(),
                                    onDismiss = { showCheckWorkBottomSheet = false },
                                    onAddCourse = {
                                        showCheckWorkBottomSheet = false
                                        navigationController.navigateToBottomTab(BottomNavTab.COURSES)
                                    },
                                    onConfirm = { courseId, isPractice ->
                                        showCheckWorkBottomSheet = false
                                        workFeedbackViewModel.start(
                                            courseId = courseId,
                                            isPractice = isPractice,
                                            imagePaths = scanningViewModel.getReviewImagePaths(),
                                            title = scanningViewModel.generateAutoTitle(),
                                        )
                                        navigationController.navigateTo(Screen.WorkFeedback)
                                    },
                                )
                            }

                            com.kashyap.wizlyai.ui.screens.scanning.ScanReviewScreen(
                                images = reviewImages,
                                actionButtonText = "Check my work",
                                onAddMore = { navigationController.navigateTo(Screen.Scanning) },
                                onDeleteImage = { index -> scanningViewModel.removeReviewImage(index) },
                                onReorderImages = { fromIndex, toIndex -> scanningViewModel.reorderReviewImages(fromIndex, toIndex) },
                                onActionClick = { if (reviewImages.isNotEmpty()) showCheckWorkBottomSheet = true },
                                onDiscard = {
                                    scanningViewModel.clearAllReviewImages()
                                    navigationController.navigateBack()
                                },
                            )
                        }


                        is Screen.Deadlines -> {
                            com.kashyap.wizlyai.ui.screens.deadlines.DeadlinesScreen(
                                viewModel = deadlinesViewModel,
                                filePicker = filePicker,
                                calendarProvider = calendarProvider,
                                onOpenPlan = { plan -> navigationController.navigateTo(Screen.StudyPlan(plan.id)) },
                                onBack = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.Profile -> {
                            // Master's: Profile is a pushed screen (Research has the tab), so back returns to Today
                            com.kashyap.wizlyai.ui.util.PlatformBackHandler(enabled = true) { navigationController.navigateToBottomTab(BottomNavTab.TODAY) }
                            profileContent()
                        }

                        is Screen.ResearchProject -> {
                            val projectViewModel = remember(screen.projectId) { com.kashyap.wizlyai.ui.viewmodel.ProjectViewModel(screen.projectId, coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.research.ProjectScreen(
                                viewModel = projectViewModel,
                                filePicker = filePicker,
                                onBack = { navigationController.navigateBack() },
                                onOpenPaper = { navigationController.navigateTo(Screen.Paper(it.id)) },
                                onCompare = { navigationController.navigateTo(Screen.PaperCompare(it)) },
                            )
                        }

                        is Screen.Paper -> {
                            val paperViewModel = remember(screen.paperId) { com.kashyap.wizlyai.ui.viewmodel.PaperViewModel(screen.paperId, coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.research.PaperScreen(viewModel = paperViewModel, onBack = { navigationController.navigateBack() })
                        }

                        is Screen.PaperCompare -> {
                            val compareViewModel = remember(screen.paperIds) { com.kashyap.wizlyai.ui.viewmodel.CompareViewModel(coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.research.CompareScreen(viewModel = compareViewModel, paperIds = screen.paperIds, onBack = { navigationController.navigateBack() })
                        }

                        is Screen.WritingFeedbackTool -> {
                            val writingViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.WritingFeedbackViewModel(coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.research.WritingFeedbackScreen(viewModel = writingViewModel, onBack = { navigationController.navigateBack() })
                        }

                        is Screen.Groups -> {
                            val groupsViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.GroupsViewModel(viewModelScope = coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.groups.GroupsScreen(
                                viewModel = groupsViewModel,
                                onOpenGroup = { navigationController.navigateTo(Screen.GroupDetail(it.id)) },
                                onBack = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.GroupDetail -> {
                            val groupViewModel = remember(screen.groupId) { com.kashyap.wizlyai.ui.viewmodel.GroupDetailViewModel(screen.groupId, coroutineScope) }
                            com.kashyap.wizlyai.ui.screens.groups.GroupDetailScreen(
                                viewModel = groupViewModel,
                                onBack = { navigationController.navigateBack() },
                                onLeft = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.Grades -> {
                            val gradesViewModel = remember { com.kashyap.wizlyai.ui.viewmodel.GradesViewModel(viewModelScope = coroutineScope) }
                            val coursesState by coursesViewModel.courses.collectAsState()
                            com.kashyap.wizlyai.ui.screens.today.GradesScreen(
                                viewModel = gradesViewModel,
                                courses = (coursesState as? UiState.Success)?.data.orEmpty().filterNot { it.isArchived },
                                onBack = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.CourseDetail -> {
                            val detailViewModel =
                                remember(screen.courseId) {
                                    com.kashyap.wizlyai.ui.viewmodel.CourseDetailViewModel(
                                        courseId = screen.courseId,
                                        viewModelScope = coroutineScope,
                                    )
                                }
                            com.kashyap.wizlyai.ui.screens.courses.CourseDetailScreen(
                                viewModel = detailViewModel,
                                filePicker = filePicker,
                                onBack = { navigationController.navigateBack() },
                                onDeleted = {
                                    coursesViewModel.load()
                                    navigationController.navigateBack()
                                },
                                onOpenTutor = { courseId, courseName ->
                                    navigationController.navigateTo(Screen.TutorChat(courseId, courseName))
                                },
                                onOpenFeedback = { submissionId ->
                                    navigationController.navigateTo(Screen.WorkFeedbackDetail(submissionId))
                                },
                                flashcardsViewModel =
                                    remember(screen.courseId) {
                                        com.kashyap.wizlyai.ui.viewmodel.CourseFlashcardsViewModel(
                                            courseId = screen.courseId,
                                            viewModelScope = coroutineScope,
                                            repository = flashcardRepository,
                                        )
                                    },
                                onStudyCards = { courseId, title ->
                                    navigationController.navigateTo(Screen.FlashcardReview(courseId, title))
                                },
                                initialTab = courseTabs[screen.courseId] ?: 0,
                                onTabChange = { courseTabs[screen.courseId] = it },
                                practiceViewModel =
                                    remember(screen.courseId) {
                                        com.kashyap.wizlyai.ui.viewmodel.CoursePracticeViewModel(
                                            courseId = screen.courseId,
                                            viewModelScope = coroutineScope,
                                        )
                                    },
                                onOpenPracticeSet = { setId, title -> navigationController.navigateTo(Screen.PracticeExam(setId, title)) },
                                onOpenPracticeAttempt = { attemptId, title -> navigationController.navigateTo(Screen.PracticeResult(attemptId, title)) },
                                syllabusViewModel =
                                    remember(screen.courseId) {
                                        com.kashyap.wizlyai.ui.viewmodel.SyllabusImportViewModel(courseId = screen.courseId, viewModelScope = coroutineScope)
                                    },
                                progressViewModel =
                                    remember(screen.courseId) {
                                        com.kashyap.wizlyai.ui.viewmodel.CourseProgressViewModel(courseId = screen.courseId, viewModelScope = coroutineScope)
                                    },
                                onOpenGrades = { navigationController.navigateTo(Screen.Grades) },
                            )
                        }

                        is Screen.StudyPlan -> {
                            val planViewModel = remember(screen.planId) { com.kashyap.wizlyai.ui.viewmodel.StudyPlanViewModel(viewModelScope = coroutineScope) }
                            LaunchedEffect(screen.planId) { planViewModel.load(screen.planId) }
                            com.kashyap.wizlyai.ui.screens.deadlines.StudyPlanScreen(
                                viewModel = planViewModel,
                                onBack = { navigationController.navigateBack() },
                                onOpenTask = { type, courseId -> openStudyTask(type, courseId) },
                            )
                        }

                        is Screen.PracticeExam -> {
                            val examViewModel = remember(screen) { com.kashyap.wizlyai.ui.viewmodel.ExamViewModel(viewModelScope = coroutineScope) }
                            LaunchedEffect(screen) { examViewModel.start(screen.setId, screen.title) }
                            com.kashyap.wizlyai.ui.screens.practice.ExamScreen(
                                viewModel = examViewModel,
                                title = screen.title,
                                onClose = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.PracticeResult -> {
                            val examViewModel = remember(screen) { com.kashyap.wizlyai.ui.viewmodel.ExamViewModel(viewModelScope = coroutineScope) }
                            LaunchedEffect(screen) { examViewModel.openResult(screen.attemptId) }
                            com.kashyap.wizlyai.ui.screens.practice.ExamScreen(
                                viewModel = examViewModel,
                                title = screen.title,
                                onClose = { navigationController.navigateBack() },
                            )
                        }

                        is Screen.FlashcardReview -> {
                            val reviewViewModel =
                                remember(screen) {
                                    com.kashyap.wizlyai.ui.viewmodel.FlashcardReviewViewModel(
                                        viewModelScope = coroutineScope,
                                        repository = flashcardRepository,
                                    )
                                }
                            LaunchedEffect(screen) { reviewViewModel.start(screen.courseId) }
                            com.kashyap.wizlyai.ui.screens.flashcards.FlashcardReviewScreen(
                                viewModel = reviewViewModel,
                                title = screen.title,
                                onClose = {
                                    coroutineScope.launch { flashcardRepository.summary().onSuccess { flashcardsDue = it.dueTotal } }
                                    navigationController.navigateBack()
                                },
                            )
                        }

                        is Screen.WorkFeedback -> {
                            com.kashyap.wizlyai.ui.screens.work.WorkFeedbackScreen(
                                viewModel = workFeedbackViewModel,
                                onClose = {
                                    workFeedbackViewModel.reset()
                                    scanningViewModel.clearAllReviewImages()
                                    navigationController.navigateAndClearHistory(Screen.Today)
                                },
                            )
                        }

                        is Screen.WorkFeedbackDetail -> {
                            LaunchedEffect(screen.submissionId) { workFeedbackViewModel.open(screen.submissionId) }
                            com.kashyap.wizlyai.ui.screens.work.WorkFeedbackScreen(
                                viewModel = workFeedbackViewModel,
                                onClose = {
                                    workFeedbackViewModel.reset()
                                    navigationController.navigateBack()
                                },
                            )
                        }

                        is Screen.TutorChat -> {
                            val tutorViewModel =
                                remember(screen.courseId) {
                                    com.kashyap.wizlyai.ui.viewmodel.TutorViewModel(
                                        courseId = screen.courseId,
                                        viewModelScope = coroutineScope,
                                    )
                                }
                            com.kashyap.wizlyai.ui.screens.tutor.TutorChatScreen(
                                viewModel = tutorViewModel,
                                courseName = screen.courseName,
                                onBack = { navigationController.navigateBack() },
                            )
                        }


                        else -> {
                            PlaceholderScreen("Screen not implemented: ${screen.route}")
                        }
                    }
                    }
                }

                // LAYER 1: Custom Scrim (darkens entire screen including nav bar)
                if (showBottomNav && showCreateSheet) {
                    androidx.compose.foundation.layout.Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)) // Light scrim (25% opacity)
                                .clickable { showCreateSheet = false }
                                .zIndex(1f),
                    )
                }

                // LAYER 2: Create Action Bottom Sheet (slides up from behind nav)
                if (showBottomNav) {
                    com.kashyap.wizlyai.ui.components.CreateActionBottomSheet(
                        visible = showCreateSheet,
                        onDismiss = { showCreateSheet = false },
                        onCheckWork = {
                            showCreateSheet = false
                            // Camera → photo review → Check my work
                            navigationController.navigateTo(Screen.Scanning)
                        },
                        onUploadMaterial = {
                            showCreateSheet = false
                            navigationController.navigateToBottomTab(BottomNavTab.COURSES)
                        },
                        onAddDeadline = {
                            showCreateSheet = false
                            navigationController.navigateTo(Screen.Deadlines)
                        },
                        bottomOffset = navigationBarsInsetsDp + (bottomNavBaseHeight / 2), // Start from middle of tab bar, accounting for system safe area
                        modifier =
                            Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .fillMaxWidth()
                                .zIndex(1.5f), // Between scrim and nav bar
                    )
                }

                // LAYER 3: Bottom Navigation (always on top)
                if (showBottomNav) {
                    WizlyBottomNavigation(
                        currentTab = currentTab,
                        currentPageIndex = getActualTabIndex(pagerState.currentPage),
                        pageOffsetFraction = pagerState.currentPageOffsetFraction,
                        isSheetOpen = showCreateSheet,
                        fabScale = fabScale, // Pass FAB scale for swipe animation
                        showResearch = isMaster,
                        onTabSelected = { tab ->
                            if (tab == BottomNavTab.SCAN) {
                                // Show create action bottom sheet instead of navigating
                                showCreateSheet = true
                            } else {
                                navigationController.navigateToBottomTab(tab)
                            }
                        },
                        onDismissSheet = {
                            // Close the bottom sheet when × is tapped
                            showCreateSheet = false
                        },
                        modifier =
                            Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .zIndex(2f), // Ensure nav stays on top
                    )
                }
            }
        } // End CompositionLocalProvider
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
    }
}
