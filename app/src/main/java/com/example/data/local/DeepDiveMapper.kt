package com.example.data.local

import com.example.data.local.entities.DeepDiveCardEntity
import com.example.data.local.entities.DeepDiveInteractionEntity
import com.example.domain.models.DeepDiveCard
import com.example.domain.models.DeepDiveInteraction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object DeepDiveMapper {
    private val moshi = Moshi.Builder().build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val adapter = moshi.adapter<List<String>>(stringListType)

    fun toEntity(domain: DeepDiveCard): DeepDiveCardEntity {
        return DeepDiveCardEntity(
            id = domain.id,
            contentType = domain.content_type,
            hook = domain.hook,
            body = domain.body,
            tagsJson = adapter.toJson(domain.tags),
            sourceFile = domain.source_file,
            sourceExcerpt = domain.source_excerpt,
            topic = domain.topic,
            subtopic = domain.subtopic,
            createdAt = domain.created_at,
            updatedAt = domain.updated_at,
            status = domain.status,
            flagNote = domain.flag_note,
            aiReview = domain.ai_review,
            timesShown = domain.times_shown,
            lastShown = domain.last_shown,
            sourceFlag = domain.source_flag
        )
    }

    fun toDomain(entity: DeepDiveCardEntity): DeepDiveCard {
        return DeepDiveCard(
            id = entity.id,
            content_type = entity.contentType,
            hook = entity.hook,
            body = entity.body,
            tags = adapter.fromJson(entity.tagsJson) ?: emptyList(),
            source_file = entity.sourceFile,
            source_excerpt = entity.sourceExcerpt,
            topic = entity.topic,
            subtopic = entity.subtopic,
            created_at = entity.createdAt,
            updated_at = entity.updatedAt,
            status = entity.status,
            flag_note = entity.flagNote,
            ai_review = entity.aiReview,
            times_shown = entity.timesShown,
            last_shown = entity.lastShown,
            source_flag = entity.sourceFlag
        )
    }

    fun toEntity(domain: DeepDiveInteraction): DeepDiveInteractionEntity {
        return DeepDiveInteractionEntity(
            id = domain.id,
            cardId = domain.cardId,
            topic = domain.topic,
            subtopic = domain.subtopic,
            tagsJson = adapter.toJson(domain.tags),
            dwellTimeMs = domain.dwellTimeMs,
            timestamp = domain.timestamp,
            explicitFeedback = domain.explicitFeedback
        )
    }

    fun toDomain(entity: DeepDiveInteractionEntity): DeepDiveInteraction {
        return DeepDiveInteraction(
            id = entity.id,
            cardId = entity.cardId,
            topic = entity.topic,
            subtopic = entity.subtopic,
            tags = adapter.fromJson(entity.tagsJson) ?: emptyList(),
            dwellTimeMs = entity.dwellTimeMs,
            timestamp = entity.timestamp,
            explicitFeedback = entity.explicitFeedback
        )
    }
}
