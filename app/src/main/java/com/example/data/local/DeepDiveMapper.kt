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
            contentType = "deep_dive",
            hook = domain.hook,
            body = domain.body,
            tagsJson = adapter.toJson(domain.tags),
            sourceFile = domain.sourceFile,
            sourceExcerpt = domain.sourceExcerpt,
            topic = domain.topic,
            subtopic = domain.subtopic,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = java.time.Instant.now().toString(),
            status = "active",
            flagNote = null,
            aiReview = null,
            timesShown = domain.timesShown,
            lastShown = domain.lastShown?.toString(),
            sourceFlag = null
        )
    }

    fun toDomain(entity: DeepDiveCardEntity): DeepDiveCard {
        return DeepDiveCard(
            id = entity.id,
            hook = entity.hook,
            body = entity.body,
            tags = adapter.fromJson(entity.tagsJson) ?: emptyList(),
            sourceExcerpt = entity.sourceExcerpt,
            topic = entity.topic,
            subtopic = entity.subtopic,
            sourceFile = entity.sourceFile,
            dwellTimeMs = 0L,
            timesShown = entity.timesShown,
            lastShown = entity.lastShown?.toLongOrNull()
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
