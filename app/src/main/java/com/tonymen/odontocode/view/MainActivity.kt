package com.tonymen.odontocode.view

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.*
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import com.tonymen.odontocode.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si la sesión está activa
        val userId = auth.currentUser?.uid
        if (userId != null) {
            verificarSesionActiva(userId)
        } else {
            // Si no hay usuario, redirigir al login
            redirigirAlLogin()
        }

        // Cargar opción de búsqueda guardada desde el ViewModel
        val savedSearchOption = mainViewModel.loadSearchOption(this)
        mainViewModel.updateSearchCriteria(savedSearchOption)

        setContent {
            OdontoCodeTheme {
                val isAdmin by mainViewModel.isAdmin.collectAsStateWithLifecycle()
                SearchScreen(mainViewModel = mainViewModel, isAdmin = isAdmin) {
                    userId?.let {
                        cerrarSesionDesdeSettings(it)
                    }
                }
            }
        }
    }

    private fun verificarSesionActiva(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    val currentDeviceId = getCurrentDeviceId()
                    if (user.activeDeviceId.isNullOrEmpty()) {
                        // Si no hay un activeDeviceId configurado, actualizar con el dispositivo actual
                        firestore.collection("users").document(userId)
                            .update("activeDeviceId", currentDeviceId)
                            .addOnSuccessListener {
                                // Continuar con la sesión después de actualizar el activeDeviceId
                                setMainContent()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al actualizar el dispositivo activo", Toast.LENGTH_LONG).show()
                                cerrarSesionYRedirigir()
                            }
                    } else if (user.activeDeviceId != currentDeviceId) {
                        // Si el activeDeviceId no coincide con el dispositivo actual, cerrar la sesión
                        showInvalidSessionDialog()
                    } else {
                        // El dispositivo es correcto, establecer el contenido
                        setMainContent()
                    }
                } else {
                    // Si el usuario es null, cerrar la sesión y redirigir al login
                    cerrarSesionYRedirigir()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al verificar la sesión", Toast.LENGTH_LONG).show()
                cerrarSesionYRedirigir()
            }
    }

    private fun showInvalidSessionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sesión inválida")
            .setMessage("La sesión está activa en otro dispositivo. Se cerrará la sesión actual.")
            .setPositiveButton("Aceptar") { _, _ ->
                cerrarSesionYRedirigir()
            }
            .setCancelable(false)
            .show()
    }

    private fun setMainContent() {
        setContent {
            OdontoCodeTheme {
                val isAdmin by mainViewModel.isAdmin.collectAsStateWithLifecycle()
                SearchScreen(mainViewModel = mainViewModel, isAdmin = isAdmin) {
                    val userId = auth.currentUser?.uid
                    userId?.let {
                        cerrarSesionDesdeSettings(it)
                    }
                }
            }
        }
    }

    private fun cerrarSesionDesdeSettings(userId: String) {
        firestore.collection("users").document(userId)
            .update("activeDeviceId", "")
            .addOnSuccessListener {
                // ActiveDeviceId se actualizó correctamente, cerrar sesión de Firebase
                auth.signOut()
                redirigirAlLogin()
            }
            .addOnFailureListener {
                // Mostrar error al usuario si ocurre un problema
                Toast.makeText(this, "Error al cerrar sesión, intente de nuevo", Toast.LENGTH_LONG).show()
            }
    }

    private fun redirigirAlLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun cerrarSesionYRedirigir() {
        auth.signOut()
        redirigirAlLogin()
    }

    override fun onBackPressed() {
        when {
            mainViewModel.selectedProcedure.value != null -> {
                mainViewModel.clearSelectedProcedure()
                mainViewModel.requestClearFocus()
            }

            mainViewModel.expandedAreas.value?.isNotEmpty() == true -> {
                mainViewModel.collapseAllAreas()
                mainViewModel.requestClearFocus()
            }

            mainViewModel.expandedDiagnoses.value?.isNotEmpty() == true -> {
                mainViewModel.collapseAllDiagnoses()
                mainViewModel.requestClearFocus()
            }

            mainViewModel.backPressedOnce.value -> {
                super.onBackPressed()
            }

            else -> {
                mainViewModel.setBackPressedOnce(true)
                showExitToast()
                mainViewModel.resetBackPressedOnceWithDelay()
            }
        }
    }

    private fun showExitToast() {
        Toast.makeText(this, "Presiona nuevamente para salir", Toast.LENGTH_SHORT).show()
    }

    // Obtener ID único del dispositivo
    private fun getCurrentDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(mainViewModel: MainViewModel, isAdmin: Boolean, onLogoutClick: () -> Unit) {
    val areaList by mainViewModel.areas.collectAsStateWithLifecycle()
    val odontopediatriaList by mainViewModel.odontopediatriaAreas.collectAsStateWithLifecycle()
    val combinedAreaList = areaList + odontopediatriaList
    val searchResults by mainViewModel.searchResults.collectAsStateWithLifecycle()
    val searchCriteria by mainViewModel.searchCriteria.collectAsStateWithLifecycle()
    val selectedProcedure by mainViewModel.selectedProcedure.collectAsStateWithLifecycle()
    val expandedArea by mainViewModel.expandedArea.collectAsStateWithLifecycle()
    val expandedDiagnosis by mainViewModel.expandedDiagnosis.collectAsStateWithLifecycle()
    val diagnoses by mainViewModel.diagnoses.collectAsStateWithLifecycle()
    val procedures by mainViewModel.procedures.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var settingsMenuExpanded by remember { mutableStateOf(false) }

    // Limpiar el foco cuando se solicite desde el ViewModel
    val shouldClearFocus by mainViewModel.shouldClearFocus.collectAsStateWithLifecycle()
    if (shouldClearFocus) {
        LaunchedEffect(shouldClearFocus) {
            focusManager.clearFocus()
            keyboardController?.hide()
            mainViewModel.clearFocusHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { focusManager.clearFocus() }
    ) {
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
                                        context.startActivity(
                                            Intent(
                                                context,
                                                AdminUsersActivity::class.java
                                            )
                                        )
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
                        Icon(
                            painterResource(id = R.drawable.ic_favorite),
                            contentDescription = "Favorites"
                        )
                    }

                    Text(
                        "OdontoCode",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { focusManager.clearFocus() }
            ) {
                SearchDropdown(
                    mainViewModel = mainViewModel,
                    searchCriteria = searchCriteria
                )

                // Se eliminó `focusManager` como parámetro
                SearchBarWithDropdown(
                    mainViewModel = mainViewModel,
                    keyboardController = keyboardController,
                    focusRequester = focusRequester,
                    combinedAreaList = combinedAreaList
                )

                selectedProcedure?.let { procedure ->
                    ProcedureDetail(
                        mainViewModel = mainViewModel,
                        procedure = procedure,
                        onDismiss = {
                            mainViewModel.clearSelectedProcedure()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AreaDiagnosisList(
                    areaList = areaList,
                    odontopediatriaList = odontopediatriaList,
                    expandedArea = expandedArea,
                    expandedDiagnosis = expandedDiagnosis,
                    diagnoses = diagnoses,
                    procedures = procedures,
                    mainViewModel = mainViewModel
                )
            }
        }
    }
}

@Composable
fun SearchDropdown(
    mainViewModel: MainViewModel,
    searchCriteria: String
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Buscar por: ", style = MaterialTheme.typography.titleMedium)

        Box {
            Button(onClick = { expanded = true }) {
                Text(searchCriteria)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                listOf(
                    "Área",
                    "Procedimiento",
                    "CIE-10 Procedimiento",
                    "Diagnóstico",
                    "CIE-10 Diagnóstico"
                ).forEach { criteria ->
                    DropdownMenuItem(
                        onClick = {
                            mainViewModel.updateSearchCriteria(criteria)
                            expanded = false
                            mainViewModel.saveSearchOption(context, criteria)

                            // Lógica para cargar detalles como en el botón 'More'
                            val selectedProcedure = mainViewModel.searchResults.value.firstOrNull()
                            selectedProcedure?.let { procedure ->
                                mainViewModel.selectProcedure(procedure)
                                mainViewModel.loadAreaName(procedure.area)
                                mainViewModel.loadDiagnosisData(procedure.diagnosis)
                            }
                        },
                        text = { Text(criteria) }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchBarWithDropdown(
    mainViewModel: MainViewModel,
    keyboardController: SoftwareKeyboardController?,
    focusRequester: FocusRequester,
    combinedAreaList: List<Area>
) {
    var query by remember { mutableStateOf("") }
    val searchResults by mainViewModel.searchResults.collectAsStateWithLifecycle()
    val searchCriteria by mainViewModel.searchCriteria.collectAsStateWithLifecycle()
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Obtener LocalFocusManager aquí
    val focusManager = LocalFocusManager.current

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscador") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        keyboardController?.hide()
                    }
                }
                .shadow(4.dp, shape = MaterialTheme.shapes.medium),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) {
                        mainViewModel.searchProcedures(query)
                        isDropdownExpanded = true
                    } else {
                        mainViewModel.clearSearchResults()
                    }
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )
        )

        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = {
                isDropdownExpanded = false
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp) // Aumentar la altura máxima de la barra desplegable
                .padding(horizontal = 8.dp) // Añadir un pequeño padding horizontal para mejorar el diseño
        ) {
            searchResults.forEach { result ->
                DropdownMenuItem(
                    onClick = {
                        mainViewModel.selectProcedure(result)
                        mainViewModel.loadAreaName(result.area)
                        mainViewModel.loadDiagnosisData(result.diagnosis)
                        isDropdownExpanded = false
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp), // Añadir un padding vertical para mejorar la separación
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(3f)) {
                                    Text(
                                        result.cie10procedure, // Mostrando CIE-10 en una línea
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp // Aumentar ligeramente el tamaño de la fuente
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        result.diagnosis, // Mostrando Diagnosis en la siguiente línea
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        result.procedure,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp)
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        combinedAreaList.associateBy { it.id }[result.area]?.name
                                            ?: "Área no disponible",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Divider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AreaDiagnosisList(
    areaList: List<Area>,
    odontopediatriaList: List<Area>,
    expandedArea: String?,
    expandedDiagnosis: String?,
    diagnoses: List<Diagnosis>,
    procedures: List<Procedure>,
    mainViewModel: MainViewModel
) {
    val focusManager = LocalFocusManager.current // <- Mueve aquí la llamada al FocusManager

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { focusManager.clearFocus() } // <- Usa la referencia aquí
    ) {
        item {
            Text(
                text = "Áreas Generales",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(areaList, key = { it.id }) { area ->
            AreaRow(area = area, onAreaSelected = {
                mainViewModel.toggleAreaExpansion(area.id)
                mainViewModel.collapseAllDiagnoses()
                if (expandedArea != area.id) {
                    mainViewModel.fetchDiagnosesByArea(area.id)
                }
            })

            if (expandedArea == area.id) {
                diagnoses.filter { it.area == area.id }.forEach { diagnosisItem ->
                    DiagnosisRow(
                        diagnosis = diagnosisItem,
                        isExpanded = expandedDiagnosis == diagnosisItem.id,
                        onExpandClick = {
                            mainViewModel.toggleDiagnosisExpansion(diagnosisItem.id)
                            if (expandedDiagnosis != diagnosisItem.id) {
                                mainViewModel.fetchProceduresByDiagnosis(diagnosisItem.id)
                            }
                        }
                    )

                    if (expandedDiagnosis == diagnosisItem.id) {
                        procedures.filter { it.diagnosis == diagnosisItem.id }.forEach { procedureItem ->
                            AnimatedProcedureRow(
                                procedure = procedureItem,
                                onProcedureSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                onMoreSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                mainViewModel = mainViewModel
                            )
                        }
                    }
                }

                val proceduresWithoutDiagnosis = procedures.filter { it.diagnosis == "N/A" && it.area == area.id }
                if (proceduresWithoutDiagnosis.isNotEmpty()) {
                    val diagnosisId = "SinEspecificar_\${area.id}"
                    DiagnosisRow(
                        diagnosis = Diagnosis(
                            id = diagnosisId,
                            name = "Sin Especificar",
                            cie10diagnosis = "N/A",
                            area = area.id
                        ),
                        isExpanded = expandedDiagnosis == diagnosisId,
                        onExpandClick = {
                            mainViewModel.toggleDiagnosisExpansion(diagnosisId)
                        }
                    )

                    if (expandedDiagnosis == diagnosisId) {
                        proceduresWithoutDiagnosis.forEach { procedureItem ->
                            AnimatedProcedureRow(
                                procedure = procedureItem,
                                onProcedureSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                onMoreSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                mainViewModel = mainViewModel
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Odontopediatría",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(odontopediatriaList, key = { it.id }) { area ->
            AreaRow(area = area, onAreaSelected = {
                mainViewModel.toggleAreaExpansion(area.id)
                mainViewModel.collapseAllDiagnoses()
                if (expandedArea != area.id) {
                    mainViewModel.fetchDiagnosesByOdontopediatriaArea(area.id)
                }
            })

            if (expandedArea == area.id) {
                diagnoses.filter { it.area == area.id }.forEach { diagnosisItem ->
                    DiagnosisRow(
                        diagnosis = diagnosisItem,
                        isExpanded = expandedDiagnosis == diagnosisItem.id,
                        onExpandClick = {
                            mainViewModel.toggleDiagnosisExpansion(diagnosisItem.id)
                            if (expandedDiagnosis != diagnosisItem.id) {
                                mainViewModel.fetchProceduresByDiagnosis(diagnosisItem.id)
                            }
                        }
                    )

                    if (expandedDiagnosis == diagnosisItem.id) {
                        procedures.filter { it.diagnosis == diagnosisItem.id }.forEach { procedureItem ->
                            AnimatedProcedureRow(
                                procedure = procedureItem,
                                onProcedureSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                onMoreSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                mainViewModel = mainViewModel
                            )
                        }
                    }
                }

                val proceduresWithoutDiagnosis = procedures.filter { it.diagnosis == "N/A" && it.area == area.id }
                if (proceduresWithoutDiagnosis.isNotEmpty()) {
                    val diagnosisId = "SinEspecificar_\${area.id}"
                    DiagnosisRow(
                        diagnosis = Diagnosis(
                            id = diagnosisId,
                            name = "Sin Especificar",
                            cie10diagnosis = "N/A",
                            area = area.id
                        ),
                        isExpanded = expandedDiagnosis == diagnosisId,
                        onExpandClick = {
                            mainViewModel.toggleDiagnosisExpansion(diagnosisId)
                        }
                    )

                    if (expandedDiagnosis == diagnosisId) {
                        proceduresWithoutDiagnosis.forEach { procedureItem ->
                            AnimatedProcedureRow(
                                procedure = procedureItem,
                                onProcedureSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                onMoreSelected = {
                                    mainViewModel.selectProcedure(procedureItem)
                                    mainViewModel.loadAreaName(procedureItem.area)
                                    mainViewModel.loadDiagnosisData(procedureItem.diagnosis)
                                },
                                mainViewModel = mainViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedProcedureRow(
    procedure: Procedure,
    onProcedureSelected: (Procedure) -> Unit,
    onMoreSelected: () -> Unit,
    mainViewModel: MainViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val favoriteProcedures by mainViewModel.favoriteProcedures.collectAsStateWithLifecycle()
    val isFavorite = favoriteProcedures.contains(procedure.id)

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

                IconButton(onClick = { mainViewModel.toggleFavorite(procedure.id) }) {
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
                        TextButton(onClick = {
                            onMoreSelected()
                            mainViewModel.loadAreaName(procedure.area)
                            mainViewModel.loadDiagnosisData(procedure.diagnosis)
                        }) {
                            Text("More")
                        }
                    }
                }
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

@Composable
fun ProcedureDetail(
    mainViewModel: MainViewModel,
    procedure: Procedure,
    onDismiss: () -> Unit
) {
    val areaName by mainViewModel.areaName.collectAsStateWithLifecycle()
    val diagnosisName by mainViewModel.diagnosisName.collectAsStateWithLifecycle()
    val diagnosisCIE10 by mainViewModel.diagnosisCIE10.collectAsStateWithLifecycle()
    val favoriteProcedures by mainViewModel.favoriteProcedures.collectAsStateWithLifecycle()
    val isFavorite = favoriteProcedures.contains(procedure.id)

    val isLoading = areaName == "Cargando..." || diagnosisName == "Cargando..."

    if (isLoading) {
        // Mostrar un indicador de carga mientras los datos están siendo cargados
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Código de Procedimiento: ${procedure.cie10procedure}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Procedimiento: ${procedure.procedure}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(text = "Diagnóstico: $diagnosisName", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Código de Diagnóstico: $diagnosisCIE10",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = "Área: $areaName", style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(16.dp))

                // Icono de favorito
                IconButton(onClick = { mainViewModel.toggleFavorite(procedure.id) }) {
                    Icon(
                        painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(
                            id = R.drawable.ic_favorite_border
                        ),
                        contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

