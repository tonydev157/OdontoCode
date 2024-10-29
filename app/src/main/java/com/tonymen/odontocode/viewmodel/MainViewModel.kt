package com.tonymen.odontocode.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

class MainViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Admin State
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    // Full Lists for Search
    private val _fullAreas = MutableStateFlow<List<Area>>(emptyList())
    val fullAreas: StateFlow<List<Area>> = _fullAreas

    private val _fullDiagnoses = MutableStateFlow<List<Diagnosis>>(emptyList())
    val fullDiagnoses: StateFlow<List<Diagnosis>> = _fullDiagnoses

    private val _fullProcedures = MutableStateFlow<List<Procedure>>(emptyList())
    val fullProcedures: StateFlow<List<Procedure>> = _fullProcedures

    // Areas
    private val _areas = MutableStateFlow<List<Area>>(emptyList())
    val areas: StateFlow<List<Area>> = _areas

    private val _odontopediatriaAreas = MutableStateFlow<List<Area>>(emptyList())
    val odontopediatriaAreas: StateFlow<List<Area>> = _odontopediatriaAreas

    // Procedures
    private val _proceduresByArea = MutableStateFlow<Map<String, List<Procedure>>>(emptyMap())
    val proceduresByArea: StateFlow<Map<String, List<Procedure>>> = _proceduresByArea

    private val _procedures = MutableStateFlow<List<Procedure>>(emptyList())
    val procedures: StateFlow<List<Procedure>> = _procedures

    // Diagnoses
    private val _diagnoses = MutableStateFlow<List<Diagnosis>>(emptyList())
    val diagnoses: StateFlow<List<Diagnosis>> = _diagnoses

    // Search State
    private val _searchResults = MutableStateFlow<List<Procedure>>(emptyList())
    val searchResults: StateFlow<List<Procedure>> = _searchResults

    private val _searchCriteria = MutableStateFlow("Área")
    val searchCriteria: StateFlow<String> = _searchCriteria

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isEmptyResults = MutableStateFlow(false)
    val isEmptyResults: StateFlow<Boolean> = _isEmptyResults

    private val _shouldClearFocus = MutableStateFlow(false)
    val shouldClearFocus: StateFlow<Boolean> = _shouldClearFocus

    // Favorites State
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    // Area & Diagnosis Names
    private val _areaName = MutableStateFlow("Cargando...")
    val areaName: StateFlow<String> = _areaName

    private val _diagnosisName = MutableStateFlow("Cargando...")
    val diagnosisName: StateFlow<String> = _diagnosisName

    private val _diagnosisCIE10 = MutableStateFlow("Cargando...")
    val diagnosisCIE10: StateFlow<String> = _diagnosisCIE10

    // Selected Procedure
    private val _selectedProcedure = MutableStateFlow<Procedure?>(null)
    val selectedProcedure: StateFlow<Procedure?> = _selectedProcedure

    // Expanded State
    private val _expandedAreas = MutableStateFlow<Set<String>>(emptySet())
    val expandedAreas: StateFlow<Set<String>> = _expandedAreas

    private val _expandedDiagnoses = MutableStateFlow<Set<String>>(emptySet())
    val expandedDiagnoses: StateFlow<Set<String>> = _expandedDiagnoses

    // Back Pressed State
    private val _backPressedOnce = MutableStateFlow(false)
    val backPressedOnce: StateFlow<Boolean> = _backPressedOnce

    private var searchJob: Job? = null

    private val _expandedArea = MutableStateFlow<String?>(null)
    val expandedArea: StateFlow<String?> = _expandedArea

    private val _expandedDiagnosis = MutableStateFlow<String?>(null)
    val expandedDiagnosis: StateFlow<String?> = _expandedDiagnosis

    private val _favoriteProcedures = MutableStateFlow<Set<String>>(emptySet())
    val favoriteProcedures: StateFlow<Set<String>> = _favoriteProcedures
    private val _favoriteProcedureStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val favoriteProcedureStates: StateFlow<Map<String, Boolean>> = _favoriteProcedureStates

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadInitialData()
            listenToFavoritesChanges()
        }
    }

    private suspend fun loadInitialData() {
        checkUserRole()
        loadAreas()
        loadOdontopediatriaAreas()
        loadFavorites()
        loadDiagnoses()
        loadProcedures()
        loadUserData()
    }

    // User Role
    private suspend fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val document = firestore.collection("users").document(userId).get().await()
            val user = document.toObject(User::class.java)
            _isAdmin.value = (user?.userType == UserType.ADMIN)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error checking user role: \${e.message}")
        }
    }

    // Search Procedures with Debounce
    fun searchProceduresWithDebounce(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500) // Ajusta el valor según prefieras
            searchProcedures(query)
        }
    }

    fun updateSearchCriteria(criteria: String) {
        _searchCriteria.value = criteria
    }

    fun searchProcedures(query: String) {
        val normalizedQuery = normalizeString(query)
        val searchOption = _searchCriteria.value
        searchJob?.cancel()  // Cancelar la búsqueda anterior si la hay

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Asegurarse de que todos los diagnósticos estén cargados antes de buscar
                ensureFullDiagnosesLoaded()

                // Restablecer estados antes de iniciar la búsqueda
                _isLoading.value = true
                _isSearching.value = true
                _searchResults.value = emptyList()  // Limpiar los resultados de búsquedas anteriores
                _isEmptyResults.value = false      // Asumir que habrá resultados hasta que se pruebe lo contrario

                // Realizar el filtrado de los procedimientos
                val filteredProcedures = filterProcedures(normalizedQuery, searchOption)

                // Obtener los nombres de los diagnósticos usando los IDs de los procedimientos filtrados
                val updatedProcedures = filteredProcedures.map { procedure ->
                    val diagnosis = _fullDiagnoses.value.find { it.id == procedure.diagnosis }
                    procedure.copy(
                        cie10procedure = "${procedure.cie10procedure} / ${diagnosis?.cie10diagnosis ?: "N/A"}",
                        diagnosis = diagnosis?.name ?: "Diagnóstico no encontrado"
                    )
                }

                // Actualizar el estado después de la búsqueda
                _isLoading.value = false
                _isSearching.value = false
                _searchResults.value = updatedProcedures
                _isEmptyResults.value = updatedProcedures.isEmpty()

                // Solicitar limpiar el foco al finalizar la búsqueda
                requestClearFocus()

            } catch (e: Exception) {
                // Manejar errores: restablecer el estado y loggear el error
                _isLoading.value = false
                _isSearching.value = false
                _searchResults.value = emptyList()  // Limpiar resultados en caso de error
                _isEmptyResults.value = true        // Mostrar que no hubo resultados

                Log.e("MainViewModel", "Error en la búsqueda: ${e.message}")
            }
        }
    }

    private suspend fun ensureFullDiagnosesLoaded() {
        if (_fullDiagnoses.value.isEmpty()) {
            loadDiagnoses()
        }
    }


    private fun filterProcedures(normalizedQuery: String, searchOption: String): List<Procedure> {
        return when (searchOption) {
            "Área" -> {
                val matchingAreaIds = (_fullAreas.value)
                    .filter { area -> normalizeString(area.name).contains(normalizedQuery, ignoreCase = true) }
                    .map { it.id }
                    .toSet()

                _fullProcedures.value.filter { matchingAreaIds.contains(it.area) }
            }
            "Diagnóstico" -> {
                val matchingDiagnosisIds = _fullDiagnoses.value
                    .filter { diagnosis -> normalizeString(diagnosis.name).contains(normalizedQuery, ignoreCase = true) }
                    .map { it.id }
                    .toSet()

                _fullProcedures.value.filter { matchingDiagnosisIds.contains(it.diagnosis) }
            }
            "CIE-10 Diagnóstico" -> {
                val matchingDiagnosisIds = _fullDiagnoses.value
                    .filter { diagnosis -> normalizeString(diagnosis.cie10diagnosis).contains(normalizedQuery, ignoreCase = true) }
                    .map { it.id }
                    .toSet()

                _fullProcedures.value.filter { matchingDiagnosisIds.contains(it.diagnosis) }
            }
            "Procedimiento" -> {
                _fullProcedures.value.filter {
                    normalizeString(it.procedure).contains(normalizedQuery, ignoreCase = true)
                }
            }
            "CIE-10 Procedimiento" -> {
                _fullProcedures.value.filter {
                    normalizeString(it.cie10procedure).contains(normalizedQuery, ignoreCase = true)
                }
            }
            else -> emptyList()
        }
    }

    // Normalize String
    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    // Back Press Handling
    fun setBackPressedOnce(value: Boolean) {
        _backPressedOnce.value = value
    }

    fun resetBackPressedOnceWithDelay() {
        viewModelScope.launch {
            delay(2000)
            resetBackPressedOnce()
        }
    }

    fun resetBackPressedOnce() {
        setBackPressedOnce(false)
    }

    private fun listenToFavoritesChanges() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("userFavorites")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("MainViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val favorite = snapshot.toObject(Favorite::class.java)
                    val procedureIds = favorite?.procedureIds?.toSet() ?: emptySet()
                    _favoriteProcedures.value = procedureIds

                    // Actualizamos el estado de cada procedimiento
                    _favoriteProcedureStates.update {
                        it.toMutableMap().apply {
                            procedureIds.forEach { id ->
                                this[id] = true
                            }
                        }
                    }
                }
            }
    }

    // Actualizar el estado de favorito en Firestore
    fun toggleFavorite(procedureId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val favoritesRef = firestore.collection("userFavorites").document(userId)
                val documentSnapshot = favoritesRef.get().await()
                val updatedProcedureIds = documentSnapshot.toObject(Favorite::class.java)?.procedureIds?.toMutableSet() ?: mutableSetOf()

                if (updatedProcedureIds.contains(procedureId)) {
                    updatedProcedureIds.remove(procedureId)
                } else {
                    updatedProcedureIds.add(procedureId)
                }

                favoritesRef.update("procedureIds", updatedProcedureIds.toList()).await()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling favorite: \${e.message}")
            }
        }
    }

    fun loadFavoriteState(procedureId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentSnapshot = firestore.collection("userFavorites")
                    .document(userId)
                    .get()
                    .await()
                val favorite = documentSnapshot.toObject(Favorite::class.java)
                _favoriteProcedureStates.update {
                    it.toMutableMap().apply {
                        this[procedureId] = favorite?.procedureIds?.contains(procedureId) == true
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading favorite state: \${e.message}")
            }
        }
    }

    private suspend fun loadFavorites() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val document = firestore.collection("userFavorites").document(userId).get().await()
            val favorite = document.toObject(Favorite::class.java)
            _favoriteProcedures.value = favorite?.procedureIds?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading favorites: \${e.message}")
        }
    }

    // Area & Diagnosis Loading
    fun loadAreaName(areaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val areaDocument = firestore.collection("areas").document(areaId).get().await()
                val area = areaDocument.toObject(Area::class.java)
                _areaName.value = area?.name ?: run {
                    val odontopediatriaDocument = firestore.collection("areasodontopediatria")
                        .document(areaId)
                        .get()
                        .await()
                    val odontopediatriaArea = odontopediatriaDocument.toObject(Area::class.java)
                    odontopediatriaArea?.name ?: "Área no encontrada"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading area name: \${e.message}")
            }
        }
    }

    fun loadDiagnosisData(diagnosisId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Verificar si ya tenemos el diagnóstico cargado correctamente
                if (_diagnosisName.value != "Cargando..." && _diagnosisName.value != "Diagnóstico no encontrado") {
                    Log.d("MainViewModel", "Diagnosis data already loaded. Skipping Firestore call.")
                    return@launch
                }

                // Verificar si el diagnóstico ya está en la lista completa
                val diagnosis = _fullDiagnoses.value.find { it.id == diagnosisId }

                if (diagnosis != null) {
                    Log.d("MainViewModel", "Diagnosis found locally: ${diagnosis.name}")
                    _diagnosisName.value = diagnosis.name
                    _diagnosisCIE10.value = diagnosis.cie10diagnosis
                } else {
                    Log.d("MainViewModel", "Diagnosis not found locally, fetching from Firestore")

                    // Buscar en Firestore
                    val diagnosisFromFirestore = firestore.collection("diagnosis")
                        .document(diagnosisId).get().await().toObject(Diagnosis::class.java)
                        ?: firestore.collection("diagnosisodontopediatria")
                            .document(diagnosisId).get().await().toObject(Diagnosis::class.java)

                    if (diagnosisFromFirestore != null) {
                        Log.d("MainViewModel", "Diagnosis loaded from Firestore: ${diagnosisFromFirestore.name}")
                        _diagnosisName.value = diagnosisFromFirestore.name
                        _diagnosisCIE10.value = diagnosisFromFirestore.cie10diagnosis
                        // Añadir a la lista local para futuras consultas
                        _fullDiagnoses.update { it + diagnosisFromFirestore }
                    } else {
                        Log.e("MainViewModel", "Diagnosis not found in Firestore for id: $diagnosisId")
                        _diagnosisName.value = "Diagnóstico no encontrado"
                        _diagnosisCIE10.value = "CIE-10 no encontrado"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading diagnosis data: ${e.message}")
                _diagnosisName.value = "Error al cargar el diagnóstico"
                _diagnosisCIE10.value = "Error al cargar el CIE-10"
            }
        }
    }



    // Data Loading
    private suspend fun loadAreas() {
        try {
            val documents = firestore.collection("areas").get().await()
            val areasList = documents.map { it.toObject(Area::class.java) }
            _areas.value = areasList
            _fullAreas.value = areasList
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading areas: \${e.message}")
        }
    }

    private suspend fun loadOdontopediatriaAreas() {
        try {
            val documents = firestore.collection("areasodontopediatria").get().await()
            val odontopediatriaList = documents.map { it.toObject(Area::class.java) }
            _odontopediatriaAreas.value = odontopediatriaList
            _fullAreas.update { it + odontopediatriaList }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading odontopediatria areas: \${e.message}")
        }
    }

    private suspend fun loadDiagnoses() {
        val diagnosesList = mutableListOf<Diagnosis>()
        try {
            // Cargar todos los diagnósticos de Firestore y actualizar la lista completa
            val documents = firestore.collection("diagnosis").get().await()
            diagnosesList.addAll(documents.map { it.toObject(Diagnosis::class.java) })
            val documents2 = firestore.collection("diagnosisodontopediatria").get().await()
            diagnosesList.addAll(documents2.map { it.toObject(Diagnosis::class.java) })

            // Actualizar tanto la lista completa de diagnósticos como la temporal
            _fullDiagnoses.value = diagnosesList
            _diagnoses.value = diagnosesList
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading diagnoses: \${e.message}")
        }
    }

    private suspend fun loadProcedures() {
        try {
            val documents = firestore.collection("procedures").get().await()
            val procedureList = documents.map { it.toObject(Procedure::class.java) }
            _procedures.value = procedureList
            _fullProcedures.value = procedureList
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading procedures: \${e.message}")
        }
    }

    // Expandable State
    fun toggleAreaExpansion(areaId: String) {
        _expandedArea.update { currentExpanded ->
            if (currentExpanded == areaId) {
                null // Si el área ya está expandida, la colapsamos
            } else {
                areaId // Expandimos la nueva área
            }
        }
        collapseAllDiagnoses() // Colapsar todos los diagnósticos cuando se cambia el área
    }

    // Toggle Diagnosis Expansion
    fun toggleDiagnosisExpansion(diagnosisId: String) {
        _expandedDiagnosis.update { currentExpanded ->
            if (currentExpanded == diagnosisId) {
                null // Si el diagnóstico ya está expandido, lo colapsamos
            } else {
                diagnosisId // Expandimos el nuevo diagnóstico
            }
        }
    }

    // Selected Procedure
    fun selectProcedure(procedure: Procedure?) {
        if (procedure != null) {
            // Verificar que el procedimiento tenga un área y diagnóstico válidos
            if (procedure.area.isNotEmpty() && procedure.diagnosis.isNotEmpty()) {
                // Verificar si el procedimiento completo ya está en la lista completa
                val completeProcedure = _fullProcedures.value.find { it.id == procedure.id }
                if (completeProcedure != null) {
                    _selectedProcedure.value = completeProcedure

                    // Cargar el nombre del área y diagnóstico
                    loadAreaName(completeProcedure.area)
                    loadDiagnosisData(completeProcedure.diagnosis)
                } else {
                    // Si no está en la lista completa, recargar desde Firestore
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val documentSnapshot = firestore.collection("procedures")
                                .document(procedure.id)
                                .get()
                                .await()
                            val fullProcedure = documentSnapshot.toObject(Procedure::class.java)
                            if (fullProcedure != null) {
                                _selectedProcedure.value = fullProcedure

                                // Cargar el nombre del área y diagnóstico
                                loadAreaName(fullProcedure.area)
                                loadDiagnosisData(fullProcedure.diagnosis)
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Error loading full procedure: ${e.message}")
                        }
                    }
                }
            } else {
                Log.e("MainViewModel", "Procedure has invalid area or diagnosis.")
                _selectedProcedure.value = null
            }
        } else {
            _selectedProcedure.value = null
        }
    }



    fun clearSelectedProcedure() {
        _selectedProcedure.value = null
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // User Sign Out
    fun signOutUser(onSignOutComplete: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(userId)
                    .update("activeSession", false)
                    .await()
                auth.signOut()
                onSignOutComplete()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error signing out: \${e.message}")
            }
        }
    }

    // Device ID Handling
    private fun getCurrentDeviceId(context: Context): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    private suspend fun updateDeviceId(userId: String, context: Context) {
        try {
            val deviceId = getCurrentDeviceId(context)
            firestore.collection("users").document(userId)
                .update("activeDeviceId", deviceId)
                .await()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error updating device ID: \${e.message}")
        }
    }

    // Clear Focus
    fun requestClearFocus() {
        _shouldClearFocus.value = true
    }

    fun clearFocusHandled() {
        _shouldClearFocus.value = false
    }

    // Expandable State - Collapse All Areas
    fun collapseAllAreas() {
        _expandedAreas.value = emptySet()
    }

    // Expandable State - Collapse All Diagnoses
    fun collapseAllDiagnoses() {
        _expandedDiagnoses.value = emptySet()
    }

    // Load User Data
    private suspend fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val document = firestore.collection("users").document(userId).get().await()
            val user = document.toObject(User::class.java)
            _isAdmin.value = (user?.userType == UserType.ADMIN)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading user data: \${e.message}")
        }
    }

    // Save and Load Search Option
    fun saveSearchOption(context: Context, option: String) {
        val sharedPreferences = context.getSharedPreferences("OdontoCodePrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("searchOption", option).apply()
    }

    fun loadSearchOption(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("OdontoCodePrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("searchOption", "Área") ?: "Área"
    }

    fun fetchDiagnosesByArea(areaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("diagnosis")
                    .whereEqualTo("area", areaId)
                    .get()
                    .await()
                val diagnosesList = documents.map { it.toObject(Diagnosis::class.java) }
                _diagnoses.value = diagnosesList

                // Añadir los diagnósticos cargados a la lista completa
                _fullDiagnoses.update { it + diagnosesList }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching diagnoses by area: \${e.message}")
            }
        }
    }

    fun fetchProceduresByDiagnosis(diagnosisId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("procedures")
                    .whereEqualTo("diagnosis", diagnosisId)
                    .get()
                    .await()
                val proceduresList = documents.map { it.toObject(Procedure::class.java) }
                _procedures.value = proceduresList

                // Añadir los procedimientos cargados a la lista completa
                _fullProcedures.update { it + proceduresList }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching procedures by diagnosis: \${e.message}")
            }
        }
    }

    fun fetchDiagnosesByOdontopediatriaArea(areaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("diagnosisodontopediatria")
                    .whereEqualTo("area", areaId)
                    .get()
                    .await()
                val diagnosesList = documents.map { it.toObject(Diagnosis::class.java) }
                _diagnoses.value = diagnosesList

                // Añadir los diagnósticos cargados a la lista completa
                _fullDiagnoses.update { it + diagnosesList }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching diagnoses by odontopediatria area: \${e.message}")
            }
        }
    }
}
