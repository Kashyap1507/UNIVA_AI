package com.kashyap.wizlyai.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android implementation of PreferencesManager using EncryptedSharedPreferences
 *
 * SECURITY: Sensitive data (auth tokens, user credentials) are encrypted using AES256_GCM
 * Uses Android Keystore for secure key management
 */
actual class PreferencesManager {
    private val encryptedPrefs: SharedPreferences
    private val regularPrefs: SharedPreferences

    constructor(context: Context) {
        // Security: Use EncryptedSharedPreferences for sensitive data
        encryptedPrefs = createEncryptedPreferences(context)

        // Regular SharedPreferences for non-sensitive data (themes, UI settings)
        regularPrefs = context.getSharedPreferences(REGULAR_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            // Security: Create or retrieve master key from Android Keystore
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            // Security: Create EncryptedSharedPreferences with AES256_SIV for keys and AES256_GCM for values
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences on devices with encryption issues
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular", e)
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Determine if a key should be stored encrypted
     */
    private fun shouldEncrypt(key: String): Boolean {
        return when (key) {
            // Security: Encrypt sensitive authentication and user data
            PreferenceKeys.AUTH_TOKEN,
            PreferenceKeys.USER_ID,
            PreferenceKeys.USER_EMAIL,
            PreferenceKeys.AUTH_PROVIDER,
            PreferenceKeys.TOKEN_EXPIRES_AT,
            -> true

            // Non-sensitive UI/UX preferences can use regular storage
            else -> false
        }
    }

    private fun getPrefsForKey(key: String): SharedPreferences {
        return if (shouldEncrypt(key)) encryptedPrefs else regularPrefs
    }

    actual fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        getPrefsForKey(key).edit().putBoolean(key, value).apply()
    }

    actual fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        return getPrefsForKey(key).getBoolean(key, defaultValue)
    }

    actual fun putString(
        key: String,
        value: String,
    ) {
        getPrefsForKey(key).edit().putString(key, value).apply()
    }

    actual fun getString(
        key: String,
        defaultValue: String,
    ): String {
        return getPrefsForKey(key).getString(key, defaultValue) ?: defaultValue
    }

    actual fun putLong(
        key: String,
        value: Long,
    ) {
        getPrefsForKey(key).edit().putLong(key, value).apply()
    }

    actual fun getLong(
        key: String,
        defaultValue: Long,
    ): Long {
        return getPrefsForKey(key).getLong(key, defaultValue)
    }

    actual fun remove(key: String) {
        getPrefsForKey(key).edit().remove(key).apply()
    }

    actual fun clear() {
        // Security: Clear both encrypted and regular preferences
        encryptedPrefs.edit().clear().apply()
        regularPrefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "PreferencesManager"
        private const val ENCRYPTED_PREFS_NAME = "wizly_encrypted_preferences"
        private const val REGULAR_PREFS_NAME = "wizly_preferences"
        private const val FALLBACK_PREFS_NAME = "wizly_preferences_fallback"
    }
}
