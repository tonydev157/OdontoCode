package com.tonymen.odontocode.data

data class Diagnosis(
    val id: String = "",              // Unique ID of the diagnosis
    val name: String = "",            // Name of the diagnosis
    val description: String = "",     // Detailed description of the diagnosis
    var category: String = "",        // Category or type of diagnosis
    val code: String = ""             // CIE-10 code
) {
    // Constructor vacío requerido por Firestore
    constructor() : this("", "", "", "", "")
}
