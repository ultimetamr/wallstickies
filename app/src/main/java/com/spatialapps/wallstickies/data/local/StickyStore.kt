package com.spatialapps.wallstickies.data.local

import android.content.Context
import androidx.room.Room

object StickyStore {
    fun database(context: Context): StickyDatabase = Room.databaseBuilder(
        context.applicationContext, StickyDatabase::class.java, "wall-stickies.db"
    ).addMigrations(StickyDatabase.MIGRATION_1_2, StickyDatabase.MIGRATION_2_3).build()
}
