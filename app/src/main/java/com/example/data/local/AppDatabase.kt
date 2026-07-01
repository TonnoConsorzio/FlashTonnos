package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.entities.FlashcardEntity
import com.example.data.local.entities.DeepDiveCardEntity
import com.example.data.local.entities.DeepDiveInteractionEntity
import com.example.data.local.entities.TrackedFileEntity

@Database(
    entities = [
        FlashcardEntity::class,
        DeepDiveCardEntity::class,
        DeepDiveInteractionEntity::class,
        TrackedFileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deepDiveDao(): DeepDiveDao
}

