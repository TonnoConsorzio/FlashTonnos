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
            correctAnswer = domain.correct_answer,
            optionsJson = adapter.toJson(domain.options),
            explanation = domain.explanation,
            sourceFile = domain.source_file,
            sourceExcerpt = domain.source_excerpt,
            createdAt = domain.created_at,
            updatedAt = domain.updated_at,
            status = domain.status,
            flagNote = domain.flag_note ?: domain.source_flag,
            aiReview = domain.ai_review,
            timesShown = domain.times_shown,
            timesCorrect = domain.times_correct,
            lastShown = domain.last_shown,
            difficulty = domain.difficulty,
            topicsJson = adapter.toJson(domain.topics)
        )
    }

    fun toDomain(entity: FlashcardEntity): Flashcard {
        return Flashcard(
            id = entity.id,
            type = entity.type,
            question = entity.question,
            correct_answer = entity.correctAnswer,
            options = adapter.fromJson(entity.optionsJson) ?: emptyList(),
            explanation = entity.explanation,
            source_file = entity.sourceFile,
            source_excerpt = entity.sourceExcerpt,
            created_at = entity.createdAt,
            updated_at = entity.updatedAt,
            status = entity.status,
            flag_note = entity.flagNote,
            ai_review = entity.aiReview,
            times_shown = entity.timesShown,
            times_correct = entity.timesCorrect,
            last_shown = entity.lastShown,
            difficulty = entity.difficulty,
            topics = adapter.fromJson(entity.topicsJson) ?: emptyList()
        )
    }
}
