    package com.tonymen.odontocode.data

    data class Area(
        val id: String = "",           // ID generado por Firestore
        val name: String = ""               // Nombre del área
    ) {
        constructor() : this("")      // Constructor vacío para Firestore
    }
