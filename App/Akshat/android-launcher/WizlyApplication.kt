package com.kashyap.wizlyai.android

import android.app.Application
import android.util.Log

/**
 * Application class for Wizly App
 * Initializes app-wide services and frameworks
 *
 * Note: Firebase Crashlytics is automatically initialized via Firebase SDK
 * and configured in shared/build.gradle.kts (androidMain dependencies)
 */
class WizlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Firebase Crashlytics is automatically initialized
        Log.d("WizlyApplication", "✅ Application initialized")
        Log.d("WizlyApplication", "✅ Firebase Crashlytics enabled for crash reporting")
    }
}
