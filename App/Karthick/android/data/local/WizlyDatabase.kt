package com.kashyap.wizlyai.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kashyap.wizlyai.data.local.dao.*
import com.kashyap.wizlyai.data.local.entity.*

/**
 * Room database for Wizly app with offline-first support
 *
 * Version History:
 * - Version 1: Initial database with all entities (pre-production development)
 * - Version 2: Added concept_cards_json column to user_study_collections table
 * - Version 3: Added generated_plan and plan_metadata columns to user_study_collections table
 * - Version 4: Added subscription fields to users table and usage_stats table
 * - Version 5: Added courses and course_materials cache tables (university migration Phase 2)
 * - Version 6: Documents are owned by user_id (was child_id); the K-12 tables are dropped
 *   (children, grades, subjects, topics, concepts, skills, homework, worksheets,
 *   saved_worksheets, user_study_collections, worksheet_metadata, calendar_events)
 */
@Database(
    entities = [
        DocumentEntity::class,
        DocumentPageEntity::class,
        UserEntity::class,
        AppSettingsEntity::class,
        UsageStatsEntity::class,
        CourseEntity::class,
        CourseMaterialEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class WizlyDatabase : RoomDatabase() {
    // Document DAOs
    abstract fun documentDao(): DocumentDao

    abstract fun documentPageDao(): DocumentPageDao

    // DAOs
    abstract fun userDao(): UserDao

    abstract fun appSettingsDao(): AppSettingsDao

    abstract fun usageStatsDao(): UsageStatsDao

    // Course cache DAO (v5)
    abstract fun courseCacheDao(): CourseCacheDao

    companion object {
        @Volatile
        private var INSTANCE: WizlyDatabase? = null

        /**
         * Migration from version 1 to version 2
         * Adds concept_cards_json column to user_study_collections table
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_study_collections ADD COLUMN concept_cards_json TEXT")
                Log.d("WizlyDatabase", "✅ Migration 1→2: Added concept_cards_json column")
            }
        }

        /**
         * Migration from version 2 to version 3
         * Adds generated_plan and plan_metadata columns to user_study_collections table
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_study_collections ADD COLUMN generated_plan TEXT")
                db.execSQL("ALTER TABLE user_study_collections ADD COLUMN plan_metadata TEXT")
                Log.d("WizlyDatabase", "✅ Migration 2→3: Added generated_plan and plan_metadata columns")
            }
        }

        /**
         * Migration from version 3 to version 4
         * Adds subscription fields to users table and creates usage_stats table
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add subscription fields to users table
                db.execSQL("ALTER TABLE users ADD COLUMN subscription_tier TEXT NOT NULL DEFAULT 'free'")
                db.execSQL("ALTER TABLE users ADD COLUMN subscription_status TEXT NOT NULL DEFAULT 'active'")
                db.execSQL("ALTER TABLE users ADD COLUMN account_type TEXT NOT NULL DEFAULT 'full'")
                db.execSQL("ALTER TABLE users ADD COLUMN referral_code TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN referral_limit INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE users ADD COLUMN referrals_remaining INTEGER NOT NULL DEFAULT 5")

                // Create usage_stats table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS usage_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        user_id TEXT NOT NULL,
                        feature_name TEXT NOT NULL,
                        used_count INTEGER NOT NULL DEFAULT 0,
                        limit_count INTEGER NOT NULL DEFAULT 0,
                        reset_time TEXT,
                        last_updated TEXT NOT NULL
                    )
                """)

                // Create index for usage_stats
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_usage_stats_user_id_feature_name ON usage_stats(user_id, feature_name)")

                Log.d("WizlyDatabase", "✅ Migration 3→4: Added subscription fields and usage_stats table")
            }
        }

        /**
         * Migration from version 4 to version 5
         * Adds the courses and course_materials cache tables.
         * SQL matches Room's generated schema for CourseEntity / CourseMaterialEntity.
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { db.execSQL(it) }
                Log.d("WizlyDatabase", "✅ Migration 4→5: Added courses and course_materials tables")
            }
        }

        internal val MIGRATION_4_5_SQL =
            listOf(
                "CREATE TABLE IF NOT EXISTS `courses` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `updated_at` TEXT, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_courses_user_id` ON `courses` (`user_id`)",
                "CREATE TABLE IF NOT EXISTS `course_materials` (`id` TEXT NOT NULL, `course_id` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `updated_at` TEXT, `cached_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_course_materials_course_id` ON `course_materials` (`course_id`)",
            )

        /**
         * Migration from version 5 to version 6
         * Documents move from child_id to user_id (the owner of the old learner record)
         * and lose the K-12 columns; the K-12 tables are dropped.
         *
         * Pages are copied out and back so the order is safe whether or not
         * foreign keys are enforced during the migration (dropping a parent
         * table with enforcement on would cascade-delete its pages).
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach { db.execSQL(it) }
                Log.d("WizlyDatabase", "✅ Migration 5→6: documents keyed by user_id, K-12 tables dropped")
            }
        }

        internal const val DOCUMENTS_CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `page_count` INTEGER NOT NULL, `thumbnail_path` TEXT, `status` TEXT NOT NULL, `processing_progress` INTEGER NOT NULL, `remote_thumbnail_url` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `last_synced_at` TEXT, `is_deleted` INTEGER NOT NULL, `deleted_at` TEXT, PRIMARY KEY(`id`))"

        internal const val DOCUMENT_PAGES_CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS `document_pages` (`id` TEXT NOT NULL, `document_id` TEXT NOT NULL, `page_number` INTEGER NOT NULL, `image_path` TEXT NOT NULL, `thumbnail_path` TEXT, `width` INTEGER, `height` INTEGER, `file_size` INTEGER, `remote_image_url` TEXT, `remote_thumbnail_url` TEXT, `storage_path` TEXT, `uploaded_at` TEXT NOT NULL, `sync_status` TEXT NOT NULL, `last_synced_at` TEXT, `is_deleted` INTEGER NOT NULL, `deleted_at` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`document_id`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

        internal val DOCUMENTS_INDEX_SQL =
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_documents_user_id` ON `documents` (`user_id`)",
                "CREATE INDEX IF NOT EXISTS `index_documents_sync_status` ON `documents` (`sync_status`)",
                "CREATE INDEX IF NOT EXISTS `index_documents_created_at` ON `documents` (`created_at`)",
                "CREATE INDEX IF NOT EXISTS `index_documents_is_deleted` ON `documents` (`is_deleted`)",
            )

        internal val DOCUMENT_PAGES_INDEX_SQL =
            listOf(
                "CREATE INDEX IF NOT EXISTS `index_document_pages_document_id` ON `document_pages` (`document_id`)",
                "CREATE INDEX IF NOT EXISTS `index_document_pages_page_number` ON `document_pages` (`page_number`)",
                "CREATE INDEX IF NOT EXISTS `index_document_pages_sync_status` ON `document_pages` (`sync_status`)",
            )

        private const val DOCUMENT_COLUMNS =
            "`id`, `user_id`, `title`, `type`, `page_count`, `thumbnail_path`, `status`, `processing_progress`, `remote_thumbnail_url`, `created_at`, `updated_at`, `sync_status`, `last_synced_at`, `is_deleted`, `deleted_at`"

        private const val PAGE_COLUMNS =
            "`id`, `document_id`, `page_number`, `image_path`, `thumbnail_path`, `width`, `height`, `file_size`, `remote_image_url`, `remote_thumbnail_url`, `storage_path`, `uploaded_at`, `sync_status`, `last_synced_at`, `is_deleted`, `deleted_at`"

        internal val LEGACY_TABLES_V5 =
            listOf(
                "homework", "worksheets", "saved_worksheets", "worksheet_metadata", "user_study_collections",
                "calendar_events", "skills", "concepts", "topics", "subjects", "grades", "children",
            )

        internal val MIGRATION_5_6_SQL: List<String> =
            listOf(
                DOCUMENTS_CREATE_SQL.replace("`documents`", "`documents_new`"),
                // The owner is the user behind the old learner record (children.parent_id)
                "INSERT INTO `documents_new` ($DOCUMENT_COLUMNS) SELECT `id`, " +
                    "COALESCE((SELECT c.`parent_id` FROM `children` c WHERE c.`id` = d.`child_id`), d.`child_id`), " +
                    "`title`, `type`, `page_count`, `thumbnail_path`, `status`, `processing_progress`, `remote_thumbnail_url`, " +
                    "`created_at`, `updated_at`, `sync_status`, `last_synced_at`, `is_deleted`, `deleted_at` FROM `documents` d",
                "CREATE TABLE `document_pages_backup` AS SELECT $PAGE_COLUMNS FROM `document_pages`",
                "DROP TABLE `document_pages`",
                "DROP TABLE `documents`",
                "ALTER TABLE `documents_new` RENAME TO `documents`",
                DOCUMENT_PAGES_CREATE_SQL,
                "INSERT INTO `document_pages` ($PAGE_COLUMNS) SELECT $PAGE_COLUMNS FROM `document_pages_backup` " +
                    "WHERE `document_id` IN (SELECT `id` FROM `documents`)",
                "DROP TABLE `document_pages_backup`",
            ) + DOCUMENTS_INDEX_SQL + DOCUMENT_PAGES_INDEX_SQL +
                LEGACY_TABLES_V5.map { "DROP TABLE IF EXISTS `$it`" }

        fun getDatabase(context: Context): WizlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        WizlyDatabase::class.java,
                        "wizly_database",
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .fallbackToDestructiveMigration() // Since app is not live yet
                        .addCallback(
                            object : RoomDatabase.Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    Log.d("WizlyDatabase", "✅ Database v6 created successfully at: ${db.path}")
                                    Log.d("WizlyDatabase", "📊 Tables: documents, document_pages, users, app_settings, usage_stats, courses, course_materials")
                                }

                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    super.onOpen(db)
                                    Log.d("WizlyDatabase", "📂 Database opened (v6): ${db.path}")
                                }
                            },
                        )
                        .build()

                INSTANCE = instance
                Log.d("WizlyDatabase", "🔧 Database instance initialized (v6)")
                instance
            }
        }

        /**
         * For testing purposes only
         */
        internal fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
