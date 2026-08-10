package com.spatialapps.wallstickies.data.repository

import com.spatialapps.wallstickies.domain.model.StickyNote
import kotlinx.coroutines.flow.Flow

interface StickyNoteRepository {
    fun observe(): Flow<List<StickyNote>>
    suspend fun save(note: StickyNote)
    suspend fun delete(id: String)
}
