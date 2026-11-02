package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val avatarColor: Long = 0xFF0284C7,
    val avatarUri: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val ipAddress: String = "",
    val port: Int = 8888,
    val isHub: Boolean = false,
    val statusMessage: String = "Home",
    val isDnd: Boolean = false
)

@Entity(
    tableName = "chats",
    indices = [Index(value = ["lastMessageTimestamp"])]
)
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val title: String,
    val isGroup: Boolean = false,
    val avatarColor: Long = 0xFF0D9488,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["timestamp"]),
        Index(value = ["deliveryStatus"])
    ]
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: String = "TEXT", // TEXT, VOICE, IMAGE, FILE, BROADCAST, CALL_EVENT
    val mediaUri: String? = null,
    val mediaDurationMs: Long = 0L,
    val fileSize: Long = 0L,
    val fileName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "SENT", // PENDING, SENT, DELIVERED, READ
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val reactions: String = "" // format: "❤️:id1,id2|👍:id3"
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val callId: String,
    val callerId: String,
    val callerName: String,
    val receiverId: String,
    val receiverName: String,
    val isVideo: Boolean = false,
    val isIncoming: Boolean = true,
    val isMissed: Boolean = false,
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "shared_items")
data class SharedItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val category: String = "Grocery", // Grocery, Chore, Note, ToDo
    val addedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)
