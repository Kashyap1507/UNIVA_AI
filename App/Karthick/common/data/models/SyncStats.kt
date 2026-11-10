package com.kashyap.wizlyai.data.models

/**
 * Settings sync statistics data class
 *
 * Tracks the results of a settings sync operation.
 * Used by both Android and iOS AppSettingsSyncManager implementations.
 *
 * @param settingsSynced Number of settings records successfully synced
 * @param failures Number of sync failures
 * @param durationMs Time taken for sync operation in milliseconds
 */
data class SettingsSyncStats(
    val settingsSynced: Int,
    val failures: Int,
    val durationMs: Long,
)
