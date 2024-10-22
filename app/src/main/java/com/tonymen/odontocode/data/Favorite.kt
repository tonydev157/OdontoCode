package com.tonymen.odontocode.data

data class Favorite(
    val diagnosisId: String = "",       // ID del diagnóstico marcado como favorito
    val userId: String = "",            // ID del usuario que marcó el favorito
    val name: String = "",              // Nombre del diagnóstico
    val code: String = "",              // Código CIE-10 del diagnóstico
    val category: String = "",          // Categoría del diagnóstico
    val description: String = ""        // Descripción del diagnóstico
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", "", "", "")
}
