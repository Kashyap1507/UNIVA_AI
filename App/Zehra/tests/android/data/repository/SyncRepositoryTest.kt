package com.kashyap.wizlyai.data.repository

import android.content.Context
import android.util.Log
import com.kashyap.wizlyai.data.local.WizlyDatabase
import com.kashyap.wizlyai.data.local.dao.AppSettingsDao
import com.kashyap.wizlyai.data.local.dao.UsageStatsDao
import com.kashyap.wizlyai.data.local.dao.UserDao
import com.kashyap.wizlyai.data.local.entity.AppSettingsEntity
import com.kashyap.wizlyai.data.local.entity.UserEntity
import com.kashyap.wizlyai.network.models.AppSettings
import com.kashyap.wizlyai.network.models.BootstrapData
import com.kashyap.wizlyai.sync.SyncPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncRepositoryTest {
    private lateinit var mockContext: Context
    private lateinit var mockDatabase: WizlyDatabase
    private lateinit var mockUserDao: UserDao
    private lateinit var mockUsageStatsDao: UsageStatsDao
    private lateinit var mockAppSettingsDao: AppSettingsDao
    private lateinit var repository: SyncRepository

    private val bootstrapTimestamp = "2025-10-11T12:00:00Z"

    @BeforeTest
    fun setup() {
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences("wizly_sync", Context.MODE_PRIVATE) } returns mockk(relaxed = true)
        mockDatabase = mockk(relaxed = true)
        mockUserDao = mockk(relaxed = true)
        mockUsageStatsDao = mockk(relaxed = true)
        mockAppSettingsDao = mockk(relaxed = true)

        every { mockDatabase.userDao() } returns mockUserDao
        every { mockDatabase.usageStatsDao() } returns mockUsageStatsDao
        every { mockDatabase.appSettingsDao() } returns mockAppSettingsDao

        mockkObject(WizlyDatabase.Companion)
        every { WizlyDatabase.getDatabase(any()) } returns mockDatabase

        mockkConstructor(SyncPreferences::class)
        every { anyConstructed<SyncPreferences>().shouldSync(any()) } returns true
        every { anyConstructed<SyncPreferences>().getLastSyncTimestamp() } returns null
        every { anyConstructed<SyncPreferences>().saveLastSyncTimestamp(any()) } just Runs
        every { anyConstructed<SyncPreferences>().clearSync() } just Runs

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        repository = SyncRepository(mockContext)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `syncUser inserts entity`() =
        runTest {
            val user =
                UserEntity(
                    id = "user123",
                    email = "test@example.com",
                    name = "Test User",
                    authProvider = "email",
                    createdAt = bootstrapTimestamp,
                    updatedAt = bootstrapTimestamp,
                    lastLogin = bootstrapTimestamp,
                    syncStatus = "synced",
                )
            coEvery { mockUserDao.insert(user) } returns 1L

            val result = repository.syncUser(user)

            assertTrue(result.isSuccess)
            coVerify { mockUserDao.insert(user) }
        }

    @Test
    fun `syncBootstrapData inserts app settings and saves timestamp`() =
        runTest {
            val bootstrapData =
                sampleBootstrapData().copy(
                    appSettings =
                        AppSettings(
                            cloudSyncEnabled = false,
                            cellularSyncEnabled = false,
                            scanQuality = "LARGE",
                            createdAt = bootstrapTimestamp,
                            updatedAt = bootstrapTimestamp,
                        ),
                )
            val userId = "student123"

            coEvery { mockAppSettingsDao.getByUserId(userId) } returns null
            coEvery { mockAppSettingsDao.insert(any()) } returns 1L
            val result = repository.syncBootstrapData(bootstrapData, userId)

            assertTrue(result.isSuccess)
            val stats = result.getOrNull()
            assertNotNull(stats)
            assertEquals(1, stats.itemsSynced) // app_settings
            assertEquals(bootstrapTimestamp, stats.syncTimestamp)

            coVerify { mockAppSettingsDao.insert(any()) }
            verify { anyConstructed<SyncPreferences>().saveLastSyncTimestamp(bootstrapTimestamp) }
        }

    @Test
    fun `syncBootstrapData keeps newer local app settings`() =
        runTest {
            val bootstrapData =
                sampleBootstrapData().copy(
                    appSettings =
                        AppSettings(
                            cloudSyncEnabled = false,
                            cellularSyncEnabled = false,
                            scanQuality = "LARGE",
                            createdAt = bootstrapTimestamp,
                            updatedAt = bootstrapTimestamp,
                        ),
                )
            val userId = "student123"

            coEvery { mockAppSettingsDao.getByUserId(userId) } returns AppSettingsEntity.createDefault(userId, "2025-10-12T12:00:00Z")
            coEvery { mockAppSettingsDao.update(any()) } just Runs

            val result = repository.syncBootstrapData(bootstrapData, userId)

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { mockAppSettingsDao.insert(any()) }
            coVerify(exactly = 0) { mockAppSettingsDao.update(any()) }
        }

    @Test
    fun `shouldSync delegates to SyncPreferences`() {
        every { anyConstructed<SyncPreferences>().shouldSync(24) } returns true

        assertTrue(repository.shouldSync())
        verify { anyConstructed<SyncPreferences>().shouldSync(24) }
    }

    @Test
    fun `getLastSyncTimestamp reads from SyncPreferences`() {
        every { anyConstructed<SyncPreferences>().getLastSyncTimestamp() } returns bootstrapTimestamp

        assertEquals(bootstrapTimestamp, repository.getLastSyncTimestamp())
        verify { anyConstructed<SyncPreferences>().getLastSyncTimestamp() }
    }

    @Test
    fun `clearSyncState clears tables and preferences`() =
        runTest {
            every { mockDatabase.clearAllTables() } just Runs
            every { anyConstructed<SyncPreferences>().clearSync() } just Runs

            val result = repository.clearSyncState()

            assertTrue(result.isSuccess)
            verify { mockDatabase.clearAllTables() }
            verify { anyConstructed<SyncPreferences>().clearSync() }
        }

    @Test
    fun `getSyncStats returns counts from DAOs`() =
        runTest {
            coEvery { mockUserDao.getCount() } returns 1
            coEvery { mockAppSettingsDao.getCount() } returns 1

            val stats = repository.getSyncStats()

            assertEquals(mapOf("users" to 1, "app_settings" to 1), stats)
        }

    @Test
    fun `getLastSyncTimestamp returns null when not set`() {
        every { anyConstructed<SyncPreferences>().getLastSyncTimestamp() } returns null

        assertNull(repository.getLastSyncTimestamp())
    }

    private fun sampleBootstrapData(): BootstrapData = BootstrapData(syncTimestamp = bootstrapTimestamp, appSettings = null)
}
