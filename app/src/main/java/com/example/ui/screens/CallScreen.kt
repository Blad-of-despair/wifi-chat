package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webrtc.CallState
import com.example.webrtc.WebRtcManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    callState: CallState,
    callDurationSec: Int,
    webRtcManager: WebRtcManager,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Boolean,
    onToggleCamera: () -> Boolean,
    onFlipCamera: () -> Unit,
    onToggleSpeaker: () -> Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (callState) {
            is CallState.OutgoingRinging -> {
                OutgoingCallView(
                    targetName = callState.targetName,
                    isVideo = callState.isVideo,
                    onEndCall = onEndCall
                )
            }
            is CallState.IncomingRinging -> {
                IncomingCallView(
                    callerName = callState.callerName,
                    isVideo = callState.isVideo,
                    onAccept = onAcceptCall,
                    onReject = onRejectCall
                )
            }
            is CallState.Connected -> {
                ConnectedCallView(
                    state = callState,
                    durationSec = callDurationSec,
                    webRtcManager = webRtcManager,
                    onEndCall = onEndCall,
                    onToggleMute = onToggleMute,
                    onToggleCamera = onToggleCamera,
                    onFlipCamera = onFlipCamera,
                    onToggleSpeaker = onToggleSpeaker
                )
            }
            is CallState.Ended -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = callState.reason,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun OutgoingCallView(
    targetName: String,
    isVideo: Boolean,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "HomeLink Calling...",
                color = Color(0xFF94A3B8),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = targetName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isVideo) "Local Wi-Fi Video Call" else "Local Wi-Fi Voice Call",
                color = Color(0xFF38BDF8),
                fontSize = 14.sp
            )
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF0284C7).copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0284C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = onEndCall,
            containerColor = Color(0xFFEF4444),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(72.dp)
                .testTag("end_call_button")
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun IncomingCallView(
    callerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Incoming LAN Call",
                color = Color(0xFF38BDF8),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = callerName,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isVideo) "Video Call from family phone" else "Voice Call from family phone",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp
            )
        }

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D9488)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(
                onClick = onReject,
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("reject_call_button")
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Decline", modifier = Modifier.size(32.dp))
            }

            FloatingActionButton(
                onClick = onAccept,
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("accept_call_button")
            ) {
                Icon(Icons.Default.Call, contentDescription = "Accept", modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun ConnectedCallView(
    state: CallState.Connected,
    durationSec: Int,
    webRtcManager: WebRtcManager,
    onEndCall: () -> Unit,
    onToggleMute: () -> Boolean,
    onToggleCamera: () -> Boolean,
    onFlipCamera: () -> Unit,
    onToggleSpeaker: () -> Boolean
) {
    val durationText = "%02d:%02d".format(durationSec / 60, durationSec % 60)

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Renderers or Audio Avatar
        if (state.isVideo && !state.isCameraOff) {
            // Remote Video Fullscreen
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(webRtcManager.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        webRtcManager.remoteRenderer = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Video Floating Preview PiP in top right corner
            Box(
                modifier = Modifier
                    .padding(top = 24.dp, end = 24.dp)
                    .size(110.dp, 150.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(webRtcManager.eglBase.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setMirror(true)
                            setEnableHardwareScaler(true)
                            setZOrderMediaOverlay(true)
                            webRtcManager.localRenderer = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Voice Call presentation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.partnerName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Encrypted Local Audio",
                    color = Color(0xFF38BDF8),
                    fontSize = 14.sp
                )
            }
        }

        // Top duration overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom Call Controls Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mic Mute
                IconButton(
                    onClick = { onToggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (state.isMuted) Color(0xFFEF4444) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                // Speaker
                IconButton(
                    onClick = { onToggleSpeaker() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (state.isSpeakerOn) Color(0xFF0284C7) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = Color.White
                    )
                }

                // Camera Toggle (if video)
                if (state.isVideo) {
                    IconButton(
                        onClick = { onToggleCamera() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (state.isCameraOff) Color(0xFFEF4444) else Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = if (state.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Camera",
                            tint = Color.White
                        )
                    }

                    // Flip camera
                    IconButton(
                        onClick = onFlipCamera,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip",
                            tint = Color.White
                        )
                    }
                }

                // End Call Button
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("end_connected_call_button")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
