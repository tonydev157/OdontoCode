package com.tonymen.odontocode.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    // Filtrar procedimientos según los diferentes criterios
    fun filterProcedureList(
        query: String,
        procedureList: List<Procedure>,
        searchOption: String
    ): List<Procedure> {
        return procedureList.filter { procedure ->
            when (searchOption) {
                "Procedimiento" -> procedure.procedure.contains(query, ignoreCase = true)
                "CIE-10 Procedimiento" -> procedure.cie10procedure.contains(query, ignoreCase = true)
                "Diagnóstico" -> procedure.diagnosis.contains(query, ignoreCase = true)
                "CIE-10 Diagnóstico" -> procedure.cie10diagnosis.contains(query, ignoreCase = true)
                else -> false
            }
        }
    }

    // Componente de la interfaz que muestra las áreas y procedimientos
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchScreen(firestore: FirebaseFirestore, isAdmin: Boolean, onLogoutClick: () -> Unit) {
        var query by remember { mutableStateOf("") }
        var selectedProcedure by remember { mutableStateOf<Procedure?>(null) }
        var searchOption by remember { mutableStateOf("Procedimiento") }
        var areaList by remember { mutableStateOf<List<Area>>(emptyList()) }
        var odontopediatriaList by remember { mutableStateOf<List<Area>>(emptyList()) }
        var expandedAreas by remember { mutableStateOf<List<String>>(emptyList()) }
        var procedureMap by remember { mutableStateOf<Map<String, List<Procedure>>>(emptyMap()) }
        var searchResults by remember { mutableStateOf<List<Procedure>>(emptyList()) }
        var isDropdownExpanded by remember { mutableStateOf(false) }
        var allProcedureList by remember { mutableStateOf<List<Procedure>>(emptyList()) }
        var settingsMenuExpanded by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val context = LocalContext.current

        // Cargar todas las áreas al iniciar
        LaunchedEffect(Unit) {
            fetchAreas(firestore) { fetchedAreas ->
                areaList = fetchedAreas.sortedBy { it.name }
            }

            fetchAreasOdontopediatria(firestore) { fetchedOdontopediatria ->
                odontopediatriaList = fetchedOdontopediatria.sortedBy { it.name }
            }
            fetchProcedures(firestore) { fetchedProcedures ->
                allProcedureList = fetchedProcedures
                searchResults = filterProcedureList(query, allProcedureList, searchOption)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Search Procedure") },
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
                    Text("Search by: ", style = MaterialTheme.typography.titleMedium)

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
                        query = newQuery
                        searchResults = filterProcedureList(query, allProcedureList, searchOption)
                    },
                    onSearch = {
                        if (query.isNotBlank()) {
                            searchResults = filterProcedureList(query, allProcedureList, searchOption)
                            isDropdownExpanded = searchResults.isNotEmpty()
                            selectedProcedure = null
                        } else {
                            searchResults = emptyList()
                            isDropdownExpanded = false
                        }
                    },
                    onResultSelected = { procedure ->
                        selectedProcedure = procedure
                        isDropdownExpanded = false
                    },
                    isDropdownExpanded = isDropdownExpanded,
                    onDismissDropdown = { isDropdownExpanded = false },
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )

                // Mostrar el procedimiento seleccionado si hay uno
                selectedProcedure?.let { procedure ->
                    ProcedureDetail(mainViewModel, firestore, procedure) {
                        selectedProcedure = null
                    }
                }

                // Espacio entre el buscador y la lista de áreas
                Spacer(modifier = Modifier.height(16.dp))

                // Contenido desplazable para áreas y odontopediatría
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Mostrar áreas normales primero
                    items(areaList) { area ->
                        Column {
                            AreaRow(area = area, onAreaSelected = {
                                if (expandedAreas.contains(area.id)) {
                                    expandedAreas = expandedAreas - area.id
                                } else {
                                    expandedAreas = expandedAreas + area.id
                                    // Cargar procedimientos para el área seleccionada
                                    fetchProceduresByArea(firestore, area.id) { procedureList ->
                                        procedureMap = procedureMap.toMutableMap().apply {
                                            put(area.id, procedureList)
                                        }
                                    }
                                }
                            })

                            // Mostrar procedimientos relacionados al área
                            if (expandedAreas.contains(area.id)) {
                                procedureMap[area.id]?.forEach { procedureItem ->
                                    AnimatedProcedureRow(
                                        procedure = procedureItem,
                                        onProcedureSelected = { selectedProcedure = it },
                                        onMoreSelected = { selectedProcedure = procedureItem }
                                    )
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
                    items(odontopediatriaList) { area ->
                        Column {
                            AreaRow(area = area, onAreaSelected = {
                                if (expandedAreas.contains(area.id)) {
                                    expandedAreas = expandedAreas - area.id
                                } else {
                                    expandedAreas = expandedAreas + area.id
                                    // Cargar procedimientos para el área seleccionada
                                    fetchProceduresByArea(firestore, area.id) { procedureList ->
                                        procedureMap = procedureMap.toMutableMap().apply {
                                            put(area.id, procedureList)
                                        }
                                    }
                                }
                            })

                            // Mostrar procedimientos relacionados al área de odontopediatría
                            if (expandedAreas.contains(area.id)) {
                                procedureMap[area.id]?.forEach { procedureItem ->
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

    // Función para cargar procedimientos
    private fun fetchProcedures(firestore: FirebaseFirestore, onResult: (List<Procedure>) -> Unit) {
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
}




// Componente de barra de búsqueda con menú desplegable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    // Definir una altura máxima para el menú desplegable
    val maxHeight = 200.dp

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Enter procedure code or name") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        keyboardController?.show() // Mostrar el teclado si el campo está enfocado
                    }
                },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch()
                    keyboardController?.hide() // Esconder el teclado después de buscar
                }
            )
        )

        // Lista desplegable de resultados de búsqueda con altura limitada
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

// Componente para cada fila de área
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

// Componente para cada fila de procedimiento (con animación)
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

    // Verificar si el procedimiento es favorito al cargar la vista
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

    // Verificar el estado de favoritos constantemente
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
                // Obtener la lista actual de favoritos
                val favorite = documentSnapshot.toObject(Favorite::class.java)
                val updatedProcedureIds = favorite?.procedureIds?.toMutableList() ?: mutableListOf()

                if (isFavorite) {
                    // Si ya es favorito, lo eliminamos de la lista
                    updatedProcedureIds.remove(procedureId)
                } else {
                    // Si no es favorito, lo añadimos a la lista
                    updatedProcedureIds.add(procedureId)
                }

                // Actualizar la lista de IDs en Firestore
                favoritesRef.update("procedureIds", updatedProcedureIds)
                    .addOnSuccessListener {
                        onSuccess(!isFavorite) // Cambia el estado de favorito
                    }
                    .addOnFailureListener { e ->
                        Log.e("toggleFavorite", "Error al actualizar favoritos", e)
                    }
            } else {
                // Si el documento no existe, creamos uno nuevo con este procedimiento como favorito
                val newFavorite = Favorite(procedureIds = listOf(procedureId))
                favoritesRef.set(newFavorite)
                    .addOnSuccessListener {
                        onSuccess(true) // El procedimiento ahora es favorito
                    }
                    .addOnFailureListener { e ->
                        Log.e("toggleFavorite", "Error al añadir nuevo favorito", e)
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("toggleFavorite", "Error al acceder a favoritos", e)
        }
    }

    // Función para actualizar el estado de favoritos en Firestore
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

                // Icono de corazón para marcar/desmarcar como favorito
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
                    // Detalles adicionales del procedimiento
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

// Detalle del procedimiento
@Composable
fun ProcedureDetail(
    mainViewModel: MainViewModel,
    firestore: FirebaseFirestore,
    procedure: Procedure,
    onDismiss: () -> Unit
) {
    var isFavorite by remember { mutableStateOf(false) }
    var areaName by remember { mutableStateOf("Cargando...") }

    // Verificar si el procedimiento es favorito al cargar
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

    // Cargar el nombre del área
    LaunchedEffect(procedure.area) {
        firestore.collection("areas")
            .document(procedure.area)
            .get()
            .addOnSuccessListener { document ->
                val area = document.toObject(Area::class.java)
                areaName = area?.name ?: "Área no encontrada"
            }
    }

    // UI de la pantalla de detalle
    Surface(modifier = Modifier.padding(16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Procedure Code: ${procedure.cie10procedure}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Mostrar los detalles del procedimiento
            Text(text = "Name: ${procedure.procedure}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(text = "CIE-10 Procedure: ${procedure.cie10procedure}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Diagnosis: ${procedure.diagnosis}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "CIE-10 Diagnosis: ${procedure.cie10diagnosis}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Area: $areaName", style = MaterialTheme.typography.bodyMedium)

            // Mostrar el corazón para marcar como favorito
            IconButton(onClick = {
                mainViewModel.toggleFavorite(procedure.id)
                isFavorite = !isFavorite // Cambia el estado localmente
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

// Implementar funciones fetchAreas y fetchProceduresByArea
fun fetchAreas(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
    firestore.collection("areas")
        .get()
        .addOnSuccessListener { documents ->
            val areasList = documents.map { it.toObject(Area::class.java) }
            onResult(areasList) // Devolver la lista de áreas normales
        }
        .addOnFailureListener {
            onResult(emptyList()) // Si falla, devolver una lista vacía
        }
}

fun fetchProceduresByArea(
    firestore: FirebaseFirestore,
    areaId: String,
    onResult: (List<Procedure>) -> Unit
) {
    firestore.collection("procedures")
        .whereEqualTo("area", areaId) // Usamos el ID del área para buscar los procedimientos
        .get()
        .addOnSuccessListener { documents ->
            val procedureList = documents.map { it.toObject(Procedure::class.java) }
            onResult(procedureList)
        }
        .addOnFailureListener {
            onResult(emptyList()) // Devolvemos una lista vacía si ocurre un error
        }
}

fun fetchAreasOdontopediatria(firestore: FirebaseFirestore, onResult: (List<Area>) -> Unit) {
    firestore.collection("areasodontopediatria")
        .get()
        .addOnSuccessListener { documents ->
            val odontopediatriaList = documents.map { it.toObject(Area::class.java) }
            onResult(odontopediatriaList) // Devolver la lista de áreas de odontopediatría
        }
        .addOnFailureListener {
            onResult(emptyList()) // Si falla, devolver una lista vacía
        }
}
