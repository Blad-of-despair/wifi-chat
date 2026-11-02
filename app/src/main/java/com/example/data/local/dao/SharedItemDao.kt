package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SharedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedItemDao {
    @Query("SELECT * FROM shared_items ORDER BY isCompleted ASC, timestamp DESC")
    fun getAllItemsFlow(): Flow<List<SharedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: SharedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(items: List<SharedItemEntity>)

    @Query("UPDATE shared_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: String, isCompleted: Boolean)

    @Query("DELETE FROM shared_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM shared_items WHERE isCompleted = 1")
    suspend fun clearCompleted()
}
