package com.tonymen.odontocode.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import com.tonymen.odontocode.viewmodel.FavoritesViewModel

class FavoritesActivity : ComponentActivity() {

    private val viewModel: FavoritesViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OdontoCodeTheme {
                val favoriteProcedures by viewModel.filteredFavorites.collectAsStateWithLifecycle()
                var query by remember { mutableStateOf("") }

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
                                viewModel.searchFavorites(it)
                            },
                            label = { Text("Buscar por código o nombre", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Buscar",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.searchFavorites(query)
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

                        // Mostrar los procedimientos favoritos
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(favoriteProcedures) { favorite ->
                                FavoriteProcedureItem(
                                    favorite = favorite,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.confirmFavoritesChanges() // Confirmar cambios de favoritos al pausar la actividad
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.confirmFavoritesChanges() // Confirmar cambios de favoritos al salir de la actividad
    }
}
