package com.tonymen.odontocode.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val nameNote: String,
    val content: String,
    val dateCreated: Long,
    val lastModified: Long
)