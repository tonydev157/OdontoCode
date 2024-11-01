package com.tonymen.odontocode.data
import com.tonymen.odontocode.data.local.NoteEntity

data class Note(
    val id: String = "",           // ID único del documento en Firestore
    val userId: String,           // ID del usuario que escribió la nota
    val nameNote: String,         // Título de la nota
    val content: String,          // Contenido de la nota
    val dateCreated: Long,        // Fecha en que se creó la nota
    val lastModified: Long = dateCreated // Fecha de última modificación, por defecto igual a la fecha de creación
) {
    constructor() : this("", "", "", "", 0L)  // Constructor vacío para Firestore

    // Función para convertir un objeto Note a una entidad NoteEntity
    fun toEntity(): NoteEntity {
        return NoteEntity(
            id = this.id,
            userId = this.userId,
            nameNote = this.nameNote,
            content = this.content,
            dateCreated = this.dateCreated,
            lastModified = this.lastModified
        )
    }
}
