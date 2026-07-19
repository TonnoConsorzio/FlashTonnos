package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.IndexEntryEntity

@Dao
interface IndexEntryDao {
    @Query("SELECT * FROM index_entries WHERE folder = :folder")
    suspend fun getByFolder(folder: String): IndexEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IndexEntryEntity)

    @Query("DELETE FROM index_entries")
    suspend fun clearAll()
}
