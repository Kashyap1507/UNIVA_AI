package com.kashyap.wizlyai.data.local

/**
 * Offline cache for courses and their materials.
 *
 * Rows hold the id, the owning key and the full JSON payload, so the schema
 * doesn't change every time the API gains a field. Android implements this
 * with Room (`courses` / `course_materials` tables, DB v5), iOS with CoreData
 * (`CachedCourse` / `CachedCourseMaterial`, model version 2).
 *
 * Methods are blocking and must be called off the main thread.
 */
interface CourseCacheStore {
    /** Replace all cached courses for [userId]. */
    fun saveCourses(userId: String, rows: List<CacheRow>)

    fun loadCourses(userId: String): List<CacheRow>

    /** Replace all cached materials for [courseId]. */
    fun saveMaterials(courseId: String, rows: List<CacheRow>)

    fun loadMaterials(courseId: String): List<CacheRow>

    /** Remove a course and its materials. */
    fun deleteCourse(courseId: String)

    /** Remove everything (logout). */
    fun clear()
}

data class CacheRow(
    val id: String,
    val json: String,
    val updatedAt: String? = null,
)

/** No-op store used until the platform registers a real one (and in tests). */
object NoOpCourseCacheStore : CourseCacheStore {
    override fun saveCourses(userId: String, rows: List<CacheRow>) {}

    override fun loadCourses(userId: String): List<CacheRow> = emptyList()

    override fun saveMaterials(courseId: String, rows: List<CacheRow>) {}

    override fun loadMaterials(courseId: String): List<CacheRow> = emptyList()

    override fun deleteCourse(courseId: String) {}

    override fun clear() {}
}

/** Platform code registers its store here at startup (MainActivity / ContentView.swift). */
object CourseCache {
    var store: CourseCacheStore = NoOpCourseCacheStore
}
