package com.tonymen.odontocode.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.Diagnosis
import com.tonymen.odontocode.data.Favorite
import com.tonymen.odontocode.data.Procedure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    private val _favoriteProcedures = MutableStateFlow<List<Procedure>>(emptyList())
    val favoriteProcedures: StateFlow<List<Procedure>> = _favoriteProcedures

    private val _filteredFavorites = MutableStateFlow<List<Procedure>>(emptyList())
    val filteredFavorites: StateFlow<List<Procedure>> = _filteredFavorites

    // StateFlow para almacenar detalles del diagnóstico
    private val _diagnosisDetails = MutableStateFlow<Map<String, Diagnosis>>(emptyMap())
    val diagnosisDetails: StateFlow<Map<String, Diagnosis>> = _diagnosisDetails

    // StateFlow para mantener un conjunto de los IDs de procedimientos favoritos del usuario
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    init {
        fetchUserFavorites()
    }

    // Función para cargar los procedimientos favoritos del usuario
    private fun fetchUserFavorites() {
        userId?.let { userId ->
            val userFavoritesRef = firestore.collection("userFavorites").document(userId)

            userFavoritesRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val favorite = document.toObject(Favorite::class.java)
                        val procedureIds = favorite?.procedureIds ?: emptyList()

                        _favoriteIds.value = procedureIds.toSet()

                        if (procedureIds.isNotEmpty()) {
                            firestore.collection("procedures")
                                .whereIn("id", procedureIds)
                                .get()
                                .addOnSuccessListener { procedureDocs ->
                                    val procedures = procedureDocs.map { it.toObject(Procedure::class.java) }
                                    _favoriteProcedures.value = procedures
                                    _filteredFavorites.value = procedures
                                }
                                .addOnFailureListener { e ->
                                    Log.e("FavoritesViewModel", "Error al cargar procedimientos favoritos", e)
                                    _favoriteProcedures.value = emptyList()
                                    _filteredFavorites.value = emptyList()
                                }
                        } else {
                            Log.d("FavoritesViewModel", "No hay procedimientos favoritos para este usuario.")
                            _favoriteProcedures.value = emptyList()
                            _filteredFavorites.value = emptyList()
                        }
                    } else {
                        Log.d("FavoritesViewModel", "El documento de favoritos no existe para este usuario.")
                        _favoriteProcedures.value = emptyList()
                        _filteredFavorites.value = emptyList()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FavoritesViewModel", "Error al cargar favoritos", e)
                    _favoriteProcedures.value = emptyList()
                    _filteredFavorites.value = emptyList()
                }
        }
    }

    // Función para alternar el estado de favorito de un procedimiento
    fun toggleFavorite(procedureId: String) {
        userId?.let { userId ->
            val favoriteRef = firestore.collection("userFavorites").document(userId)

            favoriteRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentFavorites = document.toObject(Favorite::class.java)
                    val updatedFavorites = currentFavorites?.procedureIds?.toMutableList() ?: mutableListOf()

                    if (updatedFavorites.contains(procedureId)) {
                        updatedFavorites.remove(procedureId)
                    } else {
                        updatedFavorites.add(procedureId)
                    }

                    // Actualiza Firebase
                    favoriteRef.update("procedureIds", updatedFavorites)
                        .addOnSuccessListener {
                            Log.d("FavoritesViewModel", "Favorito actualizado")

                            // Actualizar el estado local sin necesidad de volver a cargar desde Firestore
                            if (_favoriteIds.value.contains(procedureId)) {
                                _favoriteIds.value = _favoriteIds.value - procedureId
                            } else {
                                _favoriteIds.value = _favoriteIds.value + procedureId
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FavoritesViewModel", "Error al actualizar favorito", e)
                        }
                }
            }
        }
    }

    // Función para buscar en los procedimientos favoritos
    fun searchFavorites(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _filteredFavorites.value = _favoriteProcedures.value
            } else {
                _filteredFavorites.value = _favoriteProcedures.value.filter {
                    it.cie10procedure.contains(query, ignoreCase = true) ||
                            it.procedure.contains(query, ignoreCase = true)
                }
            }
        }
    }

    // Función para cargar los detalles del diagnóstico
    fun loadDiagnosisDetails(diagnosisId: String) {
        firestore.collection("diagnosis")
            .document(diagnosisId)
            .get()
            .addOnSuccessListener { document ->
                val diagnosis = document.toObject(Diagnosis::class.java)
                if (diagnosis != null) {
                    // Actualizar los detalles del diagnóstico en el StateFlow
                    _diagnosisDetails.value = _diagnosisDetails.value + (diagnosisId to diagnosis)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FavoritesViewModel", "Error al cargar los detalles del diagnóstico", e)
            }
    }
}
