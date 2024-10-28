package com.tonymen.odontocode.data

data class User(
    val id: String = "",                // Unique ID of the user (default empty string)
    val name: String = "",              // Name of the user (default empty string)
    val email: String = "",             // Email for authentication (default empty string)
    val userType: UserType = UserType.USER, // Default to USER type
    val ci: String = "",                // Cédula de Identidad (default empty string)
    val approved: Boolean = false,     // Default to false (not approved by default)
    val activeSession: Boolean = false   // Stores active session (device or token ID)
)

enum class UserType {
    ADMIN, USER
}
