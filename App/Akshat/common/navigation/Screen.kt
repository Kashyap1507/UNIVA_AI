package com.kashyap.wizlyai.navigation

/**
 * Sealed class representing all navigation screens in Wizly-App
 */
sealed class Screen(val route: String) {
    // ========== Authentication Flow ==========

    /**
     * Splash screen with 2-second auto-progression
     */
    object Splash : Screen("splash")

    /**
     * Onboarding flow with 3 slides explaining app features
     */
    object Onboarding : Screen("onboarding")

    /**
     * Auth landing screen with "I have an account" and "Get Started" buttons
     */
    object AuthLanding : Screen("auth_landing")

    /**
     * University student onboarding (university, degree, program, semester).
     * Shown after sign-up/login until the profile is complete.
     */
    object StudentOnboarding : Screen("student_onboarding")

    /**
     * Signup screen with social auth and email registration
     */
    data class Signup(
        val referralCode: String? = null,
    ) : Screen("signup")

    /**
     * Login screen with email and social authentication
     */
    object Login : Screen("login")

    /**
     * OTP Verification screen for email code entry
     */
    data class OTPVerification(
        val email: String,
        val action: String, // "signup" or "login"
        val referralCode: String? = null,
    ) : Screen("otp_verification")

    /**
     * Email verification screen
     */
    object EmailVerification : Screen("email_verification")

    // ========== Main App Screens ==========

    /**
     * Today tab: streak, plan tasks, due cards and deadlines
     */
    object Today : Screen("today")

    /**
     * Camera scanning for "Check my work"
     */
    object Scanning : Screen("scanning")

    /**
     * Multi-page review of the scanned pages before "Check my work"
     */
    object ScanReview : Screen("scan_review")

    /**
     * Library of past scans
     */
    object Documents : Screen("documents")

    /**
     * Profile/Settings screen
     * @param scrollToNotifications If true, automatically scrolls to notifications section
     */
    data class Profile(
        val scrollToNotifications: Boolean = false,
    ) : Screen("profile")

    // ========== Additional Feature Screens ==========

    /**
     * Deadlines: overdue, this week, later; imports and study plans
     */
    object Deadlines : Screen("deadlines")

    /**
     * App Settings screen with cloud sync and preferences
     */
    object AppSettings : Screen("app_settings")

    /**
     * Developer Settings screen with debug options
     */
    object DeveloperSettings : Screen("developer_settings")

    /**
     * Notification Settings screen for deadline reminders
     */
    object NotificationSettings : Screen("notification_settings")

    /**
     * Referrals screen (Invite Friends) for managing referral codes
     */
    object Referrals : Screen("referrals")

    /**
     * Premium Plans screen showing subscription tiers and features
     */
    object PremiumPlans : Screen("premium_plans")

    /**
     * Current Plan screen showing subscription details and usage stats
     */
    object CurrentPlan : Screen("current_plan")

    /**
     * One scanned document
     */
    data class DocumentDetail(val documentId: String) : Screen("document_detail/$documentId")

    /**
     * Edit the student profile
     */
    object EditProfile : Screen("edit_profile")

    // ========== Courses and study tools ==========

    /**
     * University courses list
     */
    object Courses : Screen("courses")

    /**
     * One course: outline and materials
     */
    data class CourseDetail(val courseId: String) : Screen("course/$courseId")

    /**
     * Course-grounded tutor chat
     */
    data class TutorChat(val courseId: String, val courseName: String) : Screen("tutor/$courseId")

    /**
     * "Check my work" progress and feedback for the current scan
     */
    object WorkFeedback : Screen("work_feedback")

    /**
     * A past "Check my work" result
     */
    data class WorkFeedbackDetail(val submissionId: String) : Screen("work_feedback/$submissionId")

    /**
     * Taking a practice set, quiz or mock exam (a new attempt)
     */
    data class PracticeExam(val setId: String, val title: String) : Screen("practice/$setId")

    /**
     * A submitted attempt's result and review
     */
    data class PracticeResult(val attemptId: String, val title: String) : Screen("practice/result/$attemptId")

    /**
     * An exam countdown study plan
     */
    data class StudyPlan(val planId: String) : Screen("study_plan/$planId")

    /**
     * Tutor tab: pick a course to chat with its tutor
     */
    object TutorHome : Screen("tutor_home")

    /**
     * Grade tracker across courses (GPA estimate)
     */
    object Grades : Screen("grades")

    /**
     * Study groups: mine, create, join, find
     */
    object Groups : Screen("groups")

    /**
     * One study group
     */
    data class GroupDetail(val groupId: String) : Screen("groups/$groupId")

    /**
     * Research tab (Master's): projects and tools
     */
    object ResearchHome : Screen("research")

    data class ResearchProject(val projectId: String) : Screen("research/projects/$projectId")

    data class Paper(val paperId: String) : Screen("research/papers/$paperId")

    data class PaperCompare(val paperIds: List<String>) : Screen("research/compare")

    object WritingFeedbackTool : Screen("research/writing")

    /**
     * Flashcard review session: one course, or every course when courseId is null
     */
    data class FlashcardReview(val courseId: String?, val title: String) : Screen("flashcards/review/${courseId ?: "all"}")
}

/** Bottom tabs: Today, Courses, Scan (+ sheet), Tutor, Profile (Research replaces Profile for Master's). */
enum class BottomNavTab(val route: String) {
    TODAY("today"),
    COURSES("courses"),
    SCAN("scanning"),
    TUTOR("tutor_home"),
    PROFILE("profile"),
    RESEARCH("research"),
}
