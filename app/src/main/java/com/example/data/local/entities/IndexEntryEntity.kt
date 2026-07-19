package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.models.IndexEntry

@Entity(tableName = "index_entries")
data class IndexEntryEntity(
    @PrimaryKey val folder: String,
    val sourceFile: String,
    val sourceSha: String,
    val topic: String,
    val subtopic: String,
    val flashcardCount: Int,
    val deepdiveCount: Int,
    val generatedAt: String
)

fun IndexEntry.toEntity(): IndexEntryEntity {
    return IndexEntryEntity(
        folder = this.folder,
        sourceFile = this.sourceFile,
        sourceSha = this.sourceSha,
        topic = this.topic,
        subtopic = this.subtopic,
        flashcardCount = this.flashcardCount,
        deepdiveCount = this.deepdiveCount,
        generatedAt = this.generatedAt
    )
}
