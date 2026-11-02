package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CallLogEntity
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SharedItemEntity
import com.example.data.local.entity.UserEntity
import com.example.data.model.BroadcastData
import com.example.data.repository.HomeLinkRepository
import com.example.network.NetworkUtils
import com.example.webrtc.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class HomeLinkViewModel(application: Application) : AndroidViewModel(application) {

    val repository = HomeLinkRepository(application)
    val prefs = repository.prefs

    val isOnboarded: StateFlow<Boolean> = prefs.isOnboardedFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val currentUserName: StateFlow<String> = prefs.userNameFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Family Member"
    )

    val currentAvatarColor: StateFlow<Long> = prefs.avatarColorFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF0284C7
    )

    val currentPin: StateFlow<String> = prefs.familyPinFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "1234"
    )

    val isHub: StateFlow<Boolean> = prefs.isHubFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val currentStatus: StateFlow<String> = prefs.statusMessageFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Home"
    )

    val isDnd: StateFlow<Boolean> = prefs.isDndFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val themeMode: StateFlow<String> = prefs.themeModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "system"
    )

    val isConnectedToHub: StateFlow<Boolean> = repository.isConnectedToHub
    val isHubRunning: StateFlow<Boolean> = repository.isHubRunning
    val activeHubAddress: StateFlow<String?> = repository.activeHubAddress
    val activeBroadcast: StateFlow<BroadcastData?> = repository.activeBroadcast
    val typingUsers: StateFlow<Map<String, String>> = repository.typingUsers

    val allChats: StateFlow<List<ChatEntity>> = repository.allChats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allCallLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allSharedItems: StateFlow<List<SharedItemEntity>> = repository.allSharedItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val callState: StateFlow<CallState> = repository.webRtcManager.callState
    val callDurationSec: StateFlow<Int> = repository.webRtcManager.callDurationSec

    // Navigation and screen states
    private val _currentTab = MutableStateFlow(0) // 0: Chats, 1: Calls, 2: Family, 3: Shared, 4: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _activeChatMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeChatMessages: StateFlow<List<MessageEntity>> = _activeChatMessages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val searchResults: StateFlow<List<MessageEntity>> = _searchResults.asStateFlow()

    private val _showQrDialog = MutableStateFlow(false)
    val showQrDialog: StateFlow<Boolean> = _showQrDialog.asStateFlow()

    private val _showBroadcastDialog = MutableStateFlow(false)
    val showBroadcastDialog: StateFlow<Boolean> = _showBroadcastDialog.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<MessageEntity?>(null)
    val replyingToMessage: StateFlow<MessageEntity?> = _replyingToMessage.asStateFlow()

    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        viewModelScope.launch {
            repository.markChatAsRead(chatId, getPartnerId(chatId))
            repository.messageDao.getMessagesForChatFlow(chatId).collect { msgs ->
                _activeChatMessages.value = msgs
            }
        }
    }

    fun closeChat() {
        _activeChatId.value = null
        _activeChatMessages.value = emptyList()
        _replyingToMessage.value = null
    }

    fun getPartnerId(chatId: String): String? {
        return if (chatId.startsWith("direct_")) chatId.removePrefix("direct_") else null
    }

    fun saveOnboarding(name: String, color: Long, avatarUri: String?, pin: String, isHub: Boolean) {
        viewModelScope.launch {
            prefs.saveProfile(name, color, avatarUri, pin, isHub)
            repository.currentUserName = name
            repository.currentPin = pin
            repository.isHub = isHub
            repository.startNetworkStack()
        }
    }

    fun sendTextMessage(chatId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val reply = _replyingToMessage.value
            _replyingToMessage.value = null
            repository.sendTextMessage(chatId, getPartnerId(chatId), text.trim(), reply)
        }
    }

    fun sendVoiceMessage(chatId: String, file: File, durationMs: Long) {
        viewModelScope.launch {
            repository.sendVoiceMessage(chatId, getPartnerId(chatId), file, durationMs)
        }
    }

    fun sendImageMessage(chatId: String, uri: Uri) {
        viewModelScope.launch {
            repository.sendImageMessage(chatId, getPartnerId(chatId), uri)
        }
    }

    fun sendReaction(messageId: String, chatId: String, emoji: String) {
        viewModelScope.launch {
            repository.sendReaction(messageId, chatId, emoji)
        }
    }

    fun sendBroadcast(title: String, message: String, urgency: String = "NORMAL") {
        viewModelScope.launch {
            repository.sendBroadcast(title, message, urgency)
            _showBroadcastDialog.value = false
        }
    }

    fun setReplyingTo(message: MessageEntity?) {
        _replyingToMessage.value = message
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTyping(chatId, getPartnerId(chatId), isTyping)
        }
    }

    fun startCall(targetId: String, targetName: String, isVideo: Boolean) {
        val callId = UUID.randomUUID().toString()
        repository.webRtcManager.startOutgoingCall(callId, targetId, targetName, isVideo)
    }

    fun acceptCall() {
        repository.webRtcManager.acceptIncomingCall()
    }

    fun rejectCall() {
        repository.webRtcManager.rejectIncomingCall()
    }

    fun endCall() {
        val state = repository.webRtcManager.callState.value
        if (state is CallState.Connected) {
            repository.logCall(
                callId = state.callId,
                callerId = repository.currentUserId,
                callerName = repository.currentUserName,
                receiverId = state.partnerId,
                receiverName = state.partnerName,
                isVideo = state.isVideo,
                isIncoming = false,
                isMissed = false,
                durationSec = repository.webRtcManager.callDurationSec.value
            )
        }
        repository.webRtcManager.endCall()
    }

    fun toggleCallMute(): Boolean = repository.webRtcManager.toggleMute()
    fun toggleCallCamera(): Boolean = repository.webRtcManager.toggleCamera()
    fun flipCallCamera() = repository.webRtcManager.flipCamera()
    fun toggleCallSpeaker(): Boolean = repository.webRtcManager.toggleSpeaker()

    fun dismissBroadcast() {
        repository.dismissBroadcast()
    }

    fun toggleQrDialog(show: Boolean) {
        _showQrDialog.value = show
    }

    fun toggleBroadcastDialog(show: Boolean) {
        _showBroadcastDialog.value = show
    }

    fun updateStatus(status: String) {
        viewModelScope.launch {
            prefs.updateStatus(status)
            // Broadcast presence update
        }
    }

    fun updateDnd(dnd: Boolean) {
        viewModelScope.launch {
            prefs.updateDnd(dnd)
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            prefs.updateTheme(theme)
        }
    }

    fun updateHubMode(isHub: Boolean) {
        viewModelScope.launch {
            prefs.updateHubMode(isHub)
            repository.isHub = isHub
            repository.startNetworkStack()
        }
    }

    fun updateManualHub(ip: String, port: String) {
        viewModelScope.launch {
            prefs.updateManualHub(ip, port)
            repository.startNetworkStack()
        }
    }

    fun addSharedItem(title: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val item = SharedItemEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                isCompleted = false,
                category = category,
                addedBy = repository.currentUserName,
                timestamp = System.currentTimeMillis()
            )
            repository.sharedItemDao.insertOrUpdate(item)
            val all = repository.sharedItemDao.getAllItemsFlow()
            // sync
            viewModelScope.launch {
                repository.syncSharedItems(listOf(item))
            }
        }
    }

    fun toggleSharedItem(item: SharedItemEntity) {
        viewModelScope.launch {
            val updated = item.copy(isCompleted = !item.isCompleted)
            repository.sharedItemDao.insertOrUpdate(updated)
            repository.syncSharedItems(listOf(updated))
        }
    }

    fun deleteSharedItem(id: String) {
        viewModelScope.launch {
            repository.sharedItemDao.deleteItem(id)
        }
    }

    fun searchMessages(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repository.messageDao.searchMessages(query)
        }
    }

    fun createGroupChat(title: String, avatarColor: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = "group_" + UUID.randomUUID().toString().take(8)
            val chat = ChatEntity(
                chatId = id,
                title = title.trim(),
                isGroup = true,
                avatarColor = avatarColor,
                lastMessageText = "Group created",
                lastMessageTimestamp = System.currentTimeMillis()
            )
            repository.chatDao.insertOrUpdate(chat)
        }
    }

    fun getLocalIp(): String = NetworkUtils.getLocalIpAddress()
}
