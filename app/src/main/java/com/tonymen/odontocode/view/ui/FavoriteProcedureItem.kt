package com.tonymen.odontocode.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.Diagnosis
import com.tonymen.odontocode.data.Procedure
import com.tonymen.odontocode.viewmodel.FavoritesViewModel

@Composable
fun FavoriteProcedureItem(favorite: Procedure, viewModel: FavoritesViewModel) {
    val isFavorite by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var areaName by remember { mutableStateOf("Cargando...") }

    val isCurrentlyFavorite = isFavorite.contains(favorite.id)

    // Obtener detalles del diagnóstico desde el caché del ViewModel
    val diagnosisDetails by viewModel.diagnosisDetails.collectAsStateWithLifecycle()
    val diagnosis = diagnosisDetails[favorite.diagnosis]
    val diagnosisName = diagnosis?.name ?: if (favorite.diagnosis == "N/A") "Sin especificar" else "Cargando..."
    val diagnosisCIE10 = diagnosis?.cie10diagnosis ?: if (favorite.diagnosis == "N/A") "Sin especificar" else "Cargando..."

    LaunchedEffect(favorite.diagnosis) {
        if (diagnosis == null && favorite.diagnosis != "N/A") {
            viewModel.loadDiagnosisDetails(favorite.diagnosis)
        }
    }

    LaunchedEffect(favorite.area) {
        FirebaseFirestore.getInstance().collection("areas")
            .document(favorite.area)
            .get()
            .addOnSuccessListener { document ->
                val area = document.getString("name")
                areaName = area ?: "Área no encontrada"
            }
            .addOnFailureListener {
                areaName = "Error al cargar área"
            }
    }

    // UI del elemento de favorito
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
                text = "Diagnóstico: $diagnosisName",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "CIE-10 Diagnóstico: $diagnosisCIE10",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Área: $areaName",
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(onClick = {
                viewModel.toggleFavorite(favorite.id)
            }) {
                Icon(
                    painter = if (isCurrentlyFavorite) painterResource(id = R.drawable.ic_favorite) else painterResource(id = R.drawable.ic_favorite_border),
                    contentDescription = if (isCurrentlyFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                    tint = if (isCurrentlyFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}



