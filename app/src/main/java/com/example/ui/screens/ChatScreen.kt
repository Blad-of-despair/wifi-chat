package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.ChatEntity
import com.example.data.local.entity.MessageEntity
import com.example.ui.theme.ReceivedBubbleDark
import com.example.ui.theme.ReceivedBubbleLight
import com.example.ui.theme.SentBubbleDark
import com.example.ui.theme.SentBubbleLight
import com.example.ui.theme.StatusOnline
import com.example.ui.viewmodel.HomeLinkViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatEntity,
    viewModel: HomeLinkViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val messages by viewModel.activeChatMessages.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val replyingTo by viewModel.replyingToMessage.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    val partnerId = viewModel.getPartnerId(chat.chatId)

    // Voice recorder state
    val isRecording by viewModel.repository.voiceRecorder.isRecording.collectAsState()
    val recordDurationMs by viewModel.repository.voiceRecorder.recordingDurationMs.collectAsState()
    val amplitudes by viewModel.repository.voiceRecorder.amplitudes.collectAsState()

    // Voice player state
    val playingUri by viewModel.repository.voicePlayer.currentPlayingUri.collectAsState()
    val isPlaying by viewModel.repository.voicePlayer.isPlaying.collectAsState()
    val playerSpeed by viewModel.repository.voicePlayer.playbackSpeed.collectAsState()

    // Photo picker (Zero permissions Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendImageMessage(chat.chatId, uri)
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Chat Header TopAppBar
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(chat.avatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.title.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = chat.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val isTyping = typingUsers[chat.chatId] != null
                        Text(
                            text = if (isTyping) "typing..." else if (chat.isGroup) "Family Group" else "Local Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                if (!chat.isGroup && partnerId != null) {
                    // Audio Call
                    IconButton(
                        onClick = { viewModel.startCall(partnerId, chat.title, isVideo = false) },
                        modifier = Modifier.testTag("chat_audio_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call")
                    }
                    // Video Call
                    IconButton(
                        onClick = { viewModel.startCall(partnerId, chat.title, isVideo = true) },
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                    }
                }
            }
        )

        // Messages LazyColumn
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No messages yet.\nSay hello to your family!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        val isMe = msg.senderId == viewModel.repository.currentUserId
                        MessageBubble(
                            message = msg,
                            isMe = isMe,
                            playingUri = playingUri,
                            isPlaying = isPlaying,
                            playerSpeed = playerSpeed,
                            onPlayVoice = { path -> viewModel.repository.voicePlayer.play(path) },
                            onToggleSpeed = { viewModel.repository.voicePlayer.toggleSpeed() },
                            onReact = { emoji -> viewModel.sendReaction(msg.messageId, chat.chatId, emoji) },
                            onReply = { viewModel.setReplyingTo(msg) }
                        )
                    }
                }
            }
        }

        // Reply Preview Banner
        if (replyingTo != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyingTo!!.senderName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = replyingTo!!.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { viewModel.setReplyingTo(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }
        }

        // Recording Live Waveform Bar
        if (isRecording) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val sec = (recordDurationMs / 1000).toInt()
                        Text(
                            text = "Recording %02d:%02d".format(sec / 60, sec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    // Live amplitude waveform
                    Row(
                        modifier = Modifier.width(100.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        amplitudes.takeLast(16).forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height((amp * 28).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val res = viewModel.repository.voiceRecorder.stopRecording()
                            if (res != null) {
                                viewModel.sendVoiceMessage(chat.chatId, res.file, res.durationMs)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Send voice note",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Bottom Input Row
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Attachment
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("chat_photo_picker_button")
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Send Photo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        viewModel.setTyping(chat.chatId, it.isNotBlank())
                    },
                    placeholder = { Text("Family message...") },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field")
                )

                // Mic / Hold to record or Send
                if (inputText.isBlank()) {
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                viewModel.repository.voiceRecorder.startRecording()
                            } else {
                                val res = viewModel.repository.voiceRecorder.stopRecording()
                                if (res != null) {
                                    viewModel.sendVoiceMessage(chat.chatId, res.file, res.durationMs)
                                }
                            }
                        },
                        modifier = Modifier.testTag("chat_voice_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice Message",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            viewModel.sendTextMessage(chat.chatId, inputText)
                            inputText = ""
                            viewModel.setTyping(chat.chatId, false)
                        },
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    playingUri: String?,
    isPlaying: Boolean,
    playerSpeed: Float,
    onPlayVoice: (String) -> Unit,
    onToggleSpeed: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit
) {
    var showReactionMenu by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showReactionMenu = !showReactionMenu }
            ),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // Quick reaction bar on long press
        if (showReactionMenu) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("❤️", "👍", "😂", "😮", "🙏", "🍲").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable {
                                onReact(emoji)
                                showReactionMenu = false
                            }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // Sender name if in group
                if (!isMe) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Reply context
                if (!message.replyToContent.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.1f))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = message.replyToContent,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Message Content by Type
                when (message.type) {
                    "IMAGE" -> {
                        if (!message.mediaUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = message.mediaUri,
                                contentDescription = "Shared Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    "VOICE" -> {
                        val isThisPlaying = playingUri == message.mediaUri && isPlaying
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = { message.mediaUri?.let { onPlayVoice(it) } },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isMe) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice note",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val sec = (message.mediaDurationMs / 1000).toInt()
                                Text(
                                    text = "Voice message (%02d:%02d)".format(sec / 60, sec % 60),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = "${playerSpeed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.8f),
                                    modifier = Modifier.clickable { onToggleSpeed() }
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }

                // Timestamp and delivery ticks
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            "PENDING" -> Icon(
                                Icons.Default.AccessTime,
                                contentDescription = "Pending",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            "SENT" -> Icon(
                                Icons.Default.Check,
                                contentDescription = "Sent",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            "DELIVERED" -> Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            "READ" -> Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Emoji reactions display under bubble
        if (message.reactions.isNotBlank()) {
            val reactionItems = message.reactions.split("|").filter { it.isNotBlank() }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reactionItems.forEach { item ->
                    val emoji = item.substringBefore(":")
                    val count = item.substringAfter(":").split(",").filter { it.isNotBlank() }.size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onReact(emoji) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$emoji $count",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
