package com.kashyap.wizlyai.sync

import com.kashyap.wizlyai.cache.SimpleCache
import com.kashyap.wizlyai.data.repository.SyncRepository
import com.kashyap.wizlyai.data.repository.SyncStats
import com.kashyap.wizlyai.network.models.AppSettings
import com.kashyap.wizlyai.network.models.AuthUser
import com.kashyap.wizlyai.network.models.BootstrapData
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Unit tests for SyncCoordinator
 *
 * Tests orchestration logic, cache invalidation, and error handling.
 */
class SyncCoordinatorTest {
    private lateinit var mockSyncRepository: SyncRepository
    private lateinit var testScope: CoroutineScope
    private lateinit var syncCoordinator: SyncCoordinator
    private lateinit var mockSimpleCache: SimpleCache

    @BeforeTest
    fun setup() {
        // Mock dependencies
        mockSyncRepository = mockk(relaxed = true)
        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        // Mock SimpleCache singleton access
        mockSimpleCache = mockk(relaxed = true)
        mockkObject(SimpleCache.Companion)
        every { SimpleCache.instance } returns mockSimpleCache

        // Create SyncCoordinator with test scope
        syncCoordinator =
            SyncCoordinator(
                syncRepository = mockSyncRepository,
                documentSyncManager = null,
                appSettingsSyncManager = null,
                scope = testScope,
            )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        unmockkAll()
    }

    @Test
    fun `test syncBootstrapData syncs user and bootstrap data successfully`() {
        // Given
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "email",
            )
        val bootstrapData =
            BootstrapData("2025-10-11T12:00:00Z").copy(
                appSettings = AppSettings(cloudSyncEnabled = true),
            )
        val syncStats = SyncStats(itemsSynced = 3, durationMs = 100, syncTimestamp = "2025-10-11T12:00:00Z")

        // Mock successful sync
        coEvery { mockSyncRepository.syncUser(any()) } returns Result.success(Unit)
        coEvery { mockSyncRepository.syncBootstrapData(any(), any()) } returns Result.success(syncStats)

        var callbackResult: Result<SyncStats>? = null

        // When
        syncCoordinator.syncBootstrapData(bootstrapData, authUser) { result ->
            callbackResult = result
        }

