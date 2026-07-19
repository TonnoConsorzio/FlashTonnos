package com.example.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Flashcard(
    val id: String,
    val type: String,           // "true_false" | "multiple_choice"
    val question: String,
    @Json(name = "correct_answer") val correctAnswer: String,
    val options: List<String>,
    val explanation: String,
    val difficulty: String,     // "easy" | "medium" | "hard"
    @Json(name = "source_excerpt") val sourceExcerpt: String,
    val topic: String = "",          // da meta.json della cartella
    val subtopic: String = "",       // da meta.json della cartella
    @Json(name = "source_file") val sourceFile: String = "",     // da meta.json della cartella
    // Campi locali (non nel JSON, gestiti da Room)
    val timesShown: Int = 0,
    val timesCorrect: Int = 0,
    val lastShown: Long? = null,
    val status: String = "active"   // "active" | "flagged"
)
