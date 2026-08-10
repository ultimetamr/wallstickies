package com.spatialapps.wallstickies.domain.model

data class StickyNote(
    val id: String,
    val anchorUuid: String,
    val title: String,
    val body: String,
    val style: StickyStyle = StickyStyle.SOLID,
    val color: StickyColor = StickyColor.YELLOW,
    val todos: List<TodoItem> = emptyList(),
    val fallbackPosition: StickyPosition = StickyPosition(),
)

data class TodoItem(val id: String, val text: String, val done: Boolean = false)
data class StickyPosition(val x: Float = 0f, val y: Float = 1.5f, val z: Float = -1.2f)

enum class StickyStyle { SOLID, FROSTED }
enum class StickyColor { YELLOW, PINK, BLUE, GREEN, PURPLE, ORANGE }
