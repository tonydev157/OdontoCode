package com.tonymen.odontocode.view

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.Note
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import java.text.SimpleDateFormat
import java.util.*

class NoteDetailActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recuperar los datos del Intent (ID de la nota)
        val noteId = intent.getStringExtra("noteId") ?: ""  // ID de la nota (vacío si es nueva)

        if (noteId.isNotEmpty()) {
            // Recuperar la nota desde Firestore si tiene un ID válido
            firestore.collection("notes").document(noteId).get()
                .addOnSuccessListener { document ->
                    val note = document.toObject(Note::class.java)
                    note?.let {
                        // Si la nota existe, configuramos la UI con los datos recuperados
                        setContent {
                            OdontoCodeTheme {
                                NoteDetailScreen(
                                    noteId = it.id,
                                    title = it.nameNote,
                                    content = it.content,
                                    dateCreated = it.dateCreated,
                                    lastModified = it.lastModified,  // Pasamos la fecha de modificación
                                    onSave = { updatedNote, hasChanges ->
                                        saveNoteAndExit(updatedNote, hasChanges)
                                    },
                                    onDiscard = { finish() }
                                )
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    // Manejar errores en la obtención de la nota
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
        } else {
            // Si es una nueva nota, configuramos la UI para una nota vacía
            setContent {
                OdontoCodeTheme {
                    NoteDetailScreen(
                        noteId = noteId,
                        title = "",
                        content = "",
                        dateCreated = System.currentTimeMillis(),
                        lastModified = System.currentTimeMillis(),
                        onSave = { newNote, hasChanges ->
                            saveNoteAndExit(newNote, hasChanges)
                        },
                        onDiscard = { setResult(Activity.RESULT_CANCELED); finish() }
                    )
                }
            }
        }
    }

    private fun saveNoteAndExit(note: Note, hasChanges: Boolean) {
        val noteRef = if (note.id.isEmpty()) {
            firestore.collection("notes").document() // Crear un nuevo documento si no tiene ID
        } else {
            firestore.collection("notes").document(note.id) // Actualizar documento existente
        }

        // Si la nota es nueva, obtenemos el ID generado por Firestore
        val updatedNote = if (note.id.isEmpty()) {
            note.copy(id = noteRef.id, lastModified = note.dateCreated)  // Inicializamos lastModified con dateCreated
        } else if (hasChanges) {
            note.copy(lastModified = System.currentTimeMillis())  // Si hubo cambios, actualizamos lastModified
        } else {
            note // Si no hubo cambios, dejamos las fechas como están
        }

        noteRef.set(updatedNote)
            .addOnSuccessListener {
                setResult(Activity.RESULT_OK)
                finish() // Cerrar la actividad después de guardar
            }
            .addOnFailureListener {
                setResult(Activity.RESULT_CANCELED)
                finish() // Cerrar la actividad si hubo un error
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    title: String,
    content: String,
    dateCreated: Long,
    lastModified: Long,
    onSave: (Note, Boolean) -> Unit,
    onDiscard: () -> Unit
) {
    var nameNote by remember { mutableStateOf(TextFieldValue(title)) }
    var noteContent by remember { mutableStateOf(TextFieldValue(content)) }
    val originalNameNote = remember { title }
    val originalContent = remember { content }

    // Formateamos las fechas de creación y última modificación
    val formattedDateCreated = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateCreated)) }
    val formattedDateModified = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary // Fondo adaptado al color primario del tema
                ),
                navigationIcon = {
                    // Botón de cerrar (X)
                    IconButton(onClick = {
                        onDiscard() // Se descartan los cambios y se sale sin guardar
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Descartar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    // Botón de guardar (Visto)
                    IconButton(onClick = {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        val hasChanges = nameNote.text != originalNameNote || noteContent.text != originalContent
                        val note = if (hasChanges) {
                            Note(
                                id = noteId,
                                userId = userId,
                                nameNote = nameNote.text,
                                content = noteContent.text,
                                dateCreated = dateCreated,
                                lastModified = System.currentTimeMillis() // Actualizamos solo si hubo cambios
                            )
                        } else {
                            Note(
                                id = noteId,
                                userId = userId,
                                nameNote = nameNote.text,
                                content = noteContent.text,
                                dateCreated = dateCreated,
                                lastModified = lastModified // Mantenemos la fecha de modificación
                            )
                        }
                        onSave(note, hasChanges)
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background, // Adaptación al color de fondo del tema
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Fondo adaptado
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background) // Fondo de la columna adaptado al tema
                .padding(16.dp)
        ) {
            // Campo de título con hint
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)) { // Fondo adaptado al tema
                BasicTextField(
                    value = nameNote,
                    onValueChange = { nameNote = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground // Color adaptado al tema
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), // Color del cursor adaptado
                    decorationBox = { innerTextField ->
                        if (nameNote.text.isEmpty()) {
                            Text(
                                text = "Título",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) // Hint adaptado
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Información debajo del título (fecha y caracteres)
            Text(
                text = "$formattedDateCreated | ${noteContent.text.length} caracteres",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), // Adaptación de color al tema
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Mostrar la fecha de última modificación
            Text(
                text = "Última modificación: $formattedDateModified",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), // Adaptación de color al tema
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), thickness = 1.dp) // Línea divisoria adaptada

            Spacer(modifier = Modifier.height(8.dp))

            // Campo de contenido con hint
            Box(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)) { // Fondo adaptado al tema
                BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground // Color del texto adaptado al tema
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), // Cursor adaptado al color primario
                    decorationBox = { innerTextField ->
                        if (noteContent.text.isEmpty()) {
                            Text(
                                text = "Comienza a escribir...",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) // Hint adaptado al tema
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
