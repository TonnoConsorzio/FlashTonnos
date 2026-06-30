package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val type: String,
    val question: String,
    val correctAnswer: String,
    val optionsJson: String,
    val explanation: String,
    val sourceFile: String,
    val sourceExcerpt: String,
    val createdAt: String,
    val updatedAt: String,
    val status: String,
    val flagNote: String?,
    val aiReview: String?,
    val timesShown: Int,
    val timesCorrect: Int,
    val lastShown: String?,
    val difficulty: String,
    val topicsJson: String
)
