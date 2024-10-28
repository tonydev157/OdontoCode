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

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _expandedAreas = MutableStateFlow<String?>(null) // Allow only one area to be expanded at a time
    val expandedAreas: StateFlow<String?> = _expandedAreas

    private val _searchCriteria = MutableStateFlow("Área")
    val searchCriteria: StateFlow<String> = _searchCriteria

    private val _diagnoses = MutableStateFlow<List<Diagnosis>>(emptyList())
    val diagnoses: StateFlow<List<Diagnosis>> = _diagnoses

    private val _procedures = MutableStateFlow<List<Procedure>>(emptyList())
    val procedures: StateFlow<List<Procedure>> = _procedures

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _areaName = MutableStateFlow("Cargando...")
    val areaName: StateFlow<String> = _areaName

    private val _diagnosisName = MutableStateFlow("Cargando...")
    val diagnosisName: StateFlow<String> = _diagnosisName

    private val _diagnosisCIE10 = MutableStateFlow("Cargando...")
    val diagnosisCIE10: StateFlow<String> = _diagnosisCIE10

    private val _selectedProcedure = MutableStateFlow<Procedure?>(null)
    val selectedProcedure: StateFlow<Procedure?> = _selectedProcedure

    private val _backPressedOnce = MutableStateFlow(false)
    val backPressedOnce: StateFlow<Boolean> = _backPressedOnce

    private val _shouldClearFocus = MutableStateFlow(false)
    val shouldClearFocus: StateFlow<Boolean> = _shouldClearFocus
    private var currentJob: Job? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isEmptyResults = MutableStateFlow(false)
    val isEmptyResults: StateFlow<Boolean> = _isEmptyResults

    private val _expandedDiagnoses = MutableStateFlow<String?>(null) // Allow only one diagnosis to be expanded at a time
    val expandedDiagnoses: StateFlow<String?> = _expandedDiagnoses

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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkUserRole()
            loadAreas()
            loadOdontopediatriaAreas()
            loadFavorites()
            loadDiagnoses()
            loadProcedures()
            loadUserData()
        }
    }

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

    fun updateSearchCriteria(criteria: String) {
        _searchCriteria.value = criteria
    }

    fun searchProcedures(query: String, searchOption: String) {
        val normalizedQuery = normalizeString(query)
        currentJob?.cancel()  // Cancelar la búsqueda anterior si la hay

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val filteredProcedures = when (searchOption) {
                "Área" -> {
                    val matchingAreaIds = (_areas.value + _odontopediatriaAreas.value)
                        .filter { area -> normalizeString(area.name).contains(normalizedQuery, ignoreCase = true) }
                        .map { it.id }
                        .toSet()

                    _procedures.value.filter { matchingAreaIds.contains(it.area) }
                }

                "Diagnóstico" -> {
                    val matchingDiagnosisIds = (_diagnoses.value)
                        .filter { diagnosis -> normalizeString(diagnosis.name).contains(normalizedQuery, ignoreCase = true) }
                        .map { it.id }
                        .toSet()

                    _procedures.value.filter { matchingDiagnosisIds.contains(it.diagnosis) }
                }
                "CIE-10 Diagnóstico" -> {
                    val matchingDiagnosisIds = (_diagnoses.value)
                        .filter { diagnosis -> normalizeString(diagnosis.cie10diagnosis).contains(normalizedQuery, ignoreCase = true) }
                        .map { it.id }
                        .toSet()

                    _procedures.value.filter { matchingDiagnosisIds.contains(it.diagnosis) }
                }
                "Procedimiento" -> {
                    _procedures.value.filter {
                        normalizeString(it.procedure).contains(normalizedQuery, ignoreCase = true)
                    }
                }
                "CIE-10 Procedimiento" -> {
                    _procedures.value.filter {
                        normalizeString(it.cie10procedure).contains(normalizedQuery, ignoreCase = true)
                    }
                }
                else -> emptyList()
            }

            _isLoading.value = false
            _searchResults.value = filteredProcedures
            _isEmptyResults.value = filteredProcedures.isEmpty()
            requestClearFocus()  // Solicitar que se limpie el foco al finalizar la búsqueda
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()  // Cancelar cualquier trabajo en curso
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
    }

    fun toggleFavorite(procedureId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val favoritesRef = firestore.collection("userFavorites").document(userId)
                val documentSnapshot = favoritesRef.get().await()
                if (documentSnapshot.exists()) {
                    val favorite = documentSnapshot.toObject(Favorite::class.java)
                    val updatedProcedureIds = favorite?.procedureIds?.toMutableList() ?: mutableListOf()
                    if (updatedProcedureIds.contains(procedureId)) {
                        updatedProcedureIds.remove(procedureId)
                    } else {
                        updatedProcedureIds.add(procedureId)
                    }
                    favoritesRef.update("procedureIds", updatedProcedureIds).await()
                } else {
                    val newFavorite = Favorite(procedureIds = listOf(procedureId))
                    favoritesRef.set(newFavorite).await()
                }
                loadFavoriteState(procedureId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling favorite: ${e.message}")
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
                _isFavorite.value = favorite?.procedureIds?.contains(procedureId) == true
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading favorite state: \${e.message}")
            }
        }
    }

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
                val diagnosisDocument = firestore.collection("diagnosis").document(diagnosisId).get().await()
                val diagnosis = diagnosisDocument.toObject(Diagnosis::class.java)
                if (diagnosis != null) {
                    _diagnosisName.value = diagnosis.name
                    _diagnosisCIE10.value = diagnosis.cie10diagnosis
                } else {
                    val odontopediatriaDocument = firestore.collection("diagnosisodontopediatria")
                        .document(diagnosisId)
                        .get()
                        .await()
                    val odontopediatriaDiagnosis = odontopediatriaDocument.toObject(Diagnosis::class.java)
                    _diagnosisName.value = odontopediatriaDiagnosis?.name ?: "Diagnóstico no encontrado"
                    _diagnosisCIE10.value = odontopediatriaDiagnosis?.cie10diagnosis ?: "CIE-10 no encontrado"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading diagnosis data: \${e.message}")
            }
        }
    }

    private suspend fun loadAreas() {
        try {
            val documents = firestore.collection("areas").get().await()
            val areasList = documents.map { it.toObject(Area::class.java) }
            _areas.value = areasList
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading areas: \${e.message}")
        }
    }

    private suspend fun loadOdontopediatriaAreas() {
        try {
            val documents = firestore.collection("areasodontopediatria").get().await()
            val odontopediatriaList = documents.map { it.toObject(Area::class.java) }
            _odontopediatriaAreas.value = odontopediatriaList
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading odontopediatria areas: \${e.message}")
        }
    }

    private suspend fun loadFavorites() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val document = firestore.collection("userFavorites").document(userId).get().await()
            val favorite = document.toObject(Favorite::class.java)
            _favorites.value = favorite?.procedureIds ?: emptyList()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading favorites: \${e.message}")
        }
    }

    fun collapseAllAreas() {
        _expandedAreas.value = null
    }

    fun fetchProceduresByArea(areaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("procedures").whereEqualTo("area", areaId).get().await()
                val procedureList = documents.map { it.toObject(Procedure::class.java) }
                _proceduresByArea.update { it.toMutableMap().apply { put(areaId, procedureList) } }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading procedures for area: \$areaId")
            }
        }
    }

    fun toggleAreaExpansion(areaId: String) {
        _expandedAreas.value = if (_expandedAreas.value == areaId) null else areaId
    }

    private suspend fun loadDiagnoses() {
        val diagnosesList = mutableListOf<Diagnosis>()
        try {
            val documents = firestore.collection("diagnosis").get().await()
            diagnosesList.addAll(documents.map { it.toObject(Diagnosis::class.java) })
            val documents2 = firestore.collection("diagnosisodontopediatria").get().await()
            diagnosesList.addAll(documents2.map { it.toObject(Diagnosis::class.java) })
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
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading procedures: \${e.message}")
        }
    }

    fun fetchDiagnosesByArea(area: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("diagnosis").whereEqualTo("area", area).get().await()
                val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
                _diagnoses.value = diagnosisList
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading diagnoses for area: \${e.message}")
            }
        }
    }

    fun fetchDiagnosesByOdontopediatriaArea(area: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("diagnosisodontopediatria").whereEqualTo("area", area).get().await()
                val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
                _diagnoses.value = diagnosisList
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading odontopediatria diagnoses for area: \${e.message}")
            }
        }
    }

    fun fetchProceduresByDiagnosis(diagnosisId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documents = firestore.collection("procedures").whereEqualTo("diagnosis", diagnosisId).get().await()
                val procedureList = documents.map { it.toObject(Procedure::class.java) }
                _procedures.value = procedureList
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading procedures for diagnosis: \${e.message}")
            }
        }
    }

    fun toggleDiagnosisExpansion(diagnosisId: String) {
        _expandedDiagnoses.value = if (_expandedDiagnoses.value == diagnosisId) null else diagnosisId
    }

    fun collapseAllDiagnoses() {
        _expandedDiagnoses.value = null
    }

    fun saveSearchOption(context: Context, option: String) {
        val sharedPreferences = context.getSharedPreferences("OdontoCodePrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("searchOption", option).apply()
    }

    fun loadSearchOption(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("OdontoCodePrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("searchOption", "Área") ?: "Área"
    }

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

    fun selectProcedure(procedure: Procedure?) {
        _selectedProcedure.value = procedure
    }

    fun clearSelectedProcedure() {
        _selectedProcedure.value = null
    }

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
            Log.e("MainViewModel", "Error updating device ID: ${e.message}")
        }
    }



    fun requestClearFocus() {
        _shouldClearFocus.value = true
    }

    fun clearFocusHandled() {
        _shouldClearFocus.value = false
    }
}
