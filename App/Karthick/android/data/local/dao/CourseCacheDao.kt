package com.kashyap.wizlyai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kashyap.wizlyai.data.local.entity.CourseEntity
import com.kashyap.wizlyai.data.local.entity.CourseMaterialEntity

@Dao
interface CourseCacheDao {
    @Query("SELECT * FROM courses WHERE user_id = :userId")
    fun coursesForUser(userId: String): List<CourseEntity>

    @Query("DELETE FROM courses WHERE user_id = :userId")
    fun deleteCoursesForUser(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCourses(courses: List<CourseEntity>)

    @Transaction
    fun replaceCourses(userId: String, courses: List<CourseEntity>) {
        deleteCoursesForUser(userId)
        insertCourses(courses)
    }

    @Query("SELECT * FROM course_materials WHERE course_id = :courseId")
    fun materialsForCourse(courseId: String): List<CourseMaterialEntity>

    @Query("DELETE FROM course_materials WHERE course_id = :courseId")
    fun deleteMaterialsForCourse(courseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMaterials(materials: List<CourseMaterialEntity>)

    @Transaction
    fun replaceMaterials(courseId: String, materials: List<CourseMaterialEntity>) {
        deleteMaterialsForCourse(courseId)
        insertMaterials(materials)
    }

    @Query("DELETE FROM courses WHERE id = :courseId")
    fun deleteCourse(courseId: String)

    @Transaction
    fun deleteCourseAndMaterials(courseId: String) {
        deleteMaterialsForCourse(courseId)
        deleteCourse(courseId)
    }

    @Query("DELETE FROM courses")
    fun clearCourses()

    @Query("DELETE FROM course_materials")
    fun clearMaterials()
}
