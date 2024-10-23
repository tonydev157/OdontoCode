package com.tonymen.odontocode.data

data class Favorite(
    val procedureIds: List<String> = emptyList() // Constructor por defecto
) {
    // Este constructor sin argumentos es requerido por Firebase
}
