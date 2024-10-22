package com.tonymen.odontocode.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.User
import com.tonymen.odontocode.data.UserType
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme

class AdminUsersActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OdontoCodeTheme {
                AdminUsersScreen(firestore = firestore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(firestore: FirebaseFirestore) {
    var query by remember { mutableStateOf("") }
    var userList by remember { mutableStateOf<List<User>>(emptyList()) }
    var filteredList by remember { mutableStateOf<List<User>>(emptyList()) }

    // Cargar los usuarios cuando se inicia la pantalla
    LaunchedEffect(Unit) {
        fetchUsers(firestore) { users ->
            userList = users.sortedBy { it.name } // Ordenar usuarios por nombre
            filteredList = userList // Inicialmente, no hay filtro
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrar Usuarios") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                onValueChange = { newQuery ->
                    query = newQuery
                    filteredList = if (newQuery.isBlank()) {
                        userList
                    } else {
                        userList.filter { it.name.contains(newQuery, ignoreCase = true) }
                    }
                },
                label = { Text("Buscar Usuario") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(), // Eliminamos el Modifier.border para evitar el doble borde
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.Transparent, // Evitar que tenga un fondo si no quieres un color
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )


            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar la lista filtrada de usuarios
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList) { user ->
                    UserItem(user = user, firestore = firestore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserItem(user: User, firestore: FirebaseFirestore) {
    var approved by remember { mutableStateOf(user.approved) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        // Confirmación para cambiar el estado de acceso
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmación") },
            text = {
                Text("¿Estás seguro de que deseas ${if (approved) "revocar" else "dar"} acceso a este usuario?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    approved = !approved // Cambiar el estado local
                    updateApprovedStatus(firestore, user.id, approved) // Actualizar en Firestore
                }) {
                    Text("Sí", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Nombre: ${user.name}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Correo: ${user.email}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Cédula: ${user.ci}", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Acceso: ${if (approved) "Concedido" else "Denegado"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Botón para cambiar el estado de acceso
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (approved) "Revocar acceso" else "Dar acceso")
                }
            }
        }
    }
}

// Función para actualizar el estado de approved en Firestore
fun updateApprovedStatus(firestore: FirebaseFirestore, userId: String, newApprovedStatus: Boolean) {
    firestore.collection("users").document(userId)
        .update("approved", newApprovedStatus)
        .addOnSuccessListener {
            // Actualización exitosa
        }
        .addOnFailureListener {
            // Manejar el error
        }
}

// Función para cargar los usuarios desde Firestore
fun fetchUsers(firestore: FirebaseFirestore, onResult: (List<User>) -> Unit) {
    firestore.collection("users")
        .whereEqualTo("userType", UserType.USER.name) // Filtrar solo usuarios del tipo USER
        .get()
        .addOnSuccessListener { documents ->
            val usersList = documents.map { it.toObject(User::class.java) }
            onResult(usersList)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
}
