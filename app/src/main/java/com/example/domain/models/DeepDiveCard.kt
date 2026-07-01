package com.example.domain.models

import com.squareup.moshi.JsonClass
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DeepDiveCard(
    val id: String = UUID.randomUUID().toString(),
    val content_type: String = "deep_dive",
    val hook: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val source_file: String = "",
    val source_excerpt: String = "",
    val topic: String = "",
    val subtopic: String = "",
    val created_at: String = Instant.now().toString(),
    val updated_at: String = Instant.now().toString(),
    val status: String = "active", // "active" | "flagged" | "reviewed"
    val flag_note: String? = null,
    val ai_review: String? = null,
    val times_shown: Int = 0,
    val last_shown: String? = null,
    val source_flag: String? = null
)