        // Then
        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isSuccess)
        assertEquals(3, callbackResult!!.getOrNull()?.itemsSynced)

        coVerify(exactly = 1) { mockSyncRepository.syncUser(any()) }
        coVerify(exactly = 1) { mockSyncRepository.syncBootstrapData(bootstrapData, "user123") }
        verify(atLeast = 1) { mockSimpleCache.invalidatePattern(any()) }
    }

    @Test
    fun `test syncBootstrapData fails when user sync fails`() {
        // Given
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "email",
            )
        val bootstrapData = BootstrapData("2025-10-11T12:00:00Z")

        // Mock user sync failure (user sync is critical - must succeed to prevent FK violations)
        coEvery { mockSyncRepository.syncUser(any()) } returns Result.failure(RuntimeException("User sync failed"))

        var callbackResult: Result<SyncStats>? = null

        // When
        syncCoordinator.syncBootstrapData(bootstrapData, authUser) { result ->
            callbackResult = result
        }

        // Then - Bootstrap sync should FAIL because user sync is critical
        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isFailure)

        coVerify(exactly = 1) { mockSyncRepository.syncUser(any()) }
        coVerify(exactly = 0) { mockSyncRepository.syncBootstrapData(any(), any()) }
    }

    @Test
    fun `test syncBootstrapData reports failure when bootstrap sync fails`() {
        // Given
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "email",
            )
        val bootstrapData = BootstrapData("2025-10-11T12:00:00Z")
        val exception = RuntimeException("Bootstrap sync failed")

        // Mock successful user sync but bootstrap sync failure
        coEvery { mockSyncRepository.syncUser(any()) } returns Result.success(Unit)
        coEvery { mockSyncRepository.syncBootstrapData(any(), any()) } returns Result.failure(exception)

        var callbackResult: Result<SyncStats>? = null

        // When
        syncCoordinator.syncBootstrapData(bootstrapData, authUser) { result ->
            callbackResult = result
        }

        // Then
        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isFailure)
        assertEquals(exception, callbackResult!!.exceptionOrNull())

        coVerify(exactly = 1) { mockSyncRepository.syncBootstrapData(bootstrapData, "user123") }
    }

    @Test
    fun `test syncBootstrapData invalidates all caches after successful sync`() {
        // Given
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "email",
            )
        val bootstrapData = BootstrapData("2025-10-11T12:00:00Z")
        val syncStats = SyncStats(itemsSynced = 0, durationMs = 50, syncTimestamp = "2025-10-11T12:00:00Z")

        coEvery { mockSyncRepository.syncUser(any()) } returns Result.success(Unit)
        coEvery { mockSyncRepository.syncBootstrapData(any(), any()) } returns Result.success(syncStats)

        // When
        syncCoordinator.syncBootstrapData(bootstrapData, authUser)

        // Then - Verify the document and course caches are invalidated
        verify { mockSimpleCache.invalidatePattern("documents") }
        verify { mockSimpleCache.invalidatePattern("courses") }
    }

    @Test
    fun `test smartSync skips when shouldSync returns false and not forced`() {
        // Given
        every { mockSyncRepository.shouldSync(any()) } returns false

        // When
        runBlocking { syncCoordinator.smartSync(force = false) }

        // Then
        verify { mockSyncRepository.shouldSync() }
        verify(exactly = 0) { mockSimpleCache.invalidatePattern(any()) }
    }

    @Test
    fun `test smartSync invalidates caches when forced`() {
        // Given
        every { mockSyncRepository.shouldSync(any()) } returns false

        // When
        runBlocking { syncCoordinator.smartSync(force = true) }

        // Then
        verify(atLeast = 1) { mockSimpleCache.invalidatePattern(any()) }
    }

    @Test
    fun `test smartSync invalidates caches when shouldSync returns true`() {
        // Given
        every { mockSyncRepository.shouldSync(any()) } returns true

        // When
        runBlocking { syncCoordinator.smartSync(force = false) }

        // Then
        verify(atLeast = 1) { mockSimpleCache.invalidatePattern(any()) }
    }

    @Test
    fun `test clearSyncData clears repository and all caches`() {
        // Given
        coEvery { mockSyncRepository.clearSyncState() } returns Result.success(Unit)

        // When
        val result = runBlocking { syncCoordinator.clearSyncData() }

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockSyncRepository.clearSyncState() }
        verify(exactly = 1) { mockSimpleCache.clear() }
    }

    @Test
    fun `test clearSyncData reports failure when repository fails`() {
        // Given
        val exception = RuntimeException("Clear failed")
        coEvery { mockSyncRepository.clearSyncState() } returns Result.failure(exception)

        // When
        val result = runBlocking { syncCoordinator.clearSyncData() }

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { mockSyncRepository.clearSyncState() }
    }

    @Test
    fun `test getSyncStats returns repository stats`() {
        // Given
        val expectedStats =
            mapOf(
                "users" to 1,
                "children" to 2,
                "grades" to 13,
                "subjects" to 6,
            )
        coEvery { mockSyncRepository.getSyncStats() } returns expectedStats

        // When
        val stats = runBlocking { syncCoordinator.getSyncStats() }

        // Then
        assertEquals(expectedStats, stats)
        coVerify(exactly = 1) { mockSyncRepository.getSyncStats() }
    }

    @Test
    fun `test shouldSync delegates to repository`() {
        // Given
        every { mockSyncRepository.shouldSync(24) } returns true

        // When
        val result = syncCoordinator.shouldSync(24)

        // Then
        assertTrue(result)
        verify(exactly = 1) { mockSyncRepository.shouldSync(24) }
    }

    @Test
    fun `test syncBootstrapData works without callback`() {
        // Given
        val authUser =
            AuthUser(
                id = "user123",
                email = "test@example.com",
                authProvider = "email",
            )
        val bootstrapData = BootstrapData("2025-10-11T12:00:00Z")
        val syncStats = SyncStats(itemsSynced = 0, durationMs = 50, syncTimestamp = "2025-10-11T12:00:00Z")

        coEvery { mockSyncRepository.syncUser(any()) } returns Result.success(Unit)
        coEvery { mockSyncRepository.syncBootstrapData(any(), any()) } returns Result.success(syncStats)

        // When - No callback provided
        syncCoordinator.syncBootstrapData(bootstrapData, authUser)

        // Then - Should complete without errors
        coVerify(exactly = 1) { mockSyncRepository.syncBootstrapData(bootstrapData, "user123") }
    }
}
