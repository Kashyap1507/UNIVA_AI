package com.kashyap.wizlyai.sync

/**
 * Manages sync-related preferences including last sync timestamps
 * Used for smart sync to avoid unnecessary API calls
 *
 * Platform-specific implementations:
 * - Android: Uses SharedPreferences
 * - iOS: Uses NSUserDefaults
 */
expect class SyncPreferences {
    /**
     * Get the timestamp of the last successful sync
     * Returns null if never synced
     */
    fun getLastSyncTimestamp(): String?

    /**
     * Save the timestamp of a successful sync
     * @param timestamp ISO 8601 formatted timestamp
     */
    fun saveLastSyncTimestamp(timestamp: String)

    /**
     * Clear all sync preferences
     * Useful for logout or reset scenarios
     */
    fun clearSync()

    /**
     * Check if sync is needed based on time interval
     * @param intervalHours Number of hours before sync is considered stale
     * @return true if sync is needed, false otherwise
     */
    fun shouldSync(intervalHours: Int = 24): Boolean
}
