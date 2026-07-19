package com.example.data.local

import com.example.data.local.entities.FlashcardEntity
import com.example.domain.models.Flashcard
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object FlashcardMapper {
    private val moshi = Moshi.Builder().build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(stringListType)

    fun toEntity(domain: Flashcard): FlashcardEntity {
        return FlashcardEntity(
            id = domain.id,
            type = domain.type,
            question = domain.question,
            correctAnswer = domain.correctAnswer,
            optionsJson = adapter.toJson(domain.options),
            explanation = domain.explanation,
            sourceFile = domain.sourceFile,
            sourceExcerpt = domain.sourceExcerpt,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = java.time.Instant.now().toString(),
            status = domain.status,
            flagNote = null,
            aiReview = null,
            timesShown = domain.timesShown,
            timesCorrect = domain.timesCorrect,
            lastShown = domain.lastShown?.toString(),
            difficulty = domain.difficulty,
            topicsJson = adapter.toJson(listOf(domain.topic, domain.subtopic)),
            topic = domain.topic,
            subtopic = domain.subtopic
        )
    }

    fun toDomain(entity: FlashcardEntity): Flashcard {
        return Flashcard(
            id = entity.id,
            type = entity.type,
            question = entity.question,
            correctAnswer = entity.correctAnswer,
            options = adapter.fromJson(entity.optionsJson) ?: emptyList(),
            explanation = entity.explanation,
            sourceFile = entity.sourceFile,
            sourceExcerpt = entity.sourceExcerpt,
            timesShown = entity.timesShown,
            timesCorrect = entity.timesCorrect,
            lastShown = entity.lastShown?.toLongOrNull(),
            status = entity.status,
            difficulty = entity.difficulty,
            topic = entity.topic,
            subtopic = entity.subtopic
        )
    }
}
