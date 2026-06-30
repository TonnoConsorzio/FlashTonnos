package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.entities.FlashcardEntity

@Database(entities = [FlashcardEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
