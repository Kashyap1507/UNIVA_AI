package com.kashyap.wizlyai.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verifyOrder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {
    private val generatedImpl =
        File("build/generated/ksp/android/androidDebug/java/com/kashyap/wizlyai/data/local/WizlyDatabase_Impl.java")

    @Test
    fun migrationRunsTheCourseTableStatementsInOrder() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } just runs

        WizlyDatabase.MIGRATION_4_5.migrate(db)

        verifyOrder { WizlyDatabase.MIGRATION_4_5_SQL.forEach { db.execSQL(it) } }
        assertEquals(4 to 5, WizlyDatabase.MIGRATION_4_5.startVersion to WizlyDatabase.MIGRATION_4_5.endVersion)
    }

    /**
     * Room validates the migrated schema against the entities when the DB
     * opens. Compare our SQL with the CREATE statements Room generated, so a
     * change to CourseEntity/CourseMaterialEntity can't silently break upgrades.
     */
    @Test
    fun migrationSqlMatchesRoomGeneratedSchema() {
        val generated = generatedImpl
        assertTrue(generated.exists(), "Run the KSP task first: ${generated.absolutePath}")
        val statements =
            Regex("\"(CREATE (?:TABLE|INDEX) IF NOT EXISTS `(?:index_)?course(?:s|_materials)[^\"]*)\"")
                .findAll(generated.readText())
                .map { it.groupValues[1] }
                .toList()
        assertEquals(WizlyDatabase.MIGRATION_4_5_SQL.sorted(), statements.sorted())
    }

    @Test
    fun migration5To6RunsItsStatementsInOrder() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } just runs

        WizlyDatabase.MIGRATION_5_6.migrate(db)

        verifyOrder { WizlyDatabase.MIGRATION_5_6_SQL.forEach { db.execSQL(it) } }
        assertEquals(5 to 6, WizlyDatabase.MIGRATION_5_6.startVersion to WizlyDatabase.MIGRATION_5_6.endVersion)
    }

    /** The rebuilt documents and document_pages tables must be exactly what Room expects at v6. */
    @Test
    fun migration5To6SqlMatchesRoomGeneratedSchema() {
        assertTrue(generatedImpl.exists(), "Run the KSP task first: ${generatedImpl.absolutePath}")
        val text = generatedImpl.readText()
        val statements =
            Regex("\"(CREATE (?:TABLE|INDEX) IF NOT EXISTS `(?:index_)?document(?:s|_pages)[`_][^\"]*)\"")
                .findAll(text)
                .map { it.groupValues[1].replace("\\`", "`") }
                .toSet()
        val ours =
            setOf(WizlyDatabase.DOCUMENTS_CREATE_SQL, WizlyDatabase.DOCUMENT_PAGES_CREATE_SQL) +
                WizlyDatabase.DOCUMENTS_INDEX_SQL + WizlyDatabase.DOCUMENT_PAGES_INDEX_SQL
        assertEquals(ours, statements)
    }

    /** Old pages are copied out before their parent table is dropped, and every K-12 table goes. */
    @Test
    fun migration5To6KeepsPagesAndDropsTheK12Tables() {
        val sql = WizlyDatabase.MIGRATION_5_6_SQL
        val backup = sql.indexOfFirst { it.startsWith("CREATE TABLE `document_pages_backup`") }
        val dropPages = sql.indexOf("DROP TABLE `document_pages`")
        val dropDocuments = sql.indexOf("DROP TABLE `documents`")
        val restore = sql.indexOfFirst { it.startsWith("INSERT INTO `document_pages`") }
        assertTrue(backup in 0 until dropPages && dropPages < dropDocuments && dropDocuments < restore)
        assertTrue(sql.any { "c.`parent_id`" in it && "d.`child_id`" in it }, "owner comes from the old learner record")
        listOf("children", "grades", "subjects", "topics", "concepts", "skills", "homework", "worksheets",
            "saved_worksheets", "user_study_collections", "worksheet_metadata", "calendar_events").forEach { table ->
            assertTrue("DROP TABLE IF EXISTS `$table`" in sql, "drops $table")
        }
    }
}
