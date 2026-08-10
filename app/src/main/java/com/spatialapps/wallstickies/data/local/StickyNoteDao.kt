package com.spatialapps.wallstickies.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StickyNoteDao {
    @Query("SELECT * FROM stickies") fun observe(): Flow<List<StickyNoteEntity>>
    @Upsert suspend fun upsert(note: StickyNoteEntity)
    @Query("DELETE FROM stickies WHERE id = :id") suspend fun delete(id: String)
}
