package com.example.domain.models

import java.time.Instant
import java.util.UUID

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "true_false" or "multiple_choice"
    val question: String,
    val correct_answer: String,
    val options: List<String>,
    val explanation: String,
    val source_file: String = "",
    val source_excerpt: String = "",
    val created_at: String = Instant.now().toString(),
    val updated_at: String = Instant.now().toString(),
    val status: String = "active", // "active", "flagged", "reviewed"
    val flag_note: String? = null,
    val ai_review: String? = null,
    val times_shown: Int = 0,
    val times_correct: Int = 0,
    val last_shown: String? = null,
    val difficulty: String = "medium", // "easy", "medium", "hard"
    val topics: List<String> = emptyList(),
    val source_flag: String? = null
)
