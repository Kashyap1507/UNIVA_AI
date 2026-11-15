package com.kashyap.wizlyai.analytics

/**
 * Analytics event names and parameter constants
 *
 * Centralized definitions for all analytics events tracked in the app.
 * This ensures consistency and makes it easy to update event names.
 *
 * Event naming convention:
 * - Use snake_case (Firebase Analytics standard)
 * - Use past tense verbs for completed actions (e.g., "work_scan_uploaded")
 * - Keep names concise but descriptive
 */
object AnalyticsEvents {
    // ==================== Authentication Events ====================

    /** User successfully completed onboarding flow */
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    /** User started the signup process */
    const val SIGNUP_STARTED = "signup_started"

    /** User completed signup successfully */
    const val SIGNUP_COMPLETED = "signup_completed"

    /** User logged in successfully */
    const val LOGIN_COMPLETED = "login_completed"

    /** User logged out */
    const val LOGOUT = "logout"

    /** Authentication failed */
    const val AUTH_FAILED = "auth_failed"

    // ==================== Profile and Course Events ====================

    /** Student created a course */
    const val COURSE_CREATED = "course_created"

    /** Student edited their profile */
    const val STUDENT_PROFILE_EDITED = "student_profile_edited"

    /** Student deleted a course */
    const val COURSE_DELETED = "course_deleted"

    /** Student switched to a different course */
    const val COURSE_SWITCHED = "course_switched"

    // ==================== Work Scanning Events ====================

    /** User started scanning their work */
    const val SCAN_STARTED = "scan_started"

    /** User captured an image of their work */
    const val IMAGE_CAPTURED = "image_captured"

    /** User uploaded work for feedback */
    const val WORK_SCAN_UPLOADED = "work_scan_uploaded"

    /** Work feedback was received from server */
    const val WORK_FEEDBACK_RECEIVED = "work_feedback_received"

    /** User viewed work feedback details */
    const val WORK_FEEDBACK_VIEWED = "work_feedback_viewed"

    /** User shared work feedback */
    const val WORK_FEEDBACK_SHARED = "work_feedback_shared"

    /** Work scan upload failed */
    const val WORK_SCAN_UPLOAD_FAILED = "work_scan_upload_failed"

    // ==================== Practice Events ====================

    /** User clicked create practice set button */
    const val PRACTICE_SET_CREATE_CLICKED = "practice_set_create_clicked"

    /** User selected a topic for a practice set */
    const val PRACTICE_SET_TOPIC_SELECTED = "practice_set_topic_selected"

    /** Practice set was successfully generated */
    const val PRACTICE_SET_GENERATED = "practice_set_generated"

    /** User downloaded a practice set */
    const val PRACTICE_SET_DOWNLOADED = "practice_set_downloaded"

    /** User viewed practice set details */
    const val PRACTICE_SET_VIEWED = "practice_set_viewed"

    /** User shared a practice set */
    const val PRACTICE_SET_SHARED = "practice_set_shared"

    /** Practice set generation failed */
    const val PRACTICE_SET_GENERATION_FAILED = "practice_set_generation_failed"

    // ==================== Progress & Analytics Events ====================

    /** User viewed their progress dashboard */
    const val PROGRESS_VIEWED = "progress_viewed"

    /** User viewed analytics/insights */
    const val ANALYTICS_VIEWED = "analytics_viewed"

    /** User exported progress report */
    const val PROGRESS_EXPORTED = "progress_exported"

    // ==================== Settings Events ====================

    /** User viewed settings screen */
    const val SETTINGS_VIEWED = "settings_viewed"

    /** User changed app settings */
    const val SETTINGS_CHANGED = "settings_changed"

    /** User enabled cloud sync */
    const val CLOUD_SYNC_ENABLED = "cloud_sync_enabled"

    /** User disabled cloud sync */
    const val CLOUD_SYNC_DISABLED = "cloud_sync_disabled"

    // ==================== Calendar Events ====================

    /** User added event to calendar */
    const val CALENDAR_EVENT_ADDED = "calendar_event_added"

    /** User viewed calendar screen */
    const val CALENDAR_VIEWED = "calendar_viewed"

    // ==================== Sync Events ====================

    /** Bootstrap sync completed successfully */
    const val BOOTSTRAP_SYNC_SUCCESS = "bootstrap_sync_success"

    /** Bootstrap sync failed */
    const val BOOTSTRAP_SYNC_FAILED = "bootstrap_sync_failed"

    /** Smart sync triggered */
    const val SMART_SYNC_TRIGGERED = "smart_sync_triggered"

    /** Document sync completed */
    const val DOCUMENT_SYNC_COMPLETED = "document_sync_completed"

    // ==================== Performance Events ====================

    /** API call performance tracking */
    const val API_CALL = "api_call"

    /** App crash occurred */
    const val APP_CRASH = "app_crash"

    /** Network error occurred */
    const val NETWORK_ERROR = "network_error"

    // ==================== Subscription Events ====================

    /** User viewed premium plans screen */
    const val PREMIUM_PLANS_VIEWED = "premium_plans_viewed"

    /** User selected a subscription plan */
    const val SUBSCRIPTION_PLAN_SELECTED = "subscription_plan_selected"

    /** User successfully upgraded subscription */
    const val SUBSCRIPTION_UPGRADED = "subscription_upgraded"

    /** Subscription upgrade failed */
    const val SUBSCRIPTION_UPGRADE_FAILED = "subscription_upgrade_failed"

