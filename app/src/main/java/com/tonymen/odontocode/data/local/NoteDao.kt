package com.tonymen.odontocode.data.local

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes")
    fun getAllNotes(): LiveData<List<NoteEntity>>

    @Transaction
    suspend fun insertOrUpdate(note: NoteEntity) {
        insert(note) // Debido a OnConflictStrategy.REPLACE, esto funcionará como una actualización
    }
}