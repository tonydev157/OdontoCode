package com.tonymen.odontocode.data

data class Category(
    val id: String = "",              // Unique ID of the category
    val name: String = ""             // Name of the category
) {
    // Constructor vacío necesario para Firebase Firestore
    constructor() : this("", "")
}