    /** User reached usage limit */
    const val USAGE_LIMIT_REACHED = "usage_limit_reached"

    /** Usage limit dialog shown */
    const val LIMIT_DIALOG_SHOWN = "limit_dialog_shown"

    /** Upgrade prompt shown (80% usage) */
    const val UPGRADE_PROMPT_SHOWN = "upgrade_prompt_shown"

    /** User clicked upgrade from limit dialog */
    const val UPGRADE_FROM_LIMIT_DIALOG = "upgrade_from_limit_dialog"

    /** User clicked upgrade from prompt */
    const val UPGRADE_FROM_PROMPT = "upgrade_from_prompt"

    /** User dismissed upgrade prompt */
    const val UPGRADE_PROMPT_DISMISSED = "upgrade_prompt_dismissed"

    /** User viewed usage stats */
    const val USAGE_STATS_VIEWED = "usage_stats_viewed"
}

/**
 * Screen names for screen view tracking
 */
object AnalyticsScreens {
    const val HOME = "home_screen"
    const val LOGIN = "login_screen"
    const val SIGNUP = "signup_screen"
    const val ONBOARDING = "onboarding_screen"
    const val WORK_SCAN_LIST = "work_scan_list_screen"
    const val WORK_FEEDBACK = "work_feedback_screen"
    const val PRACTICE_LIST = "practice_list_screen"
    const val PRACTICE_SET_DETAIL = "practice_set_detail_screen"
    const val CAMERA = "camera_screen"
    const val STUDENT_PROFILE = "student_profile_screen"
    const val SETTINGS = "settings_screen"
    const val PROGRESS = "progress_screen"
    const val CALENDAR = "calendar_screen"
    const val PREMIUM_PLANS = "premium_plans_screen"
}

/**
 * Parameter keys for analytics events
 */
object AnalyticsParams {
    // Common parameters
    const val USER_ID = "user_id"
    const val COURSE_ID = "course_id"
    const val TIMESTAMP = "timestamp"
    const val DURATION_MS = "duration_ms"
    const val SUCCESS = "success"
    const val ERROR_CODE = "error_code"
    const val ERROR_MESSAGE = "error_message"

    // Student parameters
    const val DEGREE_LEVEL = "degree_level"
    const val PROGRAM = "program"
    const val SEMESTER = "semester"

    // Content parameters
    const val MATERIAL_ID = "material_id"
    const val CONCEPT = "concept"

    // Document parameters
    const val DOCUMENT_ID = "document_id"
    const val DOCUMENT_TYPE = "document_type"
    const val PAGE_COUNT = "page_count"
    const val FILE_SIZE_KB = "file_size_kb"

    // Work feedback parameters
    const val SUBMISSION_ID = "submission_id"
    const val SCORE_PERCENTAGE = "score_percentage"
    const val QUESTIONS_TOTAL = "questions_total"
    const val QUESTIONS_CORRECT = "questions_correct"
    const val QUESTIONS_INCORRECT = "questions_incorrect"

    // Practice parameters
    const val PRACTICE_SET_ID = "practice_set_id"
    const val QUESTION_COUNT = "question_count"
    const val DIFFICULTY_LEVEL = "difficulty_level"

    // API parameters
    const val API_ENDPOINT = "api_endpoint"
    const val API_METHOD = "api_method"
    const val API_STATUS_CODE = "api_status_code"
    const val API_DURATION_MS = "api_duration_ms"

    // Sync parameters
    const val SYNC_TYPE = "sync_type"
    const val ITEMS_SYNCED = "items_synced"
    const val SYNC_SOURCE = "sync_source"

    // Settings parameters
    const val SETTING_NAME = "setting_name"
    const val SETTING_VALUE = "setting_value"

    // Screen parameters
    const val SCREEN_NAME = "screen_name"
    const val SCREEN_CLASS = "screen_class"
    const val PREVIOUS_SCREEN = "previous_screen"

    // User properties (for setUserProperty)
    const val COURSES_COUNT = "courses_count"
    const val SUBSCRIPTION_TYPE = "subscription_type"
    const val ACCOUNT_AGE_DAYS = "account_age_days"
    const val TOTAL_WORK_CHECKS = "total_work_checks"
    const val TOTAL_PRACTICE_SETS = "total_practice_sets"

    // Subscription parameters
    const val SUBSCRIPTION_TIER = "subscription_tier"
    const val PREVIOUS_TIER = "previous_tier"
    const val NEW_TIER = "new_tier"
    const val PLAN_PRICE = "plan_price"
    const val BILLING_PERIOD = "billing_period" // monthly, yearly
    const val FEATURE_NAME = "feature_name" // work_checks, practice_sets, flashcard_sets
    const val USAGE_COUNT = "usage_count"
    const val USAGE_LIMIT = "usage_limit"
    const val USAGE_PERCENTAGE = "usage_percentage"
    const val TIME_UNTIL_RESET_HOURS = "time_until_reset_hours"
}

/**
 * Timed event names for measuring durations
 */
object AnalyticsTimedEvents {
    const val WORK_SCAN_FLOW = "work_scan_flow_duration"
    const val PRACTICE_SET_GENERATION = "practice_set_generation_duration"
    const val WORK_FEEDBACK_PROCESSING = "work_feedback_processing_duration"
    const val IMAGE_UPLOAD = "image_upload_duration"
    const val BOOTSTRAP_SYNC = "bootstrap_sync_duration"
}
