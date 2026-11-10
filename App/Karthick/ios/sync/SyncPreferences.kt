package com.kashyap.wizlyai.sync

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of SyncPreferences
 * Uses NSUserDefaults for storage
 */
actual class SyncPreferences {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    /**
     * Get the timestamp of the last successful sync
     * Returns null if never synced
     */
    actual fun getLastSyncTimestamp(): String? {
        return userDefaults.stringForKey(KEY_LAST_SYNC_TIMESTAMP)
    }

    /**
     * Save the timestamp of a successful sync
     * @param timestamp ISO 8601 formatted timestamp
     */
    actual fun saveLastSyncTimestamp(timestamp: String) {
        userDefaults.setObject(timestamp, KEY_LAST_SYNC_TIMESTAMP)
        userDefaults.synchronize()
    }

    /**
     * Clear all sync preferences
     * Useful for logout or reset scenarios
     */
    actual fun clearSync() {
        userDefaults.removeObjectForKey(KEY_LAST_SYNC_TIMESTAMP)
        userDefaults.synchronize()
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
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }
}
