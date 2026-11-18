package com.kashyap.wizlyai.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.ZERO

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleCacheTest {
    @BeforeTest
    fun setUp() {
        SimpleCache.instance.clear()
    }

    @AfterTest
    fun tearDown() {
        SimpleCache.instance.clear()
    }

    @Test
    fun `get caches value within ttl`() =
        runTest {
            var fetchCount = 0

            val first =
                SimpleCache.instance.get("children") {
                    fetchCount++
                    "value-1"
                }
            val second =
                SimpleCache.instance.get("children") {
                    fetchCount++
                    "value-2"
                }

            assertEquals("value-1", first)
            assertEquals("value-1", second)
            assertEquals(1, fetchCount)
        }

    @Test
    fun `get refetches when ttl is zero`() =
        runTest {
            var fetchCount = 0

            SimpleCache.instance.get("documents") {
                fetchCount++
                "initial"
            }

            SimpleCache.instance.get("documents", ttl = ZERO) {
                fetchCount++
                "refetched"
            }

            assertEquals(2, fetchCount)
        }

    @Test
    fun `invalidate removes cached key`() =
        runTest {
            var fetchCount = 0

            SimpleCache.instance.get("progress_child") {
                fetchCount++
                "cached"
            }

            SimpleCache.instance.invalidate("progress_child")

            val refreshed =
                SimpleCache.instance.get("progress_child") {
                    fetchCount++
                    "fresh"
                }

            assertEquals("fresh", refreshed)
            assertEquals(2, fetchCount)
        }

    @Test
    fun `invalidate pattern removes matching keys only`() =
        runTest {
            SimpleCache.instance.set("documents_child_1", "doc1")
            SimpleCache.instance.set("documents_child_1_recent", "doc2")
            SimpleCache.instance.set("progress_child_1", "progress")

            SimpleCache.instance.invalidatePattern("documents_child_1")

            var fetchCount = 0
            val reloaded1: String =
                SimpleCache.instance.get("documents_child_1") {
                    fetchCount++
                    "reloaded-1"
                }
            val reloaded2: String =
                SimpleCache.instance.get("documents_child_1_recent") {
                    fetchCount++
                    "reloaded-2"
                }

            assertEquals(2, fetchCount)
            assertEquals("reloaded-1", reloaded1)
            assertEquals("reloaded-2", reloaded2)

            val retained: String =
                SimpleCache.instance.get("progress_child_1") {
                    fail("progress entry should still be cached")
                }

            assertEquals("progress", retained)
        }

    @Test
    fun `clear removes all cached entries`() =
        runTest {
            var fetchCount = 0

            SimpleCache.instance.set("analytics_child", 42)
            SimpleCache.instance.clear()

            val value =
                SimpleCache.instance.get("analytics_child") {
                    fetchCount++
                    100
                }

            assertEquals(100, value)
            assertEquals(1, fetchCount)
        }
}
