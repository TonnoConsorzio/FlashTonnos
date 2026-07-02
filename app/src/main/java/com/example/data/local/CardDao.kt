package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM flashcards")
    fun getAllCards(): Flow<List<FlashcardEntity>>
    
    @Query("SELECT * FROM flashcards WHERE status != 'flagged' ORDER BY timesShown ASC, difficulty DESC")
    fun getStudyQueue(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getCardById(id: String): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: FlashcardEntity)

    @Update
    suspend fun update(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE sourceFile = :sourceFile")
    suspend fun deleteCardsBySourceFile(sourceFile: String)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM flashcards")
    suspend fun clearAll()
}
