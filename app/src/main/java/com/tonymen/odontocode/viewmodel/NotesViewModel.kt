package com.tonymen.odontocode.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tonymen.odontocode.data.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotesViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // StateFlow que contiene la lista de notas
    private val _notesState = MutableStateFlow<List<Note>>(emptyList())
    val notesState: StateFlow<List<Note>> get() = _notesState

    // Lista de IDs de notas seleccionadas
    private val selectedNotes = mutableStateListOf<String>()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        listenToNotes() // Escuchar cambios en las notas al inicializar el ViewModel
    }

    // Método para escuchar cambios en las notas de Firestore
    private fun listenToNotes() {
        val userId = auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            Log.e("NotesViewModel", "No se puede cargar notas: El usuario no está autenticado")
            return
        }

        // Listener para recibir actualizaciones en tiempo real de las notas del usuario autenticado
        listenerRegistration = firestore.collection("notes")
            .whereEqualTo("userId", userId) // Filtrar por el usuario autenticado
            .orderBy("lastModified", com.google.firebase.firestore.Query.Direction.DESCENDING) // Ordenar por la última modificación
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e("NotesViewModel", "Error al cargar notas: ", exception)
                    _notesState.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val notesList = snapshot.documents.mapNotNull { it.toObject(Note::class.java) }
                    Log.d("NotesViewModel", "Notas cargadas: $notesList")
                    _notesState.value = notesList // Actualizamos las notas en tiempo real
                    clearSelectedNotes() // Limpiamos la selección después de cualquier actualización
                } else {
                    Log.d("NotesViewModel", "No se encontraron notas")
                    _notesState.value = emptyList()
                }
            }
    }

    // Método para forzar la recarga de las notas (usado cuando no dependemos del listener)
    fun refreshNotes() {
        val userId = auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            Log.e("NotesViewModel", "No se puede recargar notas: El usuario no está autenticado")
            return
        }

        firestore.collection("notes")
            .whereEqualTo("userId", userId) // Aseguramos que solo cargamos las notas del usuario
            .get()
            .addOnSuccessListener { result ->
                val notesList = result.documents.mapNotNull { it.toObject(Note::class.java) }
                _notesState.value = notesList.sortedByDescending { it.lastModified } // Ordenamos por la última modificación
            }
            .addOnFailureListener { exception ->
                Log.e("NotesViewModel", "Error al recargar notas: ", exception)
            }
    }

    // Método para eliminar una nota de Firestore usando el objeto Note
    fun deleteNote(note: Note) {
        firestore.collection("notes").document(note.id)
            .delete()
            .addOnSuccessListener {
                Log.d("NotesViewModel", "Nota eliminada exitosamente")
            }
            .addOnFailureListener { exception ->
                Log.e("NotesViewModel", "Error al eliminar la nota", exception)
            }
    }

    // Método para eliminar una nota de Firestore usando el ID
    fun deleteNoteById(noteId: String) {
        firestore.collection("notes").document(noteId)
            .delete()
            .addOnSuccessListener {
                Log.d("NotesViewModel", "Nota con ID $noteId eliminada exitosamente")
                // No es necesario llamar a `refreshNotes()` porque el listener actualiza el estado automáticamente
            }
            .addOnFailureListener { exception ->
                Log.e("NotesViewModel", "Error al eliminar la nota con ID $noteId", exception)
            }
    }

    // Métodos para gestionar la selección de notas
    fun hasSelectedNotes(): Boolean {
        return selectedNotes.isNotEmpty()
    }

    fun clearSelectedNotes() {
        selectedNotes.clear()
    }

    fun selectNoteById(noteId: String) {
        if (!selectedNotes.contains(noteId)) {
            selectedNotes.add(noteId)
        }
    }

    fun deselectNoteById(noteId: String) {
        selectedNotes.remove(noteId)
    }

    fun toggleNoteSelection(noteId: String) {
        if (selectedNotes.contains(noteId)) {
            deselectNoteById(noteId)
        } else {
            selectNoteById(noteId)
        }
    }

    fun getSelectedNotes(): List<String> {
        return selectedNotes.toList()
    }

    override fun onCleared() {
        super.onCleared() // Asegura que cualquier lógica de limpieza de ViewModel padre sea ejecutada
        listenerRegistration?.remove() // Removemos el listener si existe
        listenerRegistration = null // Limpiamos la referencia al listener
    }
}
