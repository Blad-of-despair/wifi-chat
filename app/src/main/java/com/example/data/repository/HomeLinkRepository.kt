package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.HomeLinkApp
import com.example.audio.VoicePlayer
import com.example.audio.VoiceRecorder
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.CallLogEntity
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SharedItemEntity
import com.example.data.local.entity.UserEntity
import com.example.data.model.BroadcastData
import com.example.data.model.CallSignalData
import com.example.data.model.ChatMessageData
import com.example.data.model.MessageAckData
import com.example.data.model.NetworkPayload
import com.example.data.model.PresenceData
import com.example.data.model.ReactionData
import com.example.data.model.SharedItemDto
import com.example.data.model.SharedItemSyncData
import com.example.data.model.TypingData
import com.example.network.HomeLinkClient
import com.example.network.HomeLinkCrypto
import com.example.network.HomeLinkServer
import com.example.network.NetworkUtils
import com.example.network.NsdDiscoveryManager
import com.example.service.HomeLinkForegroundService
import com.example.webrtc.WebRtcManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class HomeLinkRepository(private val context: Context) {
    private val tag = "HomeLinkRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val db: AppDatabase = (context.applicationContext as HomeLinkApp).database
    val prefs: PreferencesManager = (context.applicationContext as HomeLinkApp).preferencesManager

    val userDao = db.userDao()
    val chatDao = db.chatDao()
    val messageDao = db.messageDao()
    val callLogDao = db.callLogDao()
    val sharedItemDao = db.sharedItemDao()

    val voiceRecorder = VoiceRecorder(context)
    val voicePlayer = VoicePlayer(context)

    private val moshi = Moshi.Builder().build()
    private val payloadAdapter = moshi.adapter(NetworkPayload::class.java)
    private val chatMessageAdapter = moshi.adapter(ChatMessageData::class.java)
    private val ackAdapter = moshi.adapter(MessageAckData::class.java)
    private val reactionAdapter = moshi.adapter(ReactionData::class.java)
    private val broadcastAdapter = moshi.adapter(BroadcastData::class.java)
    private val callSignalAdapter = moshi.adapter(CallSignalData::class.java)
    private val presenceAdapter = moshi.adapter(PresenceData::class.java)
    private val typingAdapter = moshi.adapter(TypingData::class.java)
    private val sharedSyncAdapter = moshi.adapter(SharedItemSyncData::class.java)

    private val nsdManager = NsdDiscoveryManager(context)
    private var server: HomeLinkServer? = null
    private var client: HomeLinkClient? = null

    val webRtcManager = WebRtcManager(context) { signal ->
        scope.launch {
            val payload = NetworkPayload(
                type = "CALL_SIGNAL",
                senderId = currentUserId,
                senderName = currentUserName,
                recipientId = signal.targetId,
                payloadJson = callSignalAdapter.toJson(signal)
            )
            sendPayload(payload)
        }
    }

    var currentUserId: String = ""
    var currentUserName: String = ""
    var currentPin: String = "1234"
    var isHub: Boolean = false

    private val _isHubRunning = MutableStateFlow(false)
    val isHubRunning: StateFlow<Boolean> = _isHubRunning.asStateFlow()

    private val _isConnectedToHub = MutableStateFlow(false)
    val isConnectedToHub: StateFlow<Boolean> = _isConnectedToHub.asStateFlow()

    private val _activeHubAddress = MutableStateFlow<String?>(null)
    val activeHubAddress: StateFlow<String?> = _activeHubAddress.asStateFlow()

    private val _activeBroadcast = MutableStateFlow<BroadcastData?>(null)
    val activeBroadcast: StateFlow<BroadcastData?> = _activeBroadcast.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, String>>(emptyMap()) // chatId -> userName
    val typingUsers: StateFlow<Map<String, String>> = _typingUsers.asStateFlow()

    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChatsFlow()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogsFlow()
    val allSharedItems: Flow<List<SharedItemEntity>> = sharedItemDao.getAllItemsFlow()

    init {
        scope.launch {
            initDefaults()
            startNetworkStack()
            startOfflineQueueWorker()
        }
    }

    private suspend fun initDefaults() {
        currentUserId = prefs.getUserId()
        currentUserName = prefs.userNameFlow.first()
        currentPin = prefs.familyPinFlow.first()
        isHub = prefs.isHubFlow.first()

        // Create default "Whole Family" group chat if not exists
        val wholeFamilyChat = chatDao.getChatById("whole_family")
        if (wholeFamilyChat == null) {
            chatDao.insertOrUpdate(
                ChatEntity(
                    chatId = "whole_family",
                    title = "Whole Family",
                    isGroup = true,
                    avatarColor = 0xFF0284C7,
                    lastMessageText = "Welcome to HomeLink private family network!",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    isPinned = true
                )
            )
        }
    }

    fun startNetworkStack() {
        scope.launch {
            val pinHash = HomeLinkCrypto.hashPin(currentPin)
            NetworkUtils.acquireMulticastLock(context)
            HomeLinkForegroundService.start(context)

            if (isHub) {
                startHubMode(pinHash)
            } else {
                startClientMode(pinHash)
            }
        }
    }

    private fun startHubMode(pinHash: String) {
        val localIp = NetworkUtils.getLocalIpAddress()
        _activeHubAddress.value = "$localIp:8888"

        server?.stop()
        server = HomeLinkServer(
            expectedPinHash = pinHash,
            hubUserId = currentUserId,
            hubUserName = currentUserName,
            port = 8888,
            onMessageReceived = { payload ->
                scope.launch { processIncomingPayload(payload) }
            }
        ).apply {
            start()
        }
        _isHubRunning.value = true
        _isConnectedToHub.value = true

        // Register NSD mDNS
        nsdManager.registerHub(
            serviceName = "HomeLinkHub",
            port = 8888,
            attributes = mapOf(
                "pinHash" to pinHash,
                "hubName" to currentUserName,
                "hubId" to currentUserId
            )
        )

        // Connect local client to own server for uniform pipeline
        client?.disconnect()
        client = HomeLinkClient(
            userId = currentUserId,
            userName = currentUserName,
            avatarColor = 0xFF0284C7,
            pinHash = pinHash,
            onPayloadReceived = { payload ->
                scope.launch { processIncomingPayload(payload) }
            }
        ).apply {
            connect("127.0.0.1", 8888)
        }
    }

    private fun startClientMode(pinHash: String) {
        server?.stop()
        _isHubRunning.value = false

        // Listen for NSD discovery
        nsdManager.startDiscovery { serviceName, host, port, attributes ->
            Log.d(tag, "Discovered hub: $serviceName at $host:$port")
            val remotePinHash = attributes["pinHash"]
            if (remotePinHash != null && remotePinHash != pinHash) {
                Log.w(tag, "Discovered Hub has different PIN hash!")
            }
            _activeHubAddress.value = "$host:$port"
            connectClientToHub(host, port, pinHash)
        }

        // Also check if manual hub IP is configured
        scope.launch {
            val manualIp = prefs.manualHubIpFlow.first()
            val manualPortStr = prefs.manualHubPortFlow.first()
            if (manualIp.isNotBlank()) {
                val manualPort = manualPortStr.toIntOrNull() ?: 8888
                connectClientToHub(manualIp, manualPort, pinHash)
            }
        }
    }

    private fun connectClientToHub(host: String, port: Int, pinHash: String) {
        client?.disconnect()
        client = HomeLinkClient(
            userId = currentUserId,
            userName = currentUserName,
            avatarColor = 0xFF0284C7,
            pinHash = pinHash,
            onPayloadReceived = { payload ->
                scope.launch { processIncomingPayload(payload) }
            }
        ).apply {
            connect(host, port)
        }
        scope.launch {
            client?.isConnected?.collect { connected ->
                _isConnectedToHub.value = connected
            }
        }
    }

    private suspend fun processIncomingPayload(payload: NetworkPayload) {
        // Skip own messages reflected from server broadcast
        if (payload.senderId == currentUserId && payload.type != "MESSAGE_ACK") return

        when (payload.type) {
            "PRESENCE" -> {
                val presence = try { presenceAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (presence != null && presence.userId != currentUserId) {
                    val existing = userDao.getUserById(presence.userId)
                    val updated = UserEntity(
                        userId = presence.userId,
                        name = presence.userName.ifBlank { existing?.name ?: "Family Member" },
                        avatarColor = if (presence.avatarColor != 0L) presence.avatarColor else existing?.avatarColor ?: 0xFF0284C7,
                        isOnline = presence.isOnline,
                        lastSeen = presence.lastSeen,
                        ipAddress = presence.ipAddress,
                        port = presence.port,
                        isHub = presence.isHub,
                        statusMessage = presence.statusMessage,
                        isDnd = presence.isDnd
                    )
                    userDao.insertOrUpdate(updated)

                    // Also ensure a direct chat exists for this user
                    val directChatId = "direct_${presence.userId}"
                    if (chatDao.getChatById(directChatId) == null) {
                        chatDao.insertOrUpdate(
                            ChatEntity(
                                chatId = directChatId,
                                title = updated.name,
                                isGroup = false,
                                avatarColor = updated.avatarColor,
                                lastMessageText = "Online on local Wi-Fi",
                                lastMessageTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            "CHAT_MESSAGE" -> {
                val msg = try { chatMessageAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (msg != null) {
                    var localMediaUri = msg.mediaBase64
                    // If image or voice is transmitted as base64, save to cache file
                    if (!msg.mediaBase64.isNullOrEmpty()) {
                        localMediaUri = saveBase64MediaToFile(msg.mediaBase64, msg.type, msg.messageId)
                    }

                    val entity = MessageEntity(
                        messageId = msg.messageId,
                        chatId = msg.chatId,
                        senderId = msg.senderId,
                        senderName = msg.senderName,
                        content = msg.content,
                        type = msg.type,
                        mediaUri = localMediaUri,
                        mediaDurationMs = msg.mediaDurationMs,
                        fileSize = msg.fileSize,
                        fileName = msg.fileName,
                        timestamp = msg.timestamp,
                        deliveryStatus = "DELIVERED",
                        replyToMessageId = msg.replyToMessageId,
                        replyToContent = msg.replyToContent
                    )
                    messageDao.insertOrUpdate(entity)

                    // Update chat last message
                    val preview = when (msg.type) {
                        "VOICE" -> "🎤 Voice message (${msg.mediaDurationMs / 1000}s)"
                        "IMAGE" -> "📷 Photo"
                        "FILE" -> "📎 ${msg.fileName ?: "File"}"
                        else -> msg.content
                    }
                    chatDao.updateLastMessage(msg.chatId, preview, msg.timestamp)
                    chatDao.incrementUnread(msg.chatId)

                    // Send delivery ACK back to sender
                    val ackPayload = NetworkPayload(
                        type = "MESSAGE_ACK",
                        senderId = currentUserId,
                        senderName = currentUserName,
                        recipientId = msg.senderId,
                        chatId = msg.chatId,
                        payloadJson = ackAdapter.toJson(
                            MessageAckData(
                                messageId = msg.messageId,
                                chatId = msg.chatId,
                                status = "DELIVERED",
                                readerId = currentUserId
                            )
                        )
                    )
                    sendPayload(ackPayload)
                }
            }

            "MESSAGE_ACK" -> {
                val ack = try { ackAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (ack != null) {
                    messageDao.updateDeliveryStatus(ack.messageId, ack.status)
                }
            }

            "REACTION" -> {
                val reaction = try { reactionAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (reaction != null) {
                    val msg = messageDao.getMessageById(reaction.messageId)
                    if (msg != null) {
                        val updatedReactions = toggleReactionString(msg.reactions, reaction.emoji, reaction.userId)
                        messageDao.updateReactions(reaction.messageId, updatedReactions)
                    }
                }
            }

            "TYPING" -> {
                val typing = try { typingAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (typing != null) {
                    val map = _typingUsers.value.toMutableMap()
                    if (typing.isTyping) {
                        map[typing.chatId] = typing.userName
                    } else {
                        map.remove(typing.chatId)
                    }
                    _typingUsers.value = map
                }
            }

            "BROADCAST" -> {
                val b = try { broadcastAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (b != null) {
                    _activeBroadcast.value = b
                    val entity = MessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        chatId = "whole_family",
                        senderId = payload.senderId,
                        senderName = payload.senderName,
                        content = "📢 [ANNOUNCEMENT] ${b.title}: ${b.message}",
                        type = "BROADCAST",
                        timestamp = payload.timestamp,
                        deliveryStatus = "READ"
                    )
                    messageDao.insertOrUpdate(entity)
                    chatDao.updateLastMessage("whole_family", "📢 ${b.title}", payload.timestamp)
                }
            }

            "CALL_SIGNAL" -> {
                val signal = try { callSignalAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (signal != null) {
                    webRtcManager.handleIncomingSignal(signal)
                }
            }

            "SHARED_ITEM_SYNC" -> {
                val sync = try { sharedSyncAdapter.fromJson(payload.payloadJson) } catch (_: Exception) { null }
                if (sync != null) {
                    val entities = sync.items.map {
                        SharedItemEntity(
                            id = it.id,
                            title = it.title,
                            isCompleted = it.isCompleted,
                            category = it.category,
                            addedBy = it.addedBy,
                            timestamp = it.timestamp
                        )
                    }
                    sharedItemDao.insertOrUpdateAll(entities)
                }
            }
        }
    }

    private fun toggleReactionString(existing: String, emoji: String, userId: String): String {
        // format: "❤️:u1,u2|👍:u3"
        val parts = if (existing.isBlank()) mutableListOf() else existing.split("|").toMutableList()
        val index = parts.indexOfFirst { it.startsWith("$emoji:") }
        if (index >= 0) {
            val users = parts[index].substringAfter(":").split(",").filter { it.isNotBlank() }.toMutableList()
            if (users.contains(userId)) {
                users.remove(userId)
            } else {
                users.add(userId)
            }
            if (users.isEmpty()) {
                parts.removeAt(index)
            } else {
                parts[index] = "$emoji:${users.joinToString(",")}"
            }
        } else {
            parts.add("$emoji:$userId")
        }
        return parts.joinToString("|")
    }

    private fun saveBase64MediaToFile(base64: String, type: String, id: String): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val extension = if (type == "VOICE") ".m4a" else ".jpg"
            val file = File(context.cacheDir, "received_${id}$extension")
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun sendTextMessage(chatId: String, recipientId: String?, text: String, replyTo: MessageEntity? = null) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val entity = MessageEntity(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = text,
            type = "TEXT",
            timestamp = timestamp,
            deliveryStatus = "PENDING",
            replyToMessageId = replyTo?.messageId,
            replyToContent = replyTo?.content?.take(60)
        )
        messageDao.insertOrUpdate(entity)
        chatDao.updateLastMessage(chatId, text, timestamp)

        val chatData = ChatMessageData(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = text,
            type = "TEXT",
            replyToMessageId = replyTo?.messageId,
            replyToContent = replyTo?.content?.take(60),
            timestamp = timestamp
        )
        val payload = NetworkPayload(
            type = "CHAT_MESSAGE",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = recipientId,
            chatId = chatId,
            payloadJson = chatMessageAdapter.toJson(chatData),
            timestamp = timestamp
        )

        val sent = sendPayload(payload)
        if (sent) {
            messageDao.updateDeliveryStatus(msgId, "SENT")
        }
    }

    suspend fun sendVoiceMessage(chatId: String, recipientId: String?, file: File, durationMs: Long) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val entity = MessageEntity(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = "🎤 Voice message",
            type = "VOICE",
            mediaUri = file.absolutePath,
            mediaDurationMs = durationMs,
            fileSize = file.length(),
            timestamp = timestamp,
            deliveryStatus = "PENDING"
        )
        messageDao.insertOrUpdate(entity)
        chatDao.updateLastMessage(chatId, "🎤 Voice message", timestamp)

        val chatData = ChatMessageData(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = "🎤 Voice message",
            type = "VOICE",
            mediaBase64 = base64,
            mediaDurationMs = durationMs,
            fileSize = file.length(),
            timestamp = timestamp
        )
        val payload = NetworkPayload(
            type = "CHAT_MESSAGE",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = recipientId,
            chatId = chatId,
            payloadJson = chatMessageAdapter.toJson(chatData),
            timestamp = timestamp
        )

        val sent = sendPayload(payload)
        if (sent) {
            messageDao.updateDeliveryStatus(msgId, "SENT")
        }
    }

    suspend fun sendImageMessage(chatId: String, recipientId: String?, imageUri: Uri) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Compress image to reasonable LAN size
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val baos = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val bytes = baos.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val entity = MessageEntity(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = "📷 Photo",
            type = "IMAGE",
            mediaUri = imageUri.toString(),
            fileSize = bytes.size.toLong(),
            timestamp = timestamp,
            deliveryStatus = "PENDING"
        )
        messageDao.insertOrUpdate(entity)
        chatDao.updateLastMessage(chatId, "📷 Photo", timestamp)

        val chatData = ChatMessageData(
            messageId = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = currentUserName,
            content = "📷 Photo",
            type = "IMAGE",
            mediaBase64 = base64,
            fileSize = bytes.size.toLong(),
            timestamp = timestamp
        )
        val payload = NetworkPayload(
            type = "CHAT_MESSAGE",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = recipientId,
            chatId = chatId,
            payloadJson = chatMessageAdapter.toJson(chatData),
            timestamp = timestamp
        )

        val sent = sendPayload(payload)
        if (sent) {
            messageDao.updateDeliveryStatus(msgId, "SENT")
        }
    }

    suspend fun sendReaction(messageId: String, chatId: String, emoji: String) {
        val updated = toggleReactionString(
            messageDao.getMessageById(messageId)?.reactions ?: "",
            emoji,
            currentUserId
        )
        messageDao.updateReactions(messageId, updated)

        val reactionData = ReactionData(
            messageId = messageId,
            chatId = chatId,
            emoji = emoji,
            userId = currentUserId,
            action = "TOGGLE"
        )
        val payload = NetworkPayload(
            type = "REACTION",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = "all",
            chatId = chatId,
            payloadJson = reactionAdapter.toJson(reactionData)
        )
        sendPayload(payload)
    }

    suspend fun sendBroadcast(title: String, message: String, urgency: String = "NORMAL") {
        val b = BroadcastData(title, message, currentUserName, urgency)
        val payload = NetworkPayload(
            type = "BROADCAST",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = "all",
            payloadJson = broadcastAdapter.toJson(b)
        )
        sendPayload(payload)

        // Save locally
        val entity = MessageEntity(
            messageId = UUID.randomUUID().toString(),
            chatId = "whole_family",
            senderId = currentUserId,
            senderName = currentUserName,
            content = "📢 [ANNOUNCEMENT] $title: $message",
            type = "BROADCAST",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = "READ"
        )
        messageDao.insertOrUpdate(entity)
        chatDao.updateLastMessage("whole_family", "📢 $title", System.currentTimeMillis())
    }

    suspend fun syncSharedItems(items: List<SharedItemEntity>) {
        val dtos = items.map {
            SharedItemDto(
                id = it.id,
                title = it.title,
                isCompleted = it.isCompleted,
                category = it.category,
                addedBy = it.addedBy,
                timestamp = it.timestamp
            )
        }
        val payload = NetworkPayload(
            type = "SHARED_ITEM_SYNC",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = "all",
            payloadJson = sharedSyncAdapter.toJson(SharedItemSyncData(dtos))
        )
        sendPayload(payload)
    }

    suspend fun sendTyping(chatId: String, recipientId: String?, isTyping: Boolean) {
        val payload = NetworkPayload(
            type = "TYPING",
            senderId = currentUserId,
            senderName = currentUserName,
            recipientId = recipientId,
            chatId = chatId,
            payloadJson = typingAdapter.toJson(TypingData(chatId, currentUserId, currentUserName, isTyping))
        )
        sendPayload(payload)
    }

    suspend fun markChatAsRead(chatId: String, partnerId: String?) {
        chatDao.clearUnread(chatId)
        messageDao.markAllAsReadInChat(chatId)

        if (!partnerId.isNullOrEmpty()) {
            val ackPayload = NetworkPayload(
                type = "MESSAGE_ACK",
                senderId = currentUserId,
                senderName = currentUserName,
                recipientId = partnerId,
                chatId = chatId,
                payloadJson = ackAdapter.toJson(
                    MessageAckData(
                        messageId = "all",
                        chatId = chatId,
                        status = "READ",
                        readerId = currentUserId
                    )
                )
            )
            sendPayload(ackPayload)
        }
    }

    private suspend fun sendPayload(payload: NetworkPayload): Boolean {
        return if (isHub) {
            server?.broadcastFromHub(payload)
            true
        } else {
            client?.sendPayload(payload) ?: false
        }
    }

    private fun startOfflineQueueWorker() {
        scope.launch {
            while (isActive) {
                delay(5000L)
                if (_isConnectedToHub.value) {
                    val pending = messageDao.getPendingMessages()
                    for (msg in pending) {
                        val chatData = ChatMessageData(
                            messageId = msg.messageId,
                            chatId = msg.chatId,
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            content = msg.content,
                            type = msg.type,
                            mediaDurationMs = msg.mediaDurationMs,
                            fileSize = msg.fileSize,
                            fileName = msg.fileName,
                            timestamp = msg.timestamp
                        )
                        val recipientId = if (msg.chatId.startsWith("direct_")) msg.chatId.removePrefix("direct_") else "all"
                        val payload = NetworkPayload(
                            type = "CHAT_MESSAGE",
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            recipientId = recipientId,
                            chatId = msg.chatId,
                            payloadJson = chatMessageAdapter.toJson(chatData),
                            timestamp = msg.timestamp
                        )
                        if (sendPayload(payload)) {
                            messageDao.updateDeliveryStatus(msg.messageId, "SENT")
                        }
                    }
                }
            }
        }
    }

    fun dismissBroadcast() {
        _activeBroadcast.value = null
    }

    fun logCall(callId: String, callerId: String, callerName: String, receiverId: String, receiverName: String, isVideo: Boolean, isIncoming: Boolean, isMissed: Boolean, durationSec: Int) {
        scope.launch {
            callLogDao.insertCallLog(
                CallLogEntity(
                    callId = callId,
                    callerId = callerId,
                    callerName = callerName,
                    receiverId = receiverId,
                    receiverName = receiverName,
                    isVideo = isVideo,
                    isIncoming = isIncoming,
                    isMissed = isMissed,
                    durationSec = durationSec
                )
            )
        }
    }
}
