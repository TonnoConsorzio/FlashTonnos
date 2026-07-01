package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deep_dive_interactions")
data class DeepDiveInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardId: String,
    val topic: String,
    val subtopic: String,
    val tagsJson: String,
    val dwellTimeMs: Long,
    val timestamp: Long,
    val explicitFeedback: Int // +1, -1, 0
)
