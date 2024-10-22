package com.tonymen.odontocode.data

data class OfflineSync(
    val userId: String,            // ID del usuario
    val lastSyncDate: Long,        // Fecha de la última sincronización
    val pendingUpdates: List<String> // Lista de diagnósticos o notas pendientes por sincronizar
) {
    constructor() : this("", 0L, emptyList())  // Constructor vacío para Firestore
}
