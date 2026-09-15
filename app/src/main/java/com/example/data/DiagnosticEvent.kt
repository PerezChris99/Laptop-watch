package com.example.data

data class DiagnosticEvent(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val status: String, // "HEALTHY", "RECOVERED", "WARNING"
    val message: String
)
