package com.kashyap.wizlyai.data.local

import com.kashyap.wizlyai.data.local.dao.CourseCacheDao
import com.kashyap.wizlyai.data.local.entity.CourseEntity
import com.kashyap.wizlyai.data.local.entity.CourseMaterialEntity

/** Room-backed [CourseCacheStore] (tables added in DB v5). */
class RoomCourseCacheStore(private val dao: CourseCacheDao) : CourseCacheStore {
    override fun saveCourses(userId: String, rows: List<CacheRow>) {
        val now = System.currentTimeMillis()
        dao.replaceCourses(userId, rows.map { CourseEntity(it.id, userId, it.json, it.updatedAt, now) })
    }

    override fun loadCourses(userId: String): List<CacheRow> =
        dao.coursesForUser(userId).map { CacheRow(it.id, it.payloadJson, it.updatedAt) }

    override fun saveMaterials(courseId: String, rows: List<CacheRow>) {
        val now = System.currentTimeMillis()
        dao.replaceMaterials(courseId, rows.map { CourseMaterialEntity(it.id, courseId, it.json, it.updatedAt, now) })
    }

    override fun loadMaterials(courseId: String): List<CacheRow> =
        dao.materialsForCourse(courseId).map { CacheRow(it.id, it.payloadJson, it.updatedAt) }

    override fun deleteCourse(courseId: String) = dao.deleteCourseAndMaterials(courseId)

    override fun clear() {
        dao.clearMaterials()
        dao.clearCourses()
    }
}

/** Register the Room course cache and the JSON file store (call once from MainActivity). */
object AndroidCourseCache {
    fun initialize(context: android.content.Context) {
        CourseCache.store = RoomCourseCacheStore(WizlyDatabase.getDatabase(context.applicationContext).courseCacheDao())
        LocalJson.store = FileJsonStore(java.io.File(context.applicationContext.filesDir, "wizly_json"))
    }
}
