package com.tonymen.odontocode.view

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.User
import com.tonymen.odontocode.data.UserType
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme

class AdminUsersActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Verificar si el usuario actual es un administrador
            firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    val user = document.toObject(User::class.java)
                    if (user?.userType == UserType.ADMIN) {
                        // Si es administrador, permitir acceder a la pantalla
                        setContent {
                            OdontoCodeTheme {
                                AdminUsersScreen(firestore = firestore)
                            }
                        }
                    } else {
                        // Si no es administrador, cerrar la actividad y mostrar un mensaje
                        Toast.makeText(
                            this,
                            "No tienes permisos para acceder a esta sección.",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
                .addOnFailureListener {
                    // Manejar el error al obtener los datos del usuario
                    Toast.makeText(
                        this,
                        "Error al verificar permisos del usuario.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
        } else {
            // Si no hay un usuario autenticado, cerrar la actividad
            Toast.makeText(
                this,
                "Usuario no autenticado.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(firestore: FirebaseFirestore) {
    var query by remember { mutableStateOf("") }
    val userList = remember { mutableStateListOf<User>() }
    var filteredList by remember { mutableStateOf<List<User>>(emptyList()) }
    var accessFilter by remember { mutableStateOf("Todos") }

    // Cargar los usuarios cuando se inicia la pantalla
    LaunchedEffect(Unit) {
        fetchUsers(firestore) { users ->
            userList.clear()
            userList.addAll(users.sortedBy { it.name }) // Ordenar usuarios por nombre
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
                    filteredList = applyFilters(userList, query, accessFilter)
                },
                label = { Text("Buscar Usuario") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Filtro de acceso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtrar por acceso:", style = MaterialTheme.typography.bodyLarge)
                AccessFilterButton(currentFilter = accessFilter) { selectedFilter ->
                    accessFilter = selectedFilter
                    filteredList = applyFilters(userList, query, accessFilter)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar la lista filtrada de usuarios
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.id }) { user ->
                    UserItem(user = user, firestore = firestore) { updatedUser ->
                        // Actualizar el usuario en la lista
                        val index = userList.indexOfFirst { it.id == updatedUser.id }
                        if (index != -1) {
                            userList[index] = updatedUser
                            filteredList = applyFilters(userList, query, accessFilter)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccessFilterButton(currentFilter: String, onFilterSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize()) {
        Button(onClick = { expanded = true }) {
            Text(text = currentFilter)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = {
                expanded = false
                onFilterSelected("Todos")
            })
            DropdownMenuItem(text = { Text("Concedido") }, onClick = {
                expanded = false
                onFilterSelected("Concedido")
            })
            DropdownMenuItem(text = { Text("Denegado") }, onClick = {
                expanded = false
                onFilterSelected("Denegado")
            })
        }
    }
}

fun applyFilters(users: List<User>, query: String, accessFilter: String): List<User> {
    return users.filter { user ->
        val matchesQuery = user.name.contains(query, ignoreCase = true)
        val matchesAccess = when (accessFilter) {
            "Concedido" -> user.approved
            "Denegado" -> !user.approved
            else -> true
        }
        matchesQuery && matchesAccess
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserItem(user: User, firestore: FirebaseFirestore, onUserUpdated: (User) -> Unit) {
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
                    updateApprovedStatus(firestore, user.id, !approved) { success ->
                        if (success) {
                            approved = !approved // Cambiar el estado local después de la actualización exitosa
                            onUserUpdated(user.copy(approved = approved)) // Notificar la actualización
                        }
                    }
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
fun updateApprovedStatus(firestore: FirebaseFirestore, userId: String, newApprovedStatus: Boolean, onComplete: (Boolean) -> Unit) {
    firestore.collection("users").document(userId)
        .update("approved", newApprovedStatus)
        .addOnSuccessListener {
            onComplete(true) // Actualización exitosa
        }
        .addOnFailureListener {
            onComplete(false) // Manejar el error
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
