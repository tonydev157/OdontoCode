package com.tonymen.odontocode.data

data class Diagnosis(
    val id: String = "",              // Unique ID of the diagnosis
    val name: String = "",            // Name of the diagnosis
    val cie10diagnosis: String = "",     // Detailed description of the diagnosis
    var area: String = "",        // Category or type of diagnosis
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", "")
}
