package com.spatialapps.wallstickies.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stickies")
data class StickyNoteEntity(
    @PrimaryKey val id: String,
    val anchorUuid: String,
    val title: String,
    val body: String,
    val style: String,
    val color: String,
    val todosJson: String,
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
)
