package com.kashyap.wizlyai.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of SyncPreferences
 * Uses SharedPreferences for storage
 */
actual class SyncPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    /**
     * Get the timestamp of the last successful sync
     * Returns null if never synced
     */
    actual fun getLastSyncTimestamp(): String? {
        return prefs.getString(KEY_LAST_SYNC_TIMESTAMP, null)
    }

    /**
     * Save the timestamp of a successful sync
     * @param timestamp ISO 8601 formatted timestamp
     */
    actual fun saveLastSyncTimestamp(timestamp: String) {
        prefs.edit()
            .putString(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
    }

    /**
     * Clear all sync preferences
     * Useful for logout or reset scenarios
     */
    actual fun clearSync() {
        prefs.edit().clear().apply()
    }

    /**
     * Check if sync is needed based on time interval
     * @param intervalHours Number of hours before sync is considered stale
     * @return true if sync is needed, false otherwise
     */
    actual fun shouldSync(intervalHours: Int): Boolean {
        val lastSync = getLastSyncTimestamp() ?: return true

        return try {
            val lastSyncTime = kotlinx.datetime.Instant.parse(lastSync)
            val now = kotlinx.datetime.Clock.System.now()
            val hoursSinceSync = (now - lastSyncTime).inWholeHours

            hoursSinceSync >= intervalHours
        } catch (e: Exception) {
            // If parsing fails, assume sync is needed
            true
        }
    }

    companion object {
        private const val PREFS_NAME = "wizly_sync"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val DEFAULT_SYNC_INTERVAL_HOURS = 24
    }
}
