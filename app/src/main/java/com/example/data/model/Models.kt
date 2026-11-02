package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetworkPayload(
    val type: String, // HANDSHAKE, PRESENCE, CHAT_MESSAGE, MESSAGE_ACK, REACTION, BROADCAST, CALL_SIGNAL, TYPING, SHARED_ITEM_SYNC
    val senderId: String,
    val senderName: String,
    val recipientId: String? = null, // null means whole family or hub relay
    val chatId: String? = null,
    val payloadJson: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class HandshakeData(
    val pinHash: String,
    val userName: String,
    val avatarColor: Long,
    val statusMessage: String = "Home",
    val isDnd: Boolean = false,
    val isHub: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PresenceData(
    val userId: String,
    val userName: String,
    val avatarColor: Long,
    val isOnline: Boolean,
    val statusMessage: String = "Home",
    val isDnd: Boolean = false,
    val ipAddress: String = "",
    val port: Int = 8888,
    val isHub: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ChatMessageData(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: String = "TEXT", // TEXT, VOICE, IMAGE, FILE, BROADCAST, CALL_EVENT
    val mediaBase64: String? = null, // LAN transfer
    val mediaDurationMs: Long = 0L,
    val fileSize: Long = 0L,
    val fileName: String? = null,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class MessageAckData(
    val messageId: String,
    val chatId: String,
    val status: String, // DELIVERED, READ
    val readerId: String
)

@JsonClass(generateAdapter = true)
data class ReactionData(
    val messageId: String,
    val chatId: String,
    val emoji: String,
    val userId: String,
    val action: String // ADD, REMOVE
)

@JsonClass(generateAdapter = true)
data class BroadcastData(
    val title: String,
    val message: String,
    val senderName: String,
    val urgency: String = "NORMAL" // NORMAL, HIGH (loud ring)
)

@JsonClass(generateAdapter = true)
data class CallSignalData(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val targetId: String,
    val isVideo: Boolean,
    val signalType: String, // OFFER, ANSWER, CANDIDATE, END, REJECT, BUSY
    val sdp: String = "",
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class TypingData(
    val chatId: String,
    val userId: String,
    val userName: String,
    val isTyping: Boolean
)

@JsonClass(generateAdapter = true)
data class SharedItemDto(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val category: String,
    val addedBy: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class SharedItemSyncData(
    val items: List<SharedItemDto>
)
