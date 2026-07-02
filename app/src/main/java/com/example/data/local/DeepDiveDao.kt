package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.data.local.entities.DeepDiveCardEntity
import com.example.data.local.entities.DeepDiveInteractionEntity
import com.example.data.local.entities.TrackedFileEntity
import com.example.data.local.entities.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeepDiveDao {
    // Deep Dive Cards
    @Query("SELECT * FROM deep_dive_cards")
    fun getAllCards(): Flow<List<DeepDiveCardEntity>>

    @Query("SELECT * FROM deep_dive_cards WHERE id = :id")
    suspend fun getCardById(id: String): DeepDiveCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: DeepDiveCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCards(cards: List<DeepDiveCardEntity>)

    @Update
    suspend fun updateCard(card: DeepDiveCardEntity)

    @Query("DELETE FROM deep_dive_cards WHERE sourceFile = :sourceFile")
    suspend fun deleteCardsBySourceFile(sourceFile: String)

    @Query("DELETE FROM deep_dive_cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM deep_dive_cards")
    suspend fun clearCards()

    // Interactions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: DeepDiveInteractionEntity)

    @Query("SELECT * FROM deep_dive_interactions ORDER BY timestamp DESC")
    fun getAllInteractionsFlow(): Flow<List<DeepDiveInteractionEntity>>

    @Query("SELECT * FROM deep_dive_interactions")
    suspend fun getAllInteractions(): List<DeepDiveInteractionEntity>

    @Query("SELECT * FROM deep_dive_interactions WHERE timestamp >= :timestamp")
    suspend fun getInteractionsSince(timestamp: Long): List<DeepDiveInteractionEntity>

    @Query("DELETE FROM deep_dive_interactions")
    suspend fun clearInteractions()

    // Tracked Files
    @Query("SELECT * FROM tracked_files WHERE path = :path")
    suspend fun getTrackedFile(path: String): TrackedFileEntity?

    @Query("SELECT * FROM tracked_files")
    suspend fun getAllTrackedFiles(): List<TrackedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedFile(file: TrackedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFlashcards(cards: List<FlashcardEntity>)

    @Transaction
    suspend fun saveGenerationResult(
        trackedFile: TrackedFileEntity,
        cards: List<FlashcardEntity>,
        deepDives: List<DeepDiveCardEntity>
    ) {
        insertTrackedFile(trackedFile)
        insertAllFlashcards(cards)
        insertAllCards(deepDives)
    }

    @Query("DELETE FROM tracked_files")
    suspend fun clearTrackedFiles()
}
