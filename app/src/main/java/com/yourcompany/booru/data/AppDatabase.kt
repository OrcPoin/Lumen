package com.yourcompany.booru.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ImagePostEntity::class, VideoPost::class], version = 5) // Updated to version 5
abstract class AppDatabase : RoomDatabase() {
    abstract fun imagePostDao(): ImagePostDao
    abstract fun videoPostDao(): VideoPostDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) { // С 1 на 2
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE image_posts 
                    ADD COLUMN width INTEGER DEFAULT NULL,
                    ADD COLUMN height INTEGER DEFAULT NULL,
                    ADD COLUMN score INTEGER DEFAULT NULL,
                    ADD COLUMN createdAt TEXT DEFAULT NULL
                    """
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) { // С 2 на 3
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE image_posts 
                    ADD COLUMN sourceType TEXT DEFAULT NULL
                    """
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) { // С 3 на 4
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE video_posts (
                        id INTEGER PRIMARY KEY NOT NULL,
                        videoUrl TEXT NOT NULL,
                        title TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        isFavorited INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) { // С 4 на 5: Change id type to BIGINT
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create a new table with id as BIGINT
                database.execSQL(
                    """
                    CREATE TABLE image_posts_new (
                        id BIGINT PRIMARY KEY NOT NULL,
                        fileUrl TEXT,
                        previewUrl TEXT,
                        image TEXT,
                        directory INTEGER,
                        tags TEXT,
                        tagsString TEXT,
                        width INTEGER,
                        height INTEGER,
                        score INTEGER,
                        createdAt TEXT,
                        isFavorited INTEGER NOT NULL DEFAULT 0,
                        sourceType TEXT
                    )
                    """
                )

                // Copy data from the old table to the new table
                database.execSQL(
                    """
                    INSERT INTO image_posts_new (
                        id, fileUrl, previewUrl, image, directory, tags, tagsString, 
                        width, height, score, createdAt, isFavorited, sourceType
                    )
                    SELECT 
                        id, fileUrl, previewUrl, image, directory, tags, tagsString, 
                        width, height, score, createdAt, isFavorited, sourceType
                    FROM image_posts
                    """
                )

                // Drop the old table and rename the new table
                database.execSQL("DROP TABLE image_posts")
                database.execSQL("ALTER TABLE image_posts_new RENAME TO image_posts")
            }
        }
    }
}