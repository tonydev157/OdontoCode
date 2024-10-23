package com.tonymen.odontocode.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.Area
import com.tonymen.odontocode.data.Favorite
import com.tonymen.odontocode.data.Procedure
import com.tonymen.odontocode.data.User
import com.tonymen.odontocode.data.UserType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _areas = MutableStateFlow<List<Area>>(emptyList())
    val areas: StateFlow<List<Area>> = _areas

    private val _odontopediatriaAreas = MutableStateFlow<List<Area>>(emptyList())
    val odontopediatriaAreas: StateFlow<List<Area>> = _odontopediatriaAreas

    private val _proceduresByArea = MutableStateFlow<Map<String, List<Procedure>>>(emptyMap())
    val proceduresByArea: StateFlow<Map<String, List<Procedure>>> = _proceduresByArea

    private val _searchResults = MutableStateFlow<List<Procedure>>(emptyList())
    val searchResults: StateFlow<List<Procedure>> = _searchResults

    private val _favorites = MutableStateFlow<List<String>>(emptyList()) // Lista de IDs de favoritos
    val favorites: StateFlow<List<String>> = _favorites

    private val _expandedAreas = MutableStateFlow<List<String>>(emptyList()) // Áreas expandidas
    val expandedAreas: StateFlow<List<String>> = _expandedAreas

    private val _searchCriteria = MutableStateFlow("procedure") // Criterio por defecto
    val searchCriteria: StateFlow<String> = _searchCriteria


    init {
        checkUserRole()
        loadAreas()
        loadOdontopediatriaAreas()
        loadFavorites()
    }

    // 1. Autenticación y determinación de roles
    private fun checkUserRole() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    _isAdmin.value = (user?.userType == UserType.ADMIN)
                }
        }
    }

    // 2. Búsqueda de procedimientos (por código o nombre)
    fun filterProcedureList(query: String, procedureList: List<Procedure>) {
        val results = procedureList.filter { procedure ->
            when (_searchCriteria.value) {
                "procedure" -> procedure.procedure.contains(query, ignoreCase = true)
                "cie10procedure" -> procedure.cie10procedure.contains(query, ignoreCase = true)
                "diagnosis" -> procedure.diagnosis.contains(query, ignoreCase = true)
                "cie10diagnosis" -> procedure.cie10diagnosis.contains(query, ignoreCase = true)
                else -> false
            }
        }
        _searchResults.value = results
    }
    fun updateSearchCriteria(criteria: String) {
        _searchCriteria.value = criteria
    }


    // 3. Manejo de favoritos (agregar o eliminar favoritos)
    fun toggleFavorite(procedureId: String) {
        val userId = auth.currentUser?.uid ?: return
        val favoritesRef = firestore.collection("userFavorites").document(userId)

        favoritesRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val favorite = documentSnapshot.toObject(Favorite::class.java)
                val updatedProcedureIds = favorite?.procedureIds?.toMutableList() ?: mutableListOf()

                if (updatedProcedureIds.contains(procedureId)) {
                    updatedProcedureIds.remove(procedureId) // Si ya es favorito, lo eliminamos
                } else {
                    updatedProcedureIds.add(procedureId) // Si no es favorito, lo añadimos
                }

                // Actualizar la lista de IDs en Firestore
                favoritesRef.update("procedureIds", updatedProcedureIds)
                    .addOnSuccessListener { loadFavorites() }
            } else {
                val newFavorite = Favorite(procedureIds = listOf(procedureId))
                favoritesRef.set(newFavorite)
                    .addOnSuccessListener { loadFavorites() }
            }
        }
    }

    // Cargar la lista de favoritos del usuario
    private fun loadFavorites() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("userFavorites").document(userId).get()
            .addOnSuccessListener { document ->
                val favorite = document.toObject(Favorite::class.java)
                _favorites.value = favorite?.procedureIds ?: emptyList()
            }
    }

    // 4. Carga de áreas normales y odontopediatría
    private fun loadAreas() {
        firestore.collection("areas")
            .get()
            .addOnSuccessListener { documents ->
                val areasList = documents.map { it.toObject(Area::class.java) }
                _areas.value = areasList
            }
            .addOnFailureListener { Log.e("MainViewModel", "Error loading areas") }
    }

    private fun loadOdontopediatriaAreas() {
        firestore.collection("areasodontopediatria")
            .get()
            .addOnSuccessListener { documents ->
                val odontopediatriaList = documents.map { it.toObject(Area::class.java) }
                _odontopediatriaAreas.value = odontopediatriaList
            }
            .addOnFailureListener { Log.e("MainViewModel", "Error loading odontopediatria areas") }
    }

    // 5. Carga de procedimientos por área
    fun fetchProceduresByArea(areaId: String) {
        firestore.collection("procedures")
            .whereEqualTo("area", areaId)
            .get()
            .addOnSuccessListener { documents ->
                val procedureList = documents.map { it.toObject(Procedure::class.java) }
                _proceduresByArea.value = _proceduresByArea.value.toMutableMap().apply {
                    put(areaId, procedureList)
                }
            }
            .addOnFailureListener { Log.e("MainViewModel", "Error loading procedures for area: $areaId") }
    }

    // 6. Agrupación de procedimientos por área
    fun toggleAreaExpansion(areaId: String) {
        val currentExpandedAreas = _expandedAreas.value.toMutableList()
        if (currentExpandedAreas.contains(areaId)) {
            currentExpandedAreas.remove(areaId) // Contrae el área si ya está expandida
        } else {
            currentExpandedAreas.add(areaId) // Expande el área si no está expandida
        }
        _expandedAreas.value = currentExpandedAreas
    }

    fun isAreaExpanded(areaId: String): Boolean {
        return _expandedAreas.value.contains(areaId)
    }
}
