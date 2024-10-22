package com.tonymen.odontocode.view

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.ImeAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.Diagnosis
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

                var favoriteDiagnoses by remember { mutableStateOf<List<Diagnosis>>(emptyList()) }
                var query by remember { mutableStateOf("") }
                var filteredDiagnoses by remember { mutableStateOf<List<Diagnosis>>(emptyList()) }

                // Cargar favoritos desde Firestore
                LaunchedEffect(Unit) {
                    fetchUserFavorites(userId) { favorites ->
                        favoriteDiagnoses = favorites
                        filteredDiagnoses = favorites
                    }
                }

                // Buscar y filtrar favoritos
                fun onSearch(query: String) {
                    filteredDiagnoses = if (query.isBlank()) {
                        favoriteDiagnoses
                    } else {
                        favoriteDiagnoses.filter { it.code.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
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
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,  // Controla el color del texto cuando el campo está enfocado
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)  // Controla el color del texto cuando no está enfocado
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredDiagnoses) { diagnosis ->
                                FavoriteDiagnosisItem(diagnosis, userId, firestore)
                            }
                        }
                    }
                }
            }
        }
    }

    // Función para cargar los diagnósticos favoritos del usuario
    private fun fetchUserFavorites(userId: String, onResult: (List<Diagnosis>) -> Unit) {
        firestore.collection("userFavorites")
            .document(userId)
            .collection("diagnoses")  // Subcolección de diagnósticos favoritos
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("FavoritesActivity", "No se encontraron favoritos para el usuario: $userId")
                } else {
                    Log.d("FavoritesActivity", "Favoritos encontrados: ${documents.size()}")
                }

                val favorites = documents.map { it.toObject(Diagnosis::class.java) }
                onResult(favorites)
            }
            .addOnFailureListener { e ->
                Log.e("FavoritesActivity", "Error al cargar favoritos", e)
                onResult(emptyList())
            }
    }

}

@Composable
fun FavoriteDiagnosisItem(diagnosis: Diagnosis, userId: String, firestore: FirebaseFirestore) {
    var isFavorite by remember { mutableStateOf(false) }

    // Verificar si es favorito al cargar la vista
    LaunchedEffect(diagnosis.id) {
        val favoriteRef = firestore.collection("userFavorites")
            .document(userId)
            .collection("diagnoses")
            .document(diagnosis.id)

        favoriteRef.get()
            .addOnSuccessListener { document ->
                isFavorite = document.exists() // Si el documento existe, es favorito
            }
    }

    // Función para actualizar el estado de favoritos en Firestore
    fun toggleFavorite() {
        val favoriteRef = firestore.collection("userFavorites")
            .document(userId)
            .collection("diagnoses")
            .document(diagnosis.id)

        if (isFavorite) {
            // Eliminar de favoritos en Firestore
            favoriteRef.delete()
                .addOnSuccessListener {
                    isFavorite = false
                    Log.d("FavoriteDiagnosisItem", "Eliminado de favoritos: ${diagnosis.id}")
                }
                .addOnFailureListener { e -> Log.e("FavoriteDiagnosisItem", "Error al eliminar: ", e) }
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
                    Log.d("FavoriteDiagnosisItem", "Añadido a favoritos: ${diagnosis.id}")
                }
                .addOnFailureListener { e -> Log.e("FavoriteDiagnosisItem", "Error al añadir: ", e) }
        }
    }

    // Diseño del elemento de la lista de favoritos
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Código: ${diagnosis.code}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "Nombre: ${diagnosis.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "Descripción: ${diagnosis.description}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // Icono de corazón para marcar/desmarcar como favorito
            IconButton(
                onClick = { toggleFavorite() }
            ) {
                Icon(
                    painter = if (isFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                    contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}
