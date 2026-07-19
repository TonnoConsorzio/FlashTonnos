package com.example.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeepDiveCard(
    val id: String,
    val hook: String,
    val body: String,
    val tags: List<String>,
    @Json(name = "source_excerpt") val sourceExcerpt: String,
    val topic: String = "",
    val subtopic: String = "",
    @Json(name = "source_file") val sourceFile: String = "",
    // Campi locali
    val dwellTimeMs: Long = 0,
    val timesShown: Int = 0,
    val lastShown: Long? = null
)
