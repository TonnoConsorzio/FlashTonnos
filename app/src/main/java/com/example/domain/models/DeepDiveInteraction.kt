package com.example.domain.models

data class DeepDiveInteraction(
    val id: Int = 0,
    val cardId: String,
    val topic: String,
    val subtopic: String,
    val tags: List<String>,
    val dwellTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val explicitFeedback: Int = 0 // +1 (like), -1 (dislike), 0 (none)
)
