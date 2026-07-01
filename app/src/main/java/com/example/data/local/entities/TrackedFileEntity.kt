package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_files")
data class TrackedFileEntity(
    @PrimaryKey val path: String,
    val lastSha: String,
    val lastIndexedAt: Long
)
