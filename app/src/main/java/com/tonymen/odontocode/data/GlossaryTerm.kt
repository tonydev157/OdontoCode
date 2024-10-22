package com.tonymen.odontocode.data

data class GlossaryTerm(
    val termId: String,           // ID único del término
    val term: String,             // Término odontológico
    val definition: String        // Definición del término
) {
    constructor() : this("", "", "")  // Constructor vacío para Firestore
}
