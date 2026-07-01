package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deep_dive_cards")
data class DeepDiveCardEntity(
    @PrimaryKey val id: String,
    val contentType: String,
    val hook: String,
    val body: String,
    val tagsJson: String,
    val sourceFile: String,
    val sourceExcerpt: String,
    val topic: String,
    val subtopic: String,
    val createdAt: String,
    val updatedAt: String,
    val status: String,
    val flagNote: String?,
    val aiReview: String?,
    val timesShown: Int,
    val lastShown: String?,
    val sourceFlag: String?
)
