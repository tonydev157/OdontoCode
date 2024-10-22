package com.tonymen.odontocode.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.tonymen.odontocode.data.Note
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import com.tonymen.odontocode.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.util.*

class NotesActivity : ComponentActivity() {
    private lateinit var createNoteLauncher: ActivityResultLauncher<Intent>
    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear el launcher para la actividad de crear/editar notas
        createNoteLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Actualizar las notas y sincronizar
                viewModel.refreshNotes()
            }
        }

        setContent {
            OdontoCodeTheme {
                NotesScreen(
                    createNoteLauncher = createNoteLauncher,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refrescar las notas cuando se reanude la actividad para mantener la sincronización
        viewModel.refreshNotes()
    }

    override fun onBackPressed() {
        if (viewModel.hasSelectedNotes()) {
            viewModel.clearSelectedNotes()  // Limpiar la selección si hay elementos seleccionados
        } else {
            super.onBackPressed()  // Salir de la actividad si no hay elementos seleccionados
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    createNoteLauncher: ActivityResultLauncher<Intent>,
    viewModel: NotesViewModel
) {
    val notesState by viewModel.notesState.collectAsState()
    val selectedNotes = remember { mutableStateListOf<String>() }
    var confirmDeleteVisible by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Notas", color = MaterialTheme.colorScheme.onPrimary) },
                actions = {
                    if (confirmDeleteVisible) {
                        IconButton(onClick = {
                            showDeleteConfirmationDialog = true
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar Nota", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedNotes.clear()
                    val intent = Intent(context, NoteDetailActivity::class.java)
                    createNoteLauncher.launch(intent)
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva Nota", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Barra de búsqueda
            SearchBar(searchQuery = searchQuery, onSearchQueryChanged = {
                searchQuery = it
            })

            Spacer(modifier = Modifier.height(16.dp))

            // Filtrar notas basadas en la búsqueda
            val filteredNotes = if (searchQuery.isEmpty()) {
                notesState
            } else {
                notesState.filter {
                    it.nameNote.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true)
                }
            }

            // Contenido de las notas
            NotesContent(
                notesList = filteredNotes,
                selectedNotes = selectedNotes,
                onNoteClick = { note ->
                    if (selectedNotes.isEmpty()) {
                        val intent = Intent(context, NoteDetailActivity::class.java).apply {
                            putExtra("noteId", note.id)
                            putExtra("nameNote", note.nameNote)
                            putExtra("content", note.content)
                            putExtra("dateCreated", note.dateCreated)
                        }
                        createNoteLauncher.launch(intent)
                    } else {
                        if (selectedNotes.contains(note.id)) {
                            selectedNotes.remove(note.id)
                        } else {
                            selectedNotes.add(note.id)
                        }
                    }
                    confirmDeleteVisible = selectedNotes.isNotEmpty()
                },
                onNoteLongClick = { note ->
                    if (!selectedNotes.contains(note.id)) {
                        selectedNotes.add(note.id)
                    }
                    confirmDeleteVisible = selectedNotes.isNotEmpty()
                },
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }

    if (showDeleteConfirmationDialog) {
        confirmDelete(
            selectedNotes = selectedNotes,
            viewModel = viewModel,
            onDismiss = {
                showDeleteConfirmationDialog = false
                selectedNotes.clear()
                confirmDeleteVisible = false
                viewModel.refreshNotes()
            }
        )
    }
}

@Composable
fun SearchBar(searchQuery: String, onSearchQueryChanged: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar notas",
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
fun confirmDelete(selectedNotes: List<String>, viewModel: NotesViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(onClick = {
                selectedNotes.forEach { noteId ->
                    viewModel.deleteNoteById(noteId)
                }
                viewModel.refreshNotes()
                onDismiss()
            }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancelar")
            }
        },
        title = { Text("Confirmar eliminación") },
        text = { Text("¿Está seguro que desea eliminar las notas seleccionadas?") }
    )
}

@Composable
fun NotesContent(
    notesList: List<Note>,
    selectedNotes: List<String>,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedNotesList = notesList.sortedByDescending { it.lastModified }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = sortedNotesList, key = { it.id }) { note ->
            val isSelected = selectedNotes.contains(note.id)

            NoteCard(
                note = note,
                isSelected = isSelected,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) }
            )
        }
    }
}

@Composable
fun NoteCard(note: Note, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface

    val today = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }
    val lastModifiedDate = remember(note.lastModified) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(note.lastModified))
    }

    val formattedLastModified = if (today == lastModifiedDate) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(note.lastModified))
    } else {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(note.lastModified))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() }
                )
            }
            .background(backgroundColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = note.nameNote,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,  // Limita la vista previa del contenido a una sola línea
                overflow = TextOverflow.Ellipsis  // Si el contenido es más largo, muestra "..."
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedLastModified,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

