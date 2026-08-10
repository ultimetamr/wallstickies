package com.spatialapps.wallstickies.domain.usecase

import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialapps.wallstickies.data.repository.StickyNoteRepository
import com.spatialapps.wallstickies.data.repository.WorldAnchorRepository
import com.spatialapps.wallstickies.domain.model.StickyNote
import com.spatialapps.wallstickies.domain.model.StickyStyle
import com.spatialapps.wallstickies.domain.model.TodoItem
import com.spatialapps.wallstickies.domain.model.StickyColor
import com.spatialapps.wallstickies.domain.model.StickyPosition
import java.util.UUID

class ManageStickyNotes(
    private val notes: StickyNoteRepository,
    private val anchors: WorldAnchorRepository,
) {
    suspend fun create(
        id: String,
        title: String,
        body: String,
        position: Vector3,
        rotation: EulerAngles,
        todos: List<TodoItem> = emptyList(),
        style: StickyStyle = StickyStyle.SOLID,
        color: StickyColor = StickyColor.YELLOW,
    ): StickyNote? {
        val anchorUuid = anchors.create(position, rotation, "sticky-$id") ?: return null
        return StickyNote(id = id, anchorUuid = anchorUuid, title = title, body = body, todos = todos, style = style, color = color, fallbackPosition = position.toStickyPosition())
            .also { notes.save(it) }
    }

    suspend fun delete(note: StickyNote) {
        notes.delete(note.id)
        runCatching { UUID.fromString(note.anchorUuid) }.getOrNull()?.let { anchors.remove(it) }
    }

    suspend fun update(note: StickyNote) = notes.save(note)

    suspend fun relocate(note: StickyNote, position: Vector3, rotation: EulerAngles): StickyNote? {
        val replacement = anchors.create(position, rotation, "sticky-${note.id}") ?: return null
        val moved = note.copy(anchorUuid = replacement, fallbackPosition = position.toStickyPosition())
        notes.save(moved)
        runCatching { UUID.fromString(note.anchorUuid) }.getOrNull()?.let { anchors.remove(it) }
        return moved
    }

    suspend fun restore() = anchors.loadAll()

    fun subscribeAnchorUpdates(onAnchorChanged: (com.pico.spatial.sense.world.WorldAnchor) -> Unit) =
        anchors.subscribe(onAnchorChanged)
}

private fun Vector3.toStickyPosition() = StickyPosition(x, y, z)
