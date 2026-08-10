package com.spatialapps.wallstickies.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StickyNoteEntity::class], version = 3, exportSchema = false)
abstract class StickyDatabase : RoomDatabase() {
    abstract fun stickyDao(): StickyNoteDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE stickies ADD COLUMN color TEXT NOT NULL DEFAULT 'YELLOW'") }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickies ADD COLUMN positionX REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stickies ADD COLUMN positionY REAL NOT NULL DEFAULT 1.5")
                db.execSQL("ALTER TABLE stickies ADD COLUMN positionZ REAL NOT NULL DEFAULT -1.2")
            }
        }
    }
}
