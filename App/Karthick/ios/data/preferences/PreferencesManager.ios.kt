package com.kashyap.wizlyai.data.preferences

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of PreferencesManager using NSUserDefaults
 *
 * TEMPORARY: Simplified implementation without Keychain
 * - Firebase CocoaPods removed due to Xcode 16.4 incompatibility
 * - All data stored in NSUserDefaults (less secure but functional)
 *
 * TODO: Re-enable Keychain storage for sensitive data when Firebase is restored
 *       OR implement native iOS Keychain wrapper without CocoaPods
 *
 * SECURITY NOTE: Auth tokens stored in NSUserDefaults (not ideal for production)
 * Consider using platform.Security.* for Keychain access without CocoaPods
 */
actual class PreferencesManager {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        userDefaults.setBool(value, key)
        userDefaults.synchronize()
    }

    actual fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        return if (userDefaults.objectForKey(key) != null) {
            userDefaults.boolForKey(key)
        } else {
            defaultValue
        }
    }

    actual fun putString(
        key: String,
        value: String,
    ) {
        userDefaults.setObject(value, key)
        userDefaults.synchronize()
    }

    actual fun getString(
        key: String,
        defaultValue: String,
    ): String {
        return userDefaults.stringForKey(key) ?: defaultValue
    }

    actual fun putLong(
        key: String,
        value: Long,
    ) {
        userDefaults.setObject(value, key)
        userDefaults.synchronize()
    }

    actual fun getLong(
        key: String,
        defaultValue: Long,
    ): Long {
        return if (userDefaults.objectForKey(key) != null) {
            (userDefaults.objectForKey(key) as? Long) ?: userDefaults.integerForKey(key).toLong()
        } else {
            defaultValue
        }
    }

    actual fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
    }

    actual fun clear() {
        // Get all keys in the app's domain
        val appDomain = platform.Foundation.NSBundle.mainBundle.bundleIdentifier
        appDomain?.let {
            userDefaults.removePersistentDomainForName(it)
            userDefaults.synchronize()
        }
    }
}
