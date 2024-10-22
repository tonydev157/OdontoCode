    package com.tonymen.odontocode.view

    import android.content.Intent
    import android.os.Bundle
    import android.util.Log
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.foundation.background
    import androidx.compose.foundation.border
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material.icons.filled.Settings
    import androidx.compose.material.icons.filled.Close
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
    import androidx.compose.material.icons.filled.Note
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
    import com.tonymen.odontocode.R
    import com.tonymen.odontocode.data.User
    import com.tonymen.odontocode.data.UserType
    import com.tonymen.odontocode.data.Category
    import com.tonymen.odontocode.data.Diagnosis
    import com.tonymen.odontocode.data.Favorite
    import com.tonymen.odontocode.ui.theme.OdontoCodeTheme


    class MainActivity : ComponentActivity() {
        private lateinit var firestore: FirebaseFirestore
        private var isAdmin by mutableStateOf(false) // Nueva variable para controlar si el usuario es Admin

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Inicializar Firestore
            firestore = FirebaseFirestore.getInstance()

            // Verificar si el usuario es Admin
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        val user = document.toObject(User::class.java) // Especificar el tipo 'User' de forma explícita
                        if (user?.userType == UserType.ADMIN) {
                            isAdmin = true // Si es Admin, establecemos isAdmin a true
                        }
                    }
            }

            setContent {
                OdontoCodeTheme {
                    SearchScreen(firestore, isAdmin) { // Añadir callback para el cierre de sesión
                        FirebaseAuth.getInstance().signOut()
                        // Redirigir a LoginActivity y cerrar las actividades previas
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun SearchScreen(firestore: FirebaseFirestore, isAdmin: Boolean, onLogoutClick: () -> Unit) {
        var query by remember { mutableStateOf("") }
        var selectedDiagnosis by remember { mutableStateOf<Diagnosis?>(null) }
        var searchByCode by remember { mutableStateOf(true) }
        var categoryList by remember { mutableStateOf<List<Category>>(emptyList()) }
        var expandedCategories by remember { mutableStateOf<List<String>>(emptyList()) }
        var diagnosisMap by remember { mutableStateOf<Map<String, List<Diagnosis>>>(emptyMap()) }
        var searchResults by remember { mutableStateOf<List<Diagnosis>>(emptyList()) }
        var isDropdownExpanded by remember { mutableStateOf(false) }
        var allDiagnosisList by remember { mutableStateOf<List<Diagnosis>>(emptyList()) }
        var settingsMenuExpanded by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val context = LocalContext.current  // Añadir contexto para navegar entre actividades

        // Cargar todas las categorías al iniciar
        LaunchedEffect(Unit) {
            fetchCategories(firestore) { fetchedCategories ->
                categoryList = fetchedCategories.sortedBy { it.name }
            }
        }

        // Cargar todos los diagnósticos al iniciar
        LaunchedEffect(Unit) {
            fetchAllDiagnoses(firestore) { results ->
                allDiagnosisList = results
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Search Diagnosis") },
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
                                    onLogoutClick() // Llamada al callback de cierre de sesión
                                },
                                text = { Text("Cerrar Sesión") }
                            )

                            // Solo mostrar "Administrar Usuarios" si el usuario es Admin
                            if (isAdmin) {
                                DropdownMenuItem(
                                    onClick = {
                                        // Navegar a la pantalla de administración de usuarios
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
                        // Navegar a la pantalla de notas
                        context.startActivity(Intent(context, NotesActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Note, contentDescription = "Notes")
                    }
                    Spacer(Modifier.weight(1f))

                    // Nuevo icono de corazón para los favoritos
                    IconButton(onClick = {
                        // Navegar a la pantalla de favoritos
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
                // Opciones para seleccionar el método de búsqueda
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Search by: ", style = MaterialTheme.typography.titleMedium)
                    Row {
                        Text("Code", style = MaterialTheme.typography.bodyLarge)
                        RadioButton(
                            selected = searchByCode,
                            onClick = {
                                searchByCode = true
                                selectedDiagnosis = null
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Name", style = MaterialTheme.typography.bodyLarge)
                        RadioButton(
                            selected = !searchByCode,
                            onClick = {
                                searchByCode = false
                                selectedDiagnosis = null
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Buscador de diagnósticos por código o nombre con lista desplegable
                SearchBarWithDropdown(
                    query = query,
                    searchResults = searchResults,
                    onQueryChange = { newQuery -> query = newQuery },
                    onSearch = {
                        if (query.isNotBlank()) {
                            searchResults = filterDiagnosisList(query, allDiagnosisList, searchByCode)
                            isDropdownExpanded = searchResults.isNotEmpty()
                            selectedDiagnosis = null
                        } else {
                            searchResults = emptyList()
                            selectedDiagnosis = null
                            isDropdownExpanded = false
                        }
                    },
                    onResultSelected = { diagnosis ->
                        selectedDiagnosis = diagnosis
                        isDropdownExpanded = false
                    },
                    isDropdownExpanded = isDropdownExpanded,
                    onDismissDropdown = { isDropdownExpanded = false },
                    focusRequester = focusRequester,
                    keyboardController = keyboardController
                )

                // Mostrar el diagnóstico seleccionado si hay uno
                selectedDiagnosis?.let { diagnosis ->
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        DiagnosisDetail(firestore, diagnosis, userId, onDismiss = { selectedDiagnosis = null })
                    }
                }

                // Espacio entre el buscador y la lista de categorías
                Spacer(modifier = Modifier.height(16.dp))

                // Contenido desplazable para búsqueda manual por categorías
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Mostrar categorías y diagnósticos al hacer clic
                    items(categoryList) { category ->
                        Column {
                            CategoryRow(category = category, onCategorySelected = {
                                if (expandedCategories.contains(category.id)) {
                                    expandedCategories = expandedCategories - category.id
                                } else {
                                    expandedCategories = expandedCategories + category.id
                                    fetchDiagnosisByCategory(firestore, category.id) { diagnosisList ->
                                        diagnosisMap = diagnosisMap.toMutableMap().apply {
                                            put(category.id, diagnosisList)
                                        }
                                    }
                                }
                            })

                            if (expandedCategories.contains(category.id)) {
                                diagnosisMap[category.id]?.forEach { diagnosisItem ->
                                    AnimatedDiagnosisRow(
                                        diagnosis = diagnosisItem,
                                        onDiagnosisSelected = { selectedDiagnosis = it },
                                        onMoreSelected = { selectedDiagnosis = diagnosisItem }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    // Componente de barra de búsqueda con menú desplegable
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun SearchBarWithDropdown(
        query: String,
        searchResults: List<Diagnosis>,
        onQueryChange: (String) -> Unit,
        onSearch: () -> Unit,
        onResultSelected: (Diagnosis) -> Unit,
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
                label = { Text("Enter diagnosis code or name") },
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
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface), // Configura el color del texto directamente
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
                    // Mantener el teclado abierto y el campo enfocado
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight) // Limitar la altura del menú
            ) {
                searchResults.forEach { result ->
                    DropdownMenuItem(
                        onClick = {
                            onResultSelected(result)
                            // Mantener el teclado abierto y el campo enfocado al seleccionar un resultado
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        text = {
                            Column {
                                Text(result.code, style = MaterialTheme.typography.bodyMedium)
                                Text(result.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    )
                }
            }
        }
    }


    // Componente para cada fila de categoría
    @Composable
    fun CategoryRow(category: Category, onCategorySelected: (Category) -> Unit) {
        Button(
            onClick = { onCategorySelected(category) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)) // Esta línea es opcional, pero asegúrate de que el color de fondo sea adecuado
                .shadow(0.dp, RoundedCornerShape(8.dp)) // Cambia el valor de sombra a 0.dp para hacerla transparente
        ) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
        }
    }

// Componente para cada fila de diagnóstico (con animación)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedDiagnosisRow(diagnosis: Diagnosis, onDiagnosisSelected: (Diagnosis) -> Unit, onMoreSelected: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }

    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val firestore = FirebaseFirestore.getInstance()

    // Verificar si es favorito al cargar la vista
    LaunchedEffect(diagnosis.id) {
        if (userId != null) {
            firestore.collection("userFavorites")
                .document(userId)
                .collection("diagnoses")
                .document(diagnosis.id)
                .get()
                .addOnSuccessListener { document ->
                    isFavorite = document.exists() // Si el documento existe, es favorito
                }
        }
    }

    // Función para actualizar el estado de favoritos en Firestore
    fun toggleFavorite() {
        if (userId == null) return

        val favoriteRef = firestore.collection("userFavorites")
            .document(userId)
            .collection("diagnoses")
            .document(diagnosis.id)

        if (isFavorite) {
            // Eliminar de favoritos en Firestore
            favoriteRef.delete()
                .addOnSuccessListener {
                    isFavorite = false
                    Log.d("AnimatedDiagnosisRow", "Eliminado de favoritos: ${diagnosis.id}")
                }
                .addOnFailureListener { e -> Log.e("AnimatedDiagnosisRow", "Error al eliminar: ", e) }
        } else {
            // Añadir a favoritos en Firestore
            val favorite = mapOf(
                "id" to diagnosis.id,
                "name" to diagnosis.name,
                "description" to diagnosis.description,
                "category" to diagnosis.category,
                "code" to diagnosis.code
            )
            favoriteRef.set(favorite)
                .addOnSuccessListener {
                    isFavorite = true
                    Log.d("AnimatedDiagnosisRow", "Añadido a favoritos: ${diagnosis.id}")
                }
                .addOnFailureListener { e -> Log.e("AnimatedDiagnosisRow", "Error al añadir: ", e) }
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))  // Borde similar al del DiagnosisDetail
            .background(MaterialTheme.colorScheme.surface),  // Fondo claro
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp  // Para imitar el leve relieve de DiagnosisDetail
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(diagnosis.code, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(diagnosis.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyLarge)

                // Icono de corazón para marcar/desmarcar como favorito
                IconButton(onClick = { toggleFavorite() }) {
                    Icon(
                        painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                        contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Descripción: ${diagnosis.description}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
    fun DiagnosisDetail(
        firestore: FirebaseFirestore,
        diagnosis: Diagnosis,
        userId: String, // Pasar el userId del usuario autenticado
        onDismiss: () -> Unit
    ) {
        var categoryName by remember { mutableStateOf("") }
        var isFavorite by remember { mutableStateOf(false) } // Controla si es favorito o no

        // Verificar si el diagnóstico es favorito al cargar
        LaunchedEffect(diagnosis.id) {
            firestore.collection("userFavorites")
                .document(userId)
                .collection("diagnoses")  // Subcolección de diagnósticos favoritos
                .document(diagnosis.id)
                .get()
                .addOnSuccessListener { document ->
                    isFavorite = document.exists() // Si el documento existe, es favorito
                }
        }


        // Cargar la categoría del diagnóstico
        LaunchedEffect(diagnosis.category) {
            firestore.collection("categories")
                .document(diagnosis.category)
                .get()
                .addOnSuccessListener { document ->
                    categoryName = document.getString("name") ?: "Unknown Category"
                }
        }

        // Función para añadir o eliminar de favoritos
        fun toggleFavorite() {
            val favoriteRef = firestore.collection("favorites")
                .document(userId)
                .collection("userFavorites")
                .document(diagnosis.id)

            if (isFavorite) {
                // Eliminar de favoritos
                favoriteRef.delete()
                    .addOnSuccessListener { isFavorite = false }
            } else {
                // Añadir a favoritos
                val favorite = Favorite(
                    diagnosisId = diagnosis.id,
                    userId = userId,
                    name = diagnosis.name,
                    code = diagnosis.code,
                    category = diagnosis.category,
                    description = diagnosis.description
                )
                favoriteRef.set(favorite)
                    .addOnSuccessListener { isFavorite = true }
            }
        }

        // UI de la pantalla de detalle
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
                        text = "Diagnosis Code: ${diagnosis.code}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Name: ${diagnosis.name}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Description: ${diagnosis.description}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Category: $categoryName",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Mostrar el corazón para marcar como favorito
                IconButton(
                    onClick = { toggleFavorite() }
                ) {
                    Icon(
                        painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                        contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray // Aquí ajustas el color del favorito
                    )
                }

            }
        }
    }


// Función para cargar todos los diagnósticos desde Firestore a memoria local
fun fetchAllDiagnoses(firestore: FirebaseFirestore, onResult: (List<Diagnosis>) -> Unit) {
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

// Función para filtrar diagnósticos localmente según la consulta de búsqueda
fun filterDiagnosisList(query: String, diagnosisList: List<Diagnosis>, searchByCode: Boolean): List<Diagnosis> {
    return if (searchByCode) {
        diagnosisList.filter { diagnosis ->
            diagnosis.code.contains(query, ignoreCase = true)
        }
    } else {
        diagnosisList.filter { diagnosis ->
            diagnosis.name.contains(query, ignoreCase = true) ||
                    diagnosis.description.contains(query, ignoreCase = true)
        }
    }
}

// Función para buscar diagnosis en Firestore por categoría
fun fetchDiagnosisByCategory(firestore: FirebaseFirestore, categoryId: String, onResult: (List<Diagnosis>) -> Unit) {
    firestore.collection("diagnosis")
        .whereEqualTo("category", categoryId)
        .get()
        .addOnSuccessListener { documents ->
            val diagnosisList = documents.map { it.toObject(Diagnosis::class.java) }
            onResult(diagnosisList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}

// Función para cargar categorías desde Firestore
fun fetchCategories(firestore: FirebaseFirestore, onResult: (List<Category>) -> Unit) {
    firestore.collection("categories")
        .get()
        .addOnSuccessListener { documents ->
            val categoryList = documents.map { document ->
                val category = document.toObject(Category::class.java)
                category.copy(id = document.id)
            }
            onResult(categoryList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}
