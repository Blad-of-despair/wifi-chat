package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE deliveryStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query("UPDATE messages SET deliveryStatus = 'READ' WHERE chatId = :chatId AND deliveryStatus != 'READ'")
    suspend fun markAllAsReadInChat(chatId: String)

    @Query("UPDATE messages SET isEdited = 1, content = :newContent WHERE messageId = :messageId")
    suspend fun editMessage(messageId: String, newContent: String)

    @Query("UPDATE messages SET isDeleted = 1, content = 'This message was deleted' WHERE messageId = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    @Query("UPDATE messages SET reactions = :reactions WHERE messageId = :messageId")
    suspend fun updateReactions(messageId: String, reactions: String)

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)
}
