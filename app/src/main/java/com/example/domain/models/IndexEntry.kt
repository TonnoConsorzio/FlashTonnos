package com.example.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IndexEntry(
    val folder: String,
    @Json(name = "source_file") val sourceFile: String,
    @Json(name = "source_sha") val sourceSha: String,
    val topic: String,
    val subtopic: String,
    @Json(name = "flashcard_count") val flashcardCount: Int,
    @Json(name = "deepdive_count") val deepdiveCount: Int,
    @Json(name = "generated_at") val generatedAt: String
)

@JsonClass(generateAdapter = true)
data class FlashTonnosIndex(
    val version: Int,
    @Json(name = "last_updated") val lastUpdated: String,
    @Json(name = "total_flashcards") val totalFlashcards: Int,
    @Json(name = "total_deepdives") val totalDeepdives: Int,
    val entries: List<IndexEntry>
)
