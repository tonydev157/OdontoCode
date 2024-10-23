package com.tonymen.odontocode.data

data class Procedure(
    val id: String = "",                       // ID generado por Firestore
    val procedure: String,                     // Nombre del procedimiento
    val cie10procedure: String,                // CIE-10 del procedimiento
    val diagnosis: String,                     // Diagnóstico asociado
    val cie10diagnosis: String,                // CIE-10 del diagnóstico
    val area: String                           // Área asociada
) {
    constructor() : this("", "", "", "", "", "") // Constructor vacío para Firestore
}
