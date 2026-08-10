package com.spatialapps.wallstickies.data.repository

import com.spatialapps.wallstickies.data.local.StickyNoteDao
import com.spatialapps.wallstickies.data.local.StickyNoteEntity
import com.spatialapps.wallstickies.domain.model.StickyNote
import com.spatialapps.wallstickies.domain.model.StickyStyle
import com.spatialapps.wallstickies.domain.model.StickyColor
import com.spatialapps.wallstickies.domain.model.StickyPosition
import com.spatialapps.wallstickies.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class RoomStickyNoteRepository(private val dao: StickyNoteDao) : StickyNoteRepository {
    override fun observe(): Flow<List<StickyNote>> = dao.observe().map { notes -> notes.map(StickyNoteEntity::toDomain) }
    override suspend fun save(note: StickyNote) = dao.upsert(note.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

internal fun StickyNote.toEntity() = StickyNoteEntity(id, anchorUuid, title, body, style.name, color.name,
    JSONArray().apply {
        todos.forEach { todo -> put(org.json.JSONObject().put("id", todo.id).put("done", todo.done).put("text", todo.text)) }
    }.toString(), fallbackPosition.x, fallbackPosition.y, fallbackPosition.z)

internal fun StickyNoteEntity.toDomain() = StickyNote(id, anchorUuid, title, body,
    StickyStyle.valueOf(style), StickyColor.valueOf(color), JSONArray(todosJson).let { items ->
        List(items.length()) { index ->
            runCatching {
                items.getJSONObject(index).let { TodoItem(it.getString("id"), it.getString("text"), it.getBoolean("done")) }
            }.getOrElse {
                // ponytail: compatibility for v1 pipe-delimited todo rows; rewrite occurs on the next save.
                items.getString(index).split('|', limit = 3).let { TodoItem(it[0], it[2], it[1].toBoolean()) }
            }
        }
    }, StickyPosition(positionX, positionY, positionZ))
