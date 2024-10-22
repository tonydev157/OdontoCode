package com.tonymen.odontocode.data

data class UserFeedback(
    val userId: String,           // ID del usuario que envía el feedback
    val feedbackType: FeedbackType, // Tipo de feedback: error, sugerencia, etc.
    val message: String,          // Mensaje del feedback
    val dateSubmitted: Long       // Fecha de envío
) {
    constructor() : this("", FeedbackType.OTHER, "", 0L)  // Constructor vacío para Firestore
}

enum class FeedbackType {
    ERROR, SUGGESTION, OTHER
}
