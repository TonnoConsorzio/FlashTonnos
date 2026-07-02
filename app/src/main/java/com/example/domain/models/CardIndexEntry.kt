package com.example.domain.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CardIndexEntry(
    val id: String,
    val type: String, // "true_false", "multiple_choice", or "deep_dive"
    val question: String, // question text or deep_dive hook
    val topic: String,
    val subtopic: String
)
