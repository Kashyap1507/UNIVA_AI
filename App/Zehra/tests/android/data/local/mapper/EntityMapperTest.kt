package com.kashyap.wizlyai.data.local.mapper

import com.kashyap.wizlyai.network.models.AppSettings
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EntityMapperTest {
    private val timestamp = "2025-10-11T12:00:00Z"

    @Test
    fun `maps AuthUser to UserEntity`() {
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "google",
            )

        val entity = EntityMapper.toEntity(authUser, timestamp)

        assertEquals("user123", entity.id)
        assertEquals("test@example.com", entity.email)
        assertEquals("google", entity.authProvider)
        assertEquals("test", entity.name) // falls back to email prefix
        assertEquals(timestamp, entity.createdAt)
        assertEquals(timestamp, entity.updatedAt)
        assertEquals(timestamp, entity.lastLogin)
        assertEquals("synced", entity.syncStatus)
    }

    @Test
    fun `maps the user profile to UserEntity with referral fields`() {
        val profile =
            UserProfile(
                id = "student456",
                email = "asha@uwindsor.ca",
                name = "Asha Patel",
                authProvider = "email",
                verified = true,
                isActive = true,
                referralCode = "ABC1DE",
                referralsRemaining = 3,
                accountType = "referral_limited",
            )

        val entity = EntityMapper.toEntity(profile, timestamp)

        assertEquals("student456", entity.id)
        assertEquals("Asha Patel", entity.name)
        assertEquals("ABC1DE", entity.referralCode)
        assertEquals(3, entity.referralsRemaining)
        assertEquals("referral_limited", entity.accountType)
        assertEquals(timestamp, entity.createdAt)
    }

    @Test
    fun `maps app settings and falls back to defaults`() {
        val synced =
            EntityMapper.toEntity(
                AppSettings(cloudSyncEnabled = false, cellularSyncEnabled = true, scanQuality = "MEDIUM", updatedAt = timestamp),
                "user1",
                timestamp,
            )
        assertFalse(synced.cloudSyncEnabled)
        assertEquals("MEDIUM", synced.scanQuality)
        assertEquals("synced", synced.syncStatus)

        val defaults = EntityMapper.toEntity(null, "user1", timestamp)
        assertEquals("user1", defaults.userId)
        assertEquals(EntityMapper.toModel(defaults).scanQuality, defaults.scanQuality)
    }
}
