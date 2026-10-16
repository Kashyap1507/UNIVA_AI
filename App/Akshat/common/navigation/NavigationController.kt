package com.kashyap.wizlyai.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Navigation controller for Wizly-App
 * Handles screen navigation with authentication awareness
 */
class NavigationController {
    // Track the current screen - start on the splash experience
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen

    // Track navigation history for back navigation
    private val navigationHistory = mutableListOf<Screen>()

    // Track current user state
    private var _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId

    // Track the previous tab before entering scanning flow
    private var _previousTabBeforeScanning: BottomNavTab? = null

    /**
     * Set the current user
     */
    fun setCurrentUser(userId: String?) {
        _currentUserId.value = userId
    }

    /**
     * Navigate to initial screen based on authentication status
     * Called when the splash completes its bootstrapping sequence.
     */
    fun navigateToInitialScreen(isAuthenticated: Boolean = false) {
        if (isAuthenticated) {
            // User is authenticated - go directly to Today
            setCurrentUser("authenticated_user")
            navigateAndClearHistory(Screen.Today)
        } else {
            // User is not authenticated - check if they've seen onboarding
            setCurrentUser(null)
            // For now, go to splash (in production, check if onboarding was shown)
            navigateAndClearHistory(Screen.Splash)
        }
    }

    /**
     * Navigate to a new screen
     */
    fun navigateTo(screen: Screen) {
        // Add current screen to history before navigating
        _currentScreen.value.let {
            if (it != screen) {
                navigationHistory.add(it)
            }
        }
        _currentScreen.value = screen
    }

    /**
     * Navigate back to the previous screen if available
     * @return true if navigation was successful, false if there's no screen to go back to
     */
    fun navigateBack(): Boolean {
        if (navigationHistory.isEmpty()) {
            return false
        }

        val previousScreen = navigationHistory.removeAt(navigationHistory.lastIndex)
        _currentScreen.value = previousScreen

        return true
    }

    /**
     * Navigate to a new screen and clear history
     */
    fun navigateAndClearHistory(
        screen: Screen,
        popUpToRoute: String? = null,
        inclusive: Boolean = false,
    ) {
        // If popUpTo is provided, remove history items up to that screen
        if (popUpToRoute != null) {
            val targetIndex = navigationHistory.indexOfLast { it.route == popUpToRoute }
            if (targetIndex != -1) {
                val limit = if (inclusive) targetIndex else targetIndex + 1
                if (limit < navigationHistory.size) {
                    navigationHistory.subList(limit, navigationHistory.size).clear()
                }
            }
        } else {
            // Clear all history
            navigationHistory.clear()
        }

        _currentScreen.value = screen
    }

    /**
     * Handle successful authentication flow
     */
    fun handleSuccessfulAuth(
        userId: String,
        needsOnboarding: Boolean,
    ) {
        setCurrentUser(userId)

        // New students fill in their university profile first
        navigateAndClearHistory(if (needsOnboarding) Screen.StudentOnboarding else Screen.Today)
    }

    /**
     * Called when the student finishes onboarding
     */
    fun handleOnboardingCompleted() {
        navigateAndClearHistory(Screen.Today)
    }

    /**
     * Handle logout - clear user data and go to splash
     */
    fun handleLogout() {
        setCurrentUser(null)
        // After logout, go to splash screen
        navigateAndClearHistory(Screen.Splash)
    }

    /**
     * Navigate to bottom nav tab
     */
    fun navigateToBottomTab(tab: BottomNavTab) {
        val screen =
            when (tab) {
                BottomNavTab.TODAY -> Screen.Today
                BottomNavTab.COURSES -> Screen.Courses
                BottomNavTab.SCAN -> {
                    // Store the current tab before entering scanning
                    _previousTabBeforeScanning = getCurrentBottomTab()
                    Screen.Scanning
                }
                BottomNavTab.TUTOR -> Screen.TutorHome
                BottomNavTab.PROFILE -> Screen.Profile()
                BottomNavTab.RESEARCH -> Screen.ResearchHome
            }

        // Clear history when navigating to bottom tabs
        navigationHistory.clear()
        _currentScreen.value = screen
    }

    /**
     * Navigate back to previous tab (used after scanning completes)
     */
    fun navigateBackToPreviousTab() {
        val previousTab = _previousTabBeforeScanning ?: BottomNavTab.TODAY
        _previousTabBeforeScanning = null
        navigateToBottomTab(previousTab)
    }

    /**
     * Check if user needs onboarding
     */
    fun needsOnboarding(): Boolean {
        // User needs onboarding if they're not authenticated
        return _currentUserId.value == null
    }

    /**
     * Handle app resume - check auth status and navigate appropriately
     */
    fun handleAppResume(isAuthenticated: Boolean) {
        setCurrentUser(if (isAuthenticated) "authenticated_user" else null)

        // Only navigate if we're still showing the splash
        if (_currentScreen.value == Screen.Splash) {
            navigateToInitialScreen(isAuthenticated)
        }
        // Otherwise, keep user on their current screen
    }

    /**
     * Navigate to login with option to return to previous screen
     */
    fun navigateToLogin(returnScreen: Screen? = null) {
        if (returnScreen != null) {
            // Store where to return after login
            navigationHistory.add(returnScreen)
        }
        navigateTo(Screen.Login)
    }

    /**
     * Handle navigation after successful login
     */
    fun handlePostLoginNavigation() {
        // Check if there's a return screen
        if (navigationHistory.isNotEmpty()) {
            val returnScreen = navigationHistory.removeAt(navigationHistory.lastIndex)
            navigateTo(returnScreen)
        } else {
            // Default to Today after login
            navigateAndClearHistory(Screen.Today)
        }
    }

    /**
     * Get current bottom nav tab based on current screen
     */
    fun getCurrentBottomTab(): BottomNavTab? {
        return when (_currentScreen.value) {
            Screen.Today -> BottomNavTab.TODAY
            Screen.Courses -> BottomNavTab.COURSES
            Screen.Scanning -> BottomNavTab.SCAN
            Screen.TutorHome -> BottomNavTab.TUTOR
            Screen.ResearchHome -> BottomNavTab.RESEARCH
            is Screen.Profile -> BottomNavTab.PROFILE
            else -> null
        }
    }
}
