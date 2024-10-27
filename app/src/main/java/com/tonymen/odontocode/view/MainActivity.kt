package com.tonymen.odontocode.view

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.*
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import com.tonymen.odontocode.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private lateinit var firestore: FirebaseFirestore
    private var isAdmin by mutableStateOf(false)
    private val mainViewModel: MainViewModel by viewModels()

    private var expandedAreas by mutableStateOf<List<String>>(emptyList()) // Control de áreas abiertas
    private var selectedProcedure by mutableStateOf<Procedure?>(null) // Control del procedimiento seleccionado
    private var backPressedOnce = false // Control para doble toque en "Atrás"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    isAdmin = user?.userType == UserType.ADMIN
                }
        }

        setContent {
            OdontoCodeTheme {
                SearchScreen(firestore, isAdmin) {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
        }
    }

    override fun onBackPressed() {
        when {
            selectedProcedure != null -> {
                // Si hay un procedimiento seleccionado, deseleccionarlo
                selectedProcedure = null
            }
            mainViewModel.expandedAreas.value.isNotEmpty() -> {
                // Colapsar todas las áreas utilizando el ViewModel
                mainViewModel.collapseAllAreas() // Nueva función que debes implementar en el ViewModel
            }
            backPressedOnce -> {
                // Si ya se presionó "Atrás" una vez, salir de la aplicación
                super.onBackPressed()
                return
            }
            else -> {
                // Primer toque en "Atrás" sin áreas ni procedimientos, mostrar aviso
                backPressedOnce = true
                showExitToast()
                resetBackPressedFlagWithDelay() // Reiniciar el flag después de un delay
            }
        }
    }

    // Mostrar el mensaje de salida
    private fun showExitToast() {
        Toast.makeText(this, "Presiona nuevamente para salir", Toast.LENGTH_SHORT).show()
    }

    // Reiniciar el flag después de 2 segundos
    private fun resetBackPressedFlagWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            backPressedOnce = false
        }, 2000)
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Salir de la aplicación")
            .setMessage("¿Estás seguro de que deseas salir?")
            .setPositiveButton("Sí") { _, _ ->
                finish() // Cerrar la actividad
            }
            .setNegativeButton("No", null)
            .show()
    }

    // Función para cargar el JSON de los assets
    private fun readJsonFromAssets(fileName: String): String? {
        return try {
            val inputStream = assets.open(fileName)
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al leer el archivo JSON: $fileName", e)
            null
        }
    }

    private fun loadAreasToFirestore() {
        val json = readJsonFromAssets("Area.json") ?: return
        val areas = Gson().fromJson(json, Array<Area>::class.java).toList()

        val areaIdMap = mutableMapOf<String, String>()

        areas.forEach { area ->
            firestore.collection("areas").add(area)
                .addOnSuccessListener { documentReference ->
                    areaIdMap[area.name] = documentReference.id
                    firestore.collection("areas").document(documentReference.id)
                        .update("id", documentReference.id)
                    Log.d("MainActivity", "Área añadida con ID: ${documentReference.id}")

                    if (areaIdMap.size == areas.size) {
                        loadProceduresToFirestore(areaIdMap)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Error al añadir área", e)
                }
        }
    }

    private fun loadProceduresToFirestore(areaIdMap: Map<String, String>) {
        val json = readJsonFromAssets("Procedure.json") ?: return
        val procedures = Gson().fromJson(json, Array<Procedure>::class.java).toList()

        procedures.forEach { procedure ->
            val areaId = areaIdMap[procedure.area]
            if (areaId != null) {
                val updatedProcedure = procedure.copy(area = areaId, id = "")
                firestore.collection("procedures").add(updatedProcedure)
                    .addOnSuccessListener { documentReference ->
                        firestore.collection("procedures").document(documentReference.id)
                            .update("id", documentReference.id)
                        Log.d("MainActivity", "Procedimiento añadido con ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Error al añadir procedimiento", e)
                    }
            } else {
                Log.e("MainActivity", "Área no encontrada para el procedimiento: ${procedure.procedure}")
            }
        }
    }

    // Función de filtrado con resolución de IDs
    fun filterProcedureList(
        query: String,
        procedureList: List<Procedure>,
        searchOption: String,
        diagnosisMap: Map<String, Diagnosis>, // Mapa de diagnósticos (ID a Diagnosis)
        areaMap: Map<String, Area> // Mapa de áreas (ID a Area)
    ): List<Procedure> {
        val normalizedQuery = normalizeString(query)

        return when (searchOption) {
            "Diagnóstico" -> {
                // Buscar IDs de diagnósticos que coinciden con el nombre
                val matchingDiagnosisIds = diagnosisMap.filter { (_, diagnosis) ->
                    normalizeString(diagnosis.name).contains(normalizedQuery, ignoreCase = true)
                }.keys.toSet() // Usamos un Set para facilitar la búsqueda

                // Filtrar procedimientos cuyos diagnósticos están en matchingDiagnosisIds
                procedureList.filter { matchingDiagnosisIds.contains(it.diagnosis) }
            }
            "CIE-10 Diagnóstico" -> {
                // Buscar IDs de diagnósticos que coinciden con el CIE-10
                val matchingDiagnosisIds = diagnosisMap.filter { (_, diagnosis) ->
                    normalizeString(diagnosis.cie10diagnosis).contains(normalizedQuery, ignoreCase = true)
                }.keys.toSet()

                // Filtrar procedimientos cuyos diagnósticos están en matchingDiagnosisIds
                procedureList.filter { matchingDiagnosisIds.contains(it.diagnosis) }
            }
            "Procedimiento" -> {
                // Filtrar procedimientos por nombre
                procedureList.filter {
                    normalizeString(it.procedure).contains(normalizedQuery, ignoreCase = true)
                }
            }
            "CIE-10 Procedimiento" -> {
                // Filtrar procedimientos por CIE-10
                procedureList.filter {
                    normalizeString(it.cie10procedure).contains(normalizedQuery, ignoreCase = true)
                }
            }
            "Área" -> {
                // Filtrar procedimientos por área
                val matchingAreaIds = areaMap.filter { (_, area) ->
                    normalizeString(area.name).contains(normalizedQuery, ignoreCase = true)
                }.keys.toSet() // Extraer IDs de áreas que coinciden

                procedureList.filter { matchingAreaIds.contains(it.area) } // Filtrar procedimientos que pertenecen a esas áreas
            }
            else -> emptyList() // Retornar lista vacía si el tipo de búsqueda no es válido
        }
    }

    // Función para normalizar cadenas, eliminando tildes y convirtiendo a minúsculas
    fun normalizeString(input: String): String {
        return input
            .lowercase() // Convierte a minúsculas
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u") // Para caracteres con diéresis
            .replace("ñ", "n")
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchScreen(firestore: FirebaseFirestore, isAdmin: Boolean, onLogoutClick: () -> Unit) {
        var query by remember { mutableStateOf("") }
        var searchOption by remember { mutableStateOf("Procedimiento") }
        var areaList by remember { mutableStateOf<List<Area>>(emptyList()) }
        var odontopediatriaList by remember { mutableStateOf<List<Area>>(emptyList()) }
        var procedureMap by remember { mutableStateOf<Map<String, List<Procedure>>>(emptyMap()) }
        var diagnosisMap by remember { mutableStateOf<Map<String, List<Diagnosis>>>(emptyMap()) }
        var areaMap by remember { mutableStateOf<Map<String, Area>>(emptyMap()) } // Mapa de áreas
        var diagnosisOdontopediatriaMap by remember { mutableStateOf<Map<String, List<Diagnosis>>>(emptyMap()) }
        var searchResults by remember { mutableStateOf<List<Procedure>>(emptyList()) }
        var isDropdownExpanded by remember { mutableStateOf(false) }
        var allProcedureList by remember { mutableStateOf<List<Procedure>>(emptyList()) }
        var settingsMenuExpanded by remember { mutableStateOf(false) }
        var expandedDiagnosis by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
        var selectedProcedure by remember { mutableStateOf<Procedure?>(null) }

        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            // Cargar las áreas y llenar el mapa
            fetchAreas(firestore) { fetchedAreas ->
                areaList = fetchedAreas.sortedBy { it.name }
                areaMap = fetchedAreas.associateBy { it.id }
            }

            // Cargar las áreas de odontopediatría
            fetchAreasOdontopediatria(firestore) { fetchedOdontopediatria ->
                odontopediatriaList = fetchedOdontopediatria.sortedBy { it.name }
            }

            // Cargar procedimientos
            fetchProcedures(firestore) { fetchedProcedures ->
                allProcedureList = fetchedProcedures
            }

            // Cargar diagnósticos y llenar el mapa
            fetchDiagnoses(firestore) { fetchedDiagnoses ->
                // Nota: Aquí aseguramos que diagnosisMap sea un Map<String, List<Diagnosis>>
                diagnosisMap = fetchedDiagnoses.groupBy { it.area }
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Buscador CIE-10") },
                    actions = {
                        IconButton(onClick = { settingsMenuExpanded = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = settingsMenuExpanded,
                            onDismissRequest = { settingsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    settingsMenuExpanded = false
                                    onLogoutClick()
                                },
                                text = { Text("Cerrar Sesión") }
                            )

                            if (isAdmin) {
                                DropdownMenuItem(
                                    onClick = {
                                        context.startActivity(Intent(context, AdminUsersActivity::class.java))
                                    },
                                    text = { Text("Administrar Usuarios") }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, NotesActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Note, contentDescription = "Notes")
                    }
                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = {
                        context.startActivity(Intent(context, FavoritesActivity::class.java))
                    }) {
                        Icon(painterResource(id = R.drawable.ic_favorite), contentDescription = "Favorites")
                    }

                    Text("OdontoCode", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Barra desplegable para seleccionar el tipo de búsqueda
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Buscar por: ", style = MaterialTheme.typography.titleMedium)

                    // Dropdown de selección
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(searchOption)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                searchOption = "Procedimiento"
                                expanded = false
                            }, text = { Text("Procedimiento") })
                            DropdownMenuItem(onClick = {
                                searchOption = "CIE-10 Procedimiento"
                                expanded = false
                            }, text = { Text("CIE-10 Procedimiento") })
                            DropdownMenuItem(onClick = {
                                searchOption = "Diagnóstico"
                                expanded = false
                            }, text = { Text("Diagnóstico") })
                            DropdownMenuItem(onClick = {
                                searchOption = "CIE-10 Diagnóstico"
                                expanded = false
                            }, text = { Text("CIE-10 Diagnóstico") })
                        }
                    }
                }

                // Buscador de procedimientos con lista desplegable
                SearchBarWithDropdown(
                    query = query,
                    searchResults = searchResults,
                    onQueryChange = { newQuery ->
                        query = newQuery // Solo actualiza el texto del query, sin hacer búsqueda aquí
                    },
                    onSearch = {
                        if (query.isNotBlank()) {
                            // Actualizar los resultados al hacer clic en buscar
                            searchResults = filterProcedureList(
                                query = query,
                                procedureList = allProcedureList,
                                searchOption = searchOption,
                                diagnosisMap = diagnosisMap.flatMap { it.value }.associateBy { it.id }, // Un mapa de diagnósticos
                                areaMap = areaMap
                            )
                            isDropdownExpanded = searchResults.isNotEmpty() // Mostrar el dropdown si hay resultados
                        } else {
                            searchResults = emptyList() // Si el query está vacío, no hay resultados
                            isDropdownExpanded = false
                        }
                    },
                    onResultSelected = { procedure ->
                        selectedProcedure = procedure // Asigna el procedimiento seleccionado
                        isDropdownExpanded = false // Cierra el dropdown
                    },
                    isDropdownExpanded = isDropdownExpanded, // Estado del dropdown
                    onDismissDropdown = { isDropdownExpanded = false }, // Cierra el dropdown
                    focusRequester = focusRequester, // Manejo del enfoque
                    keyboardController = keyboardController // Control del teclado
                )




                // Mostrar el procedimiento seleccionado si hay uno
                selectedProcedure?.let { procedure ->
                    ProcedureDetail(mainViewModel, firestore, procedure) {
                        selectedProcedure = null
                    }
                }

                // Espacio entre el buscador y la lista de áreas
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Mostrar áreas normales primero
                    items(areaList, key = { it.id }) { area ->
                        Column {
                            AreaRow(area = area, onAreaSelected = {
                                if (expandedAreas.contains(area.id)) {
                                    // Colapsamos el área y cerramos los diagnósticos
                                    expandedAreas = expandedAreas - area.id
                                    expandedDiagnosis = expandedDiagnosis.filterNot { diagnosisEntry ->
                                        diagnosisMap[area.id]?.any { it.id == diagnosisEntry.key } ?: false
                                    }
                                } else {
                                    // Expandimos el área y cargamos los diagnósticos
                                    expandedAreas = expandedAreas + area.id
                                    fetchDiagnosesByArea(firestore, area.id) { diagnosisList ->
                                        diagnosisMap = diagnosisMap.toMutableMap().apply {
                                            put(area.id, diagnosisList)
                                        }

                                        // Añadir diagnóstico "Sin agrupar" para procedimientos sin diagnóstico
                                        val ungroupedProcedures = allProcedureList.filter { it.diagnosis == "N/A" && it.area == area.id }
                                        if (ungroupedProcedures.isNotEmpty()) {
                                            val sinAgruparDiagnosis = Diagnosis(
                                                id = "ungrouped_${area.id}",
                                                name = "Sin agrupar",
                                                cie10diagnosis = "N/A",
                                                area = area.id
                                            )
                                            diagnosisMap = diagnosisMap.toMutableMap().apply {
                                                put(area.id, diagnosisMap[area.id]?.plus(sinAgruparDiagnosis) ?: listOf(sinAgruparDiagnosis))
                                            }
                                            procedureMap = procedureMap.toMutableMap().apply {
                                                put(sinAgruparDiagnosis.id, ungroupedProcedures)
                                            }

                                            // Asegurarse de que se expanda manualmente, sin que se cierre después
                                            expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                put(sinAgruparDiagnosis.id, true)  // Asegurar que se expande al cargar correctamente
                                            }
                                        }
                                    }
                                }
                            })

                            // Mostrar diagnósticos relacionados al área
                            if (expandedAreas.contains(area.id)) {
                                diagnosisMap[area.id]?.forEach { diagnosisItem ->
                                    // Mostrar cada diagnóstico con un botón de expansión
                                    DiagnosisRow(
                                        diagnosis = diagnosisItem,
                                        isExpanded = expandedDiagnosis[diagnosisItem.id] == true,
                                        onExpandClick = {
                                            if (expandedDiagnosis.containsKey(diagnosisItem.id)) {
                                                expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                    put(diagnosisItem.id, !(this[diagnosisItem.id] ?: false))
                                                }
                                            } else {
                                                expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                    put(diagnosisItem.id, true)
                                                }
                                                // Cargar procedimientos relacionados al diagnóstico
                                                fetchProceduresByDiagnosis(firestore, diagnosisItem.id) { procedureList ->
                                                    procedureMap = procedureMap.toMutableMap().apply {
                                                        put(diagnosisItem.id, procedureList)
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    // Mostrar procedimientos si el diagnóstico está expandido
                                    if (expandedDiagnosis[diagnosisItem.id] == true) {
                                        procedureMap[diagnosisItem.id]?.forEach { procedureItem ->
                                            AnimatedProcedureRow(
                                                procedure = procedureItem,
                                                onProcedureSelected = { selectedProcedure = it },
                                                onMoreSelected = { selectedProcedure = procedureItem }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Agregar el texto y el divisor para "Odontopediatría"
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
                        Text(
                            text = "Odontopediatría",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(16.dp)
                        )
                        Divider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
                    }

                    // Mostrar áreas de odontopediatría
                    items(odontopediatriaList, key = { it.id }) { area ->
                        Column {
                            AreaRow(area = area, onAreaSelected = {
                                if (expandedAreas.contains(area.id)) {
                                    expandedAreas = expandedAreas - area.id
                                    expandedDiagnosis = expandedDiagnosis.filterNot { diagnosisEntry ->
                                        diagnosisOdontopediatriaMap[area.id]?.any { it.id == diagnosisEntry.key } ?: false
                                    }
                                } else {
                                    expandedAreas = expandedAreas + area.id
                                    fetchDiagnosesByOdontopediatriaArea(firestore, area.id) { diagnosisList ->
                                        diagnosisOdontopediatriaMap = diagnosisOdontopediatriaMap.toMutableMap().apply {
                                            put(area.id, diagnosisList)
                                        }

                                        // Añadir diagnóstico "Sin agrupar" para procedimientos sin diagnóstico
                                        val ungroupedProcedures = allProcedureList.filter { it.diagnosis == "N/A" && it.area == area.id }
                                        if (ungroupedProcedures.isNotEmpty()) {
                                            val sinAgruparDiagnosis = Diagnosis(
                                                id = "ungrouped_${area.id}",
                                                name = "Sin agrupar",
                                                cie10diagnosis = "N/A",
                                                area = area.id
                                            )
                                            diagnosisOdontopediatriaMap = diagnosisOdontopediatriaMap.toMutableMap().apply {
                                                put(area.id, diagnosisOdontopediatriaMap[area.id]?.plus(sinAgruparDiagnosis) ?: listOf(sinAgruparDiagnosis))
                                            }
                                            procedureMap = procedureMap.toMutableMap().apply {
                                                put(sinAgruparDiagnosis.id, ungroupedProcedures)
                                            }

                                            // Asegurar que se expanda correctamente
                                            expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                put(sinAgruparDiagnosis.id, true)
                                            }
                                        }
                                    }
                                }
                            })

                            // Mostrar diagnósticos relacionados al área de odontopediatría
                            if (expandedAreas.contains(area.id)) {
                                diagnosisOdontopediatriaMap[area.id]?.forEach { diagnosisItem ->
                                    DiagnosisRow(
                                        diagnosis = diagnosisItem,
                                        isExpanded = expandedDiagnosis[diagnosisItem.id] == true,
                                        onExpandClick = {
                                            if (expandedDiagnosis.containsKey(diagnosisItem.id)) {
                                                expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                    put(diagnosisItem.id, !(this[diagnosisItem.id] ?: false))
                                                }
                                            } else {
                                                expandedDiagnosis = expandedDiagnosis.toMutableMap().apply {
                                                    put(diagnosisItem.id, true)
                                                }
                                                // Cargar procedimientos relacionados al diagnóstico
                                                fetchProceduresByDiagnosis(firestore, diagnosisItem.id) { procedureList ->
                                                    procedureMap = procedureMap.toMutableMap().apply {
                                                        put(diagnosisItem.id, procedureList)
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    // Mostrar procedimientos si el diagnóstico está expandido
                                    if (expandedDiagnosis[diagnosisItem.id] == true) {
                                        procedureMap[diagnosisItem.id]?.forEach { procedureItem ->
                                            AnimatedProcedureRow(
                                                procedure = procedureItem,
                                                onProcedureSelected = { selectedProcedure = it },
                                                onMoreSelected = { selectedProcedure = procedureItem }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Funciones para cargar datos
    fun fetchAreas(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
        firestore.collection("areas")
            .get()
            .addOnSuccessListener { documents ->
                val areaList = documents.map { it.toObject(Area::class.java) }
                onResult(areaList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun fetchAreasOdontopediatria(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
        firestore.collection("areasodontopediatria")
            .get()
            .addOnSuccessListener { documents ->
                val areaList = documents.map { it.toObject(Area::class.java) }
                onResult(areaList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // Función para cargar los diagnósticos desde Firestore
    fun fetchDiagnoses(
        firestore: FirebaseFirestore,
        onResult: (List<Diagnosis>) -> Unit
    ) {
        firestore.collection("diagnosis")
            .get()
            .addOnSuccessListener { documents ->
                val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
                onResult(diagnosisList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }



    fun fetchProcedures(firestore: FirebaseFirestore, onResult: (List<Procedure>) -> Unit) {
        firestore.collection("procedures")
            .get()
            .addOnSuccessListener { documents ->
                val procedureList = documents.map { it.toObject(Procedure::class.java) }
                onResult(procedureList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun fetchDiagnosesByArea(firestore: FirebaseFirestore, area: String, onResult: (List<Diagnosis>) -> Unit) {
        firestore.collection("diagnosis")
            .whereEqualTo("area", area)
            .get()
            .addOnSuccessListener { documents ->
                val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
                onResult(diagnosisList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun fetchDiagnosesByOdontopediatriaArea(firestore: FirebaseFirestore, area: String, onResult: (List<Diagnosis>) -> Unit) {
        firestore.collection("diagnosisodontopediatria")
            .whereEqualTo("area", area)
            .get()
            .addOnSuccessListener { documents ->
                val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
                onResult(diagnosisList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun fetchProceduresByDiagnosis(firestore: FirebaseFirestore, diagnosisId: String, onResult: (List<Procedure>) -> Unit) {
        firestore.collection("procedures")
            .whereEqualTo("diagnosis", diagnosisId)
            .get()
            .addOnSuccessListener { documents ->
                val procedureList = documents.map { it.toObject(Procedure::class.java) }
                onResult(procedureList)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarWithDropdown(
    query: String,
    searchResults: List<Procedure>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultSelected: (Procedure) -> Unit,
    isDropdownExpanded: Boolean,
    onDismissDropdown: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) {
    val maxHeight = 200.dp
    val isLightTheme = !isSystemInDarkTheme()

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Buscador") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        keyboardController?.show()
                    }
                },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch()
                    keyboardController?.hide()
                }
            )
        )

        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = {
                onDismissDropdown()
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            searchResults.forEach { result ->
                DropdownMenuItem(
                    onClick = {
                        onResultSelected(result)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isLightTheme) Color(0xFFE0F7FA) else Color(0xFF000B33)
                        ),
                    text = {
                        Column {
                            Text(result.cie10procedure, style = MaterialTheme.typography.bodyMedium)
                            Text(result.procedure, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AreaRow(area: Area, onAreaSelected: (Area) -> Unit) {
    Button(
        onClick = { onAreaSelected(area) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .shadow(0.dp, RoundedCornerShape(8.dp))
    ) {
        Text(area.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisRow(
    diagnosis: Diagnosis,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()

    val backgroundColor = if (isLightTheme) {
        Color(0xFFE1BEE7)
    } else {
        Color(0xFF4A148C)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = diagnosis.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = onExpandClick) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = if (isExpanded) "Ocultar procedimientos" else "Mostrar procedimientos"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedProcedureRow(
    procedure: Procedure,
    onProcedureSelected: (Procedure) -> Unit,
    onMoreSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }

    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val firestore = FirebaseFirestore.getInstance()

    LaunchedEffect(procedure.id) {
        if (userId != null) {
            firestore.collection("userFavorites")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    val favorite = document.toObject(Favorite::class.java)
                    isFavorite = favorite?.procedureIds?.contains(procedure.id) == true
                }
        }
    }

    LaunchedEffect(Unit) {
        if (userId != null) {
            firestore.collection("userFavorites")
                .document(userId)
                .addSnapshotListener { documentSnapshot, error ->
                    if (error != null) {
                        Log.e("Favorites", "Listen failed.", error)
                        return@addSnapshotListener
                    }
                    val favorite = documentSnapshot?.toObject(Favorite::class.java)
                    isFavorite = favorite?.procedureIds?.contains(procedure.id) == true
                }
        }
    }

    fun toggleFavorite(
        userId: String,
        procedureId: String,
        firestore: FirebaseFirestore,
        isFavorite: Boolean,
        onSuccess: (Boolean) -> Unit
    ) {
        val favoritesRef = firestore.collection("userFavorites").document(userId)

        favoritesRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val favorite = documentSnapshot.toObject(Favorite::class.java)
                val updatedProcedureIds = favorite?.procedureIds?.toMutableList() ?: mutableListOf()

                if (isFavorite) {
                    updatedProcedureIds.remove(procedureId)
                } else {
                    updatedProcedureIds.add(procedureId)
                }

                favoritesRef.update("procedureIds", updatedProcedureIds)
                    .addOnSuccessListener {
                        onSuccess(!isFavorite)
                    }
                    .addOnFailureListener { e ->
                        Log.e("toggleFavorite", "Error al actualizar favoritos", e)
                    }
            } else {
                val newFavorite = Favorite(procedureIds = listOf(procedureId))
                favoritesRef.set(newFavorite)
                    .addOnSuccessListener {
                        onSuccess(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("toggleFavorite", "Error al añadir nuevo favorito", e)
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("toggleFavorite", "Error al acceder a favoritos", e)
        }
    }

    fun toggleFavoriteStatus() {
        if (userId == null) return

        toggleFavorite(userId, procedure.id, firestore, isFavorite) { newFavoriteState ->
            isFavorite = newFavoriteState
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    procedure.cie10procedure,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    procedure.procedure,
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.bodyLarge
                )

                IconButton(onClick = { toggleFavoriteStatus() }) {
                    Icon(
                        painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(
                            id = R.drawable.ic_favorite_border
                        ),
                        contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onMoreSelected() }) {
                            Text("More")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcedureDetail(
    mainViewModel: MainViewModel,
    firestore: FirebaseFirestore,
    procedure: Procedure,
    onDismiss: () -> Unit
) {
    var isFavorite by remember { mutableStateOf(false) }
    var areaName by remember { mutableStateOf("Cargando...") }
    var diagnosisName by remember { mutableStateOf("Cargando...") }
    var diagnosisCIE10 by remember { mutableStateOf("Cargando...") }

    LaunchedEffect(procedure.id) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            firestore.collection("userFavorites")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    val favorite = document.toObject(Favorite::class.java)
                    isFavorite = favorite?.procedureIds?.contains(procedure.id) == true
                }
        }
    }

    LaunchedEffect(procedure.area) {
        firestore.collection("areas")
            .document(procedure.area)
            .get()
            .addOnSuccessListener { document ->
                val area = document.toObject(Area::class.java)
                if (area != null) {
                    areaName = area.name
                } else {
                    firestore.collection("areasodontopediatria")
                        .document(procedure.area)
                        .get()
                        .addOnSuccessListener { odontopediatriaDocument ->
                            val odontopediatriaArea = odontopediatriaDocument.toObject(Area::class.java)
                            areaName = odontopediatriaArea?.name ?: "Área no encontrada"
                        }
                }
            }
    }

    LaunchedEffect(procedure.diagnosis) {
        firestore.collection("diagnosis")
            .document(procedure.diagnosis)
            .get()
            .addOnSuccessListener { document ->
                val diagnosis = document.toObject(Diagnosis::class.java)
                if (diagnosis != null) {
                    diagnosisName = diagnosis.name
                    diagnosisCIE10 = diagnosis.cie10diagnosis
                } else {
                    firestore.collection("diagnosisodontopediatria")
                        .document(procedure.diagnosis)
                        .get()
                        .addOnSuccessListener { odontopediatriaDocument ->
                            val odontopediatriaDiagnosis = odontopediatriaDocument.toObject(Diagnosis::class.java)
                            diagnosisName = odontopediatriaDiagnosis?.name ?: "Diagnóstico no encontrado"
                            diagnosisCIE10 = odontopediatriaDiagnosis?.cie10diagnosis ?: "CIE-10 no encontrado"
                        }
                }
            }
    }

    Surface(modifier = Modifier.padding(16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Código de Procedimiento: ${procedure.cie10procedure}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Procedimiento: ${procedure.procedure}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(text = "Diagnóstico: $diagnosisName", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Codigo de Diagnóstico: $diagnosisCIE10", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Área: $areaName", style = MaterialTheme.typography.bodyMedium)

            IconButton(onClick = {
                mainViewModel.toggleFavorite(procedure.id)
                isFavorite = !isFavorite
            }) {
                Icon(
                    painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                    contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

fun fetchAreas(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
    firestore.collection("areas")
        .get()
        .addOnSuccessListener { documents ->
            val areasList = documents.map { it.toObject(Area::class.java) }
            onResult(areasList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}

fun fetchProceduresByArea(
    firestore: FirebaseFirestore,
    areaId: String,
    onResult: (List<Procedure>) -> Unit
) {
    firestore.collection("procedures")
        .whereEqualTo("area", areaId)
        .get()
        .addOnSuccessListener { documents ->
            val procedureList = documents.map { it.toObject(Procedure::class.java) }
            onResult(procedureList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}

fun fetchAreasOdontopediatria(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
    firestore.collection("areasodontopediatria")
        .get()
        .addOnSuccessListener { documents ->
            val odontopediatriaList = documents.map { it.toObject(Area::class.java) }
            onResult(odontopediatriaList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}
