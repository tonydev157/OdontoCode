package com.tonymen.odontocode.view

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.Diagnosis
import com.tonymen.odontocode.data.Favorite
import com.tonymen.odontocode.data.Procedure
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme

class FavoritesActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OdontoCodeTheme {
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Log.e("FavoritesActivity", "Usuario no autenticado")
                    return@OdontoCodeTheme
                } else {
                    Log.d("FavoritesActivity", "Usuario autenticado con ID: $userId")
                }

                var favoriteProcedures by remember { mutableStateOf<List<Procedure>>(emptyList()) }
                var query by remember { mutableStateOf("") }
                var filteredFavorites by remember { mutableStateOf<List<Procedure>>(emptyList()) }

                // Cargar favoritos desde Firestore
                LaunchedEffect(Unit) {
                    fetchUserFavorites(userId) { favorites ->
                        favoriteProcedures = favorites
                        filteredFavorites = favorites
                    }
                }

                // Buscar y filtrar favoritos
                fun onSearch(query: String) {
                    filteredFavorites = if (query.isBlank()) {
                        favoriteProcedures
                    } else {
                        favoriteProcedures.filter {
                            it.cie10procedure.contains(query, ignoreCase = true) ||
                                    it.procedure.contains(query, ignoreCase = true)
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Mis Favoritos", color = MaterialTheme.colorScheme.onPrimary) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Barra de búsqueda
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                onSearch(it)
                            },
                            label = { Text("Buscar por código o nombre", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onSurface) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                onSearch(query)
                            }),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFavorites) { favorite ->
                                FavoriteProcedureItem(favorite, userId, firestore)
                            }
                        }
                    }
                }
            }
        }
    }

    // Función para cargar los procedimientos favoritos del usuario
    private fun fetchUserFavorites(userId: String, onResult: (List<Procedure>) -> Unit) {
        val userFavoritesRef = firestore.collection("userFavorites").document(userId)

        userFavoritesRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val favorite = document.toObject(Favorite::class.java)
                    val procedureIds = favorite?.procedureIds ?: emptyList()

                    if (procedureIds.isNotEmpty()) {
                        // Obtener los detalles de los procedimientos favoritos
                        firestore.collection("procedures")
                            .whereIn("id", procedureIds)
                            .get()
                            .addOnSuccessListener { procedureDocs ->
                                val procedures = procedureDocs.map { it.toObject(Procedure::class.java) }
                                onResult(procedures)
                            }
                            .addOnFailureListener { e ->
                                Log.e("FavoritesActivity", "Error al cargar procedimientos favoritos", e)
                                onResult(emptyList())
                            }
                    } else {
                        Log.d("FavoritesActivity", "No hay procedimientos favoritos para este usuario.")
                        onResult(emptyList())
                    }
                } else {
                    Log.d("FavoritesActivity", "El documento de favoritos no existe para este usuario.")
                    onResult(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e("FavoritesActivity", "Error al cargar favoritos", e)
                onResult(emptyList())
            }
    }
}

@Composable
fun FavoriteProcedureItem(favorite: Procedure, userId: String, firestore: FirebaseFirestore) {
    var isFavorite by remember { mutableStateOf(true) }
    var diagnosisName by remember { mutableStateOf("Cargando...") }
    var areaName by remember { mutableStateOf("Cargando...") }
    var diagnosisCIE10 by remember { mutableStateOf("Cargando...") }  // Nueva variable para el CIE-10 del diagnóstico

    // Función para actualizar el estado de favoritos en Firestore
    fun toggleFavorite() {
        val favoriteRef = firestore.collection("userFavorites")
            .document(userId)

        favoriteRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val currentFavorites = document.toObject(Favorite::class.java)
                val updatedFavorites = currentFavorites?.procedureIds?.toMutableList() ?: mutableListOf()

                if (isFavorite) {
                    updatedFavorites.remove(favorite.id)
                } else {
                    updatedFavorites.add(favorite.id)
                }

                favoriteRef.update("procedureIds", updatedFavorites)
                    .addOnSuccessListener {
                        isFavorite = !isFavorite
                        Log.d("FavoriteProcedureItem", "Favorito actualizado")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FavoriteProcedureItem", "Error al actualizar favorito", e)
                    }
            }
        }
    }

    // Cargar el nombre del diagnóstico y su CIE-10 basado en el ID almacenado en favorite.diagnosis
    LaunchedEffect(favorite.diagnosis) {
        firestore.collection("diagnosis")
            .document(favorite.diagnosis)
            .get()
            .addOnSuccessListener { document ->
                val diagnosis = document.toObject(Diagnosis::class.java)
                diagnosisName = diagnosis?.name ?: "Diagnóstico no encontrado"
                diagnosisCIE10 = diagnosis?.cie10diagnosis ?: "CIE-10 no encontrado" // Asignar el CIE-10 del diagnóstico
            }
            .addOnFailureListener {
                diagnosisName = "Error al cargar diagnóstico"
                diagnosisCIE10 = "Error al cargar CIE-10"
            }
    }

    // Cargar el nombre del área basado en el ID almacenado en favorite.area
    LaunchedEffect(favorite.area) {
        firestore.collection("areas")
            .document(favorite.area)
            .get()
            .addOnSuccessListener { document ->
                val area = document.getString("name") // Suponiendo que "name" es el campo que almacena el nombre del área
                areaName = area ?: "Área no encontrada"
            }
            .addOnFailureListener {
                areaName = "Error al cargar área"
            }
    }

    // Si algunos campos están vacíos, se mostrará un texto de "Datos no disponibles"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Código: ${favorite.cie10procedure.ifBlank { "Datos no disponibles" }}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Procedimiento: ${favorite.procedure.ifBlank { "Datos no disponibles" }}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Diagnóstico: $diagnosisName",  // Mostrar el nombre del diagnóstico cargado
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "CIE-10 Diagnóstico: $diagnosisCIE10",  // Mostrar el CIE-10 del diagnóstico cargado
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Área: $areaName",  // Mostrar el nombre del área cargada
                style = MaterialTheme.typography.bodyMedium
            )

            // Icono de corazón para marcar/desmarcar como favorito
            IconButton(onClick = { toggleFavorite() }) {
                Icon(
                    painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                    contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}


