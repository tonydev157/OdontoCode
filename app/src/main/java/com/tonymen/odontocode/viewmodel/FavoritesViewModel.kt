package com.tonymen.odontocode.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tonymen.odontocode.data.Diagnosis
import com.tonymen.odontocode.data.Favorite
import com.tonymen.odontocode.data.Procedure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    // Caché para almacenar nombres de áreas
    private val areaNameCache = mutableMapOf<String, String>()

    // StateFlow para mantener un conjunto de los IDs de procedimientos favoritos del usuario
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    // StateFlow para mantener el estado temporal del corazón
    private val _tempFavoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val tempFavoriteIds: StateFlow<Set<String>> = _tempFavoriteIds

    private var favoriteListener: ListenerRegistration? = null

    init {
        fetchUserFavoritesRealtime()
    }

    override fun onCleared() {
        super.onCleared()
        favoriteListener?.remove()
    }

    // Función para cargar los procedimientos favoritos del usuario en tiempo real
    private fun fetchUserFavoritesRealtime() {
        userId?.let { userId ->
            val userFavoritesRef = firestore.collection("userFavorites").document(userId)

            favoriteListener = userFavoritesRef.addSnapshotListener { document, e ->
                if (e != null) {
                    Log.e("FavoritesViewModel", "Error al escuchar los favoritos", e)
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val favorite = document.toObject(Favorite::class.java)
                    val procedureIds = favorite?.procedureIds ?: emptyList()

                    _favoriteIds.value = procedureIds.toSet()
                    _tempFavoriteIds.value = procedureIds.toSet()

                    if (procedureIds.isNotEmpty()) {
                        firestore.collection("procedures")
                            .whereIn("id", procedureIds)
                            .get()
                            .addOnSuccessListener { procedureDocs ->
                                val procedures = procedureDocs.map { it.toObject(Procedure::class.java) }
                                _favoriteProcedures.value = procedures
                                _filteredFavorites.value = procedures

                                // Cargar detalles de diagnósticos y áreas
                                procedures.forEach { procedure ->
                                    loadDiagnosisDetails(procedure.diagnosis)
                                }
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
        }
    }

    // Función para alternar el estado de favorito de un procedimiento de forma temporal
    fun toggleFavorite(procedureId: String) {
        // Actualizar el estado local del ícono inmediatamente
        val isCurrentlyFavorite = _tempFavoriteIds.value.contains(procedureId)
        _tempFavoriteIds.value = if (isCurrentlyFavorite) {
            _tempFavoriteIds.value - procedureId
        } else {
            _tempFavoriteIds.value + procedureId
        }
    }

    // Función para confirmar los cambios de favoritos y guardarlos en Firebase
    fun confirmFavoritesChanges() {
        userId?.let { userId ->
            val favoriteRef = firestore.collection("userFavorites").document(userId)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val originalFavorites = _favoriteIds.value
                    val updatedFavorites = _tempFavoriteIds.value

                    val toAdd = updatedFavorites - originalFavorites
                    val toRemove = originalFavorites - updatedFavorites

                    if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                        val newFavoritesList = updatedFavorites.toList()
                        favoriteRef.update("procedureIds", newFavoritesList).await()
                        Log.d("FavoritesViewModel", "Favoritos actualizados correctamente")
                    } else {
                        Log.d("FavoritesViewModel", "No hay cambios en los favoritos para guardar")
                    }

                } catch (e: Exception) {
                    Log.e("FavoritesViewModel", "Error al actualizar favoritos en Firestore", e)
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

    // Función para cargar los detalles del diagnóstico (de ambas colecciones) y almacenar en caché
    fun loadDiagnosisDetails(diagnosisId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val diagnosis = firestore.collection("diagnosis").document(diagnosisId).get().await().toObject(Diagnosis::class.java)
                    ?: firestore.collection("diagnosisodontopediatria").document(diagnosisId).get().await().toObject(Diagnosis::class.java)

                diagnosis?.let {
                    _diagnosisDetails.value = _diagnosisDetails.value + (diagnosisId to it)
                }
            } catch (e: Exception) {
                Log.e("FavoritesViewModel", "Error al cargar los detalles del diagnóstico", e)
            }
        }
    }


    // Función para cargar los detalles del área (de ambas colecciones) y almacenar en caché
// Función para cargar los detalles del área (de ambas colecciones)
    fun loadAreaDetails(areaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val area = firestore.collection("areas").document(areaId).get().await().getString("name")
                    ?: firestore.collection("areasodontopediatria").document(areaId).get().await().getString("name")

                area?.let {
                    cacheAreaName(areaId, it)
                }
            } catch (e: Exception) {
                Log.e("FavoritesViewModel", "Error al cargar los detalles del área", e)
            }
        }
    }



    // Función para obtener el nombre del área desde el caché
    fun getCachedAreaName(areaId: String): String? {
        return areaNameCache[areaId]
    }

    // Función para almacenar el nombre del área en el caché
     fun cacheAreaName(areaId: String, areaName: String) {
        areaNameCache[areaId] = areaName
    }
}
