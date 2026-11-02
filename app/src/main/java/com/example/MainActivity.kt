package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.data.local.entity.ChatEntity
import com.example.ui.screens.BroadcastDialog
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.QrCodeDialog
import com.example.ui.theme.HomeLinkTheme
import com.example.ui.viewmodel.HomeLinkViewModel
import com.example.webrtc.CallState

class MainActivity : ComponentActivity() {

    private val viewModel: HomeLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "dark", "amoled" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val isAmoled = themeMode == "amoled"

            HomeLinkTheme(darkTheme = isDark, isAmoled = isAmoled) {
                RequestPermissionsEffect()
                HomeLinkRoot(viewModel = viewModel, onEnterPip = { enterPipIfSupported() })
            }
        }
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = viewModel.callState.value
            if (state is CallState.Connected && state.isVideo) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfSupported()
    }
}

@Composable
private fun RequestPermissionsEffect() {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
private fun HomeLinkRoot(
    viewModel: HomeLinkViewModel,
    onEnterPip: () -> Unit
) {
    val isOnboarded by viewModel.isOnboarded.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val callDurationSec by viewModel.callDurationSec.collectAsState()
    val activeChatId by viewModel.activeChatId.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val showQrDialog by viewModel.showQrDialog.collectAsState()
    val showBroadcastDialog by viewModel.showBroadcastDialog.collectAsState()
    val isHub by viewModel.isHub.collectAsState()
    val currentPin by viewModel.currentPin.collectAsState()
    val localIp = viewModel.getLocalIp()

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isOnboarded) {
            OnboardingScreen(
                onComplete = { name, color, uri, pin, isHubMode ->
                    viewModel.saveOnboarding(name, color, uri, pin, isHubMode)
                }
            )
        } else if (callState !is CallState.Idle) {
            CallScreen(
                callState = callState,
                callDurationSec = callDurationSec,
                webRtcManager = viewModel.repository.webRtcManager,
                onAcceptCall = { viewModel.acceptCall() },
                onRejectCall = { viewModel.rejectCall() },
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleCallMute() },
                onToggleCamera = { viewModel.toggleCallCamera() },
                onFlipCamera = { viewModel.flipCallCamera() },
                onToggleSpeaker = { viewModel.toggleCallSpeaker() }
            )
        } else if (activeChatId != null) {
            val chat = allChats.firstOrNull { it.chatId == activeChatId }
                ?: ChatEntity(
                    chatId = activeChatId!!,
                    title = "Family Chat",
                    avatarColor = 0xFF0284C7
                )
            ChatScreen(
                chat = chat,
                viewModel = viewModel,
                onBack = { viewModel.closeChat() }
            )
        } else {
            MainScreen(
                viewModel = viewModel,
                onOpenChat = { chat -> viewModel.openChat(chat.chatId) }
            )
        }

        // Dialogs
        if (showQrDialog) {
            QrCodeDialog(
                hubIp = localIp,
                port = 8888,
                pin = currentPin,
                isHub = isHub,
                onDismiss = { viewModel.toggleQrDialog(false) }
            )
        }

        if (showBroadcastDialog) {
            BroadcastDialog(
                onSendBroadcast = { title, msg, urgency ->
                    viewModel.sendBroadcast(title, msg, urgency)
                },
                onDismiss = { viewModel.toggleBroadcastDialog(false) }
            )
        }
    }
}
