package com.spatialapps.wallstickies

import com.spatialapps.wallstickies.data.local.StickyNoteEntity
import com.spatialapps.wallstickies.data.repository.toDomain
import com.spatialapps.wallstickies.data.repository.toEntity
import com.spatialapps.wallstickies.domain.model.StickyColor
import com.spatialapps.wallstickies.domain.model.StickyNote
import com.spatialapps.wallstickies.domain.model.StickyStyle
import com.spatialapps.wallstickies.domain.model.StickyPosition
import com.spatialapps.wallstickies.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StickyNoteSerializationTest {
    @Test
    fun preserves_new_todos_and_style() {
        val note = StickyNote("id", "anchor", "title", "body", StickyStyle.SOLID, StickyColor.PURPLE, listOf(TodoItem("todo", "测试", true)))

        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun reads_v1_pipe_todo_rows() {
        val legacy = StickyNoteEntity("id", "anchor", "title", "body", "FROSTED", "YELLOW", "[\"todo|true|兼容旧数据\"]", 0f, 1.5f, -1.2f)

        assertEquals(TodoItem("todo", "兼容旧数据", true), legacy.toDomain().todos.single())
    }

    @Test
    fun preserves_last_known_spatial_position() {
        val note = StickyNote("id", "anchor", "title", "body", fallbackPosition = StickyPosition(1.2f, 0.8f, -2.4f))

        assertEquals(StickyPosition(1.2f, 0.8f, -2.4f), note.toEntity().toDomain().fallbackPosition)
    }
}
