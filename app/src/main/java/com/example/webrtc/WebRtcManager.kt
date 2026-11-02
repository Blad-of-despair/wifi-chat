package com.example.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.model.CallSignalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

sealed interface CallState {
    object Idle : CallState
    data class OutgoingRinging(val callId: String, val targetId: String, val targetName: String, val isVideo: Boolean) : CallState
    data class IncomingRinging(val callId: String, val callerId: String, val callerName: String, val isVideo: Boolean) : CallState
    data class Connected(
        val callId: String,
        val partnerId: String,
        val partnerName: String,
        val isVideo: Boolean,
        val startTimestamp: Long = System.currentTimeMillis(),
        val isMuted: Boolean = false,
        val isCameraOff: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val isFrontCamera: Boolean = true
    ) : CallState
    data class Ended(val reason: String) : CallState
}

class WebRtcManager(
    private val context: Context,
    private val onSendSignal: (CallSignalData) -> Unit
) {
    private val tag = "HomeLinkWebRTC"
    private val scope = CoroutineScope(Dispatchers.Main)

    val eglBase: EglBase by lazy { EglBase.create() }

    private var factory: PeerConnectionFactory? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // Support 1-to-1 and mesh peers: peerId -> PeerConnection
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSec = MutableStateFlow(0)
    val callDurationSec: StateFlow<Int> = _callDurationSec.asStateFlow()

    private var timerJob: Job? = null
    private var vibrator: Vibrator? = null

    var localRenderer: SurfaceViewRenderer? = null
    var remoteRenderer: SurfaceViewRenderer? = null

    init {
        initializeFactory()
        initVibrator()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun initializeFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            Log.d(tag, "WebRTC PeerConnectionFactory initialized")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize WebRTC factory", e)
        }
    }

    fun startOutgoingCall(callId: String, targetId: String, targetName: String, isVideo: Boolean) {
        _callState.value = CallState.OutgoingRinging(callId, targetId, targetName, isVideo)
        setupLocalMedia(isVideo)
        createPeerConnection(targetId, callId, isCaller = true, isVideo = isVideo)
    }

    fun onIncomingCall(callId: String, callerId: String, callerName: String, isVideo: Boolean) {
        if (_callState.value !is CallState.Idle) {
            // Already in a call, reject as busy
            onSendSignal(
                CallSignalData(
                    callId = callId,
                    callerId = callerId,
                    callerName = callerName,
                    targetId = callerId,
                    isVideo = isVideo,
                    signalType = "BUSY"
                )
            )
            return
        }
        _callState.value = CallState.IncomingRinging(callId, callerId, callerName, isVideo)
        startVibrating()
    }

    fun acceptIncomingCall() {
        val state = _callState.value as? CallState.IncomingRinging ?: return
        stopVibrating()
        setupLocalMedia(state.isVideo)
        createPeerConnection(state.callerId, state.callId, isCaller = false, isVideo = state.isVideo)

        _callState.value = CallState.Connected(
            callId = state.callId,
            partnerId = state.callerId,
            partnerName = state.callerName,
            isVideo = state.isVideo,
            isSpeakerOn = state.isVideo
        )
        startCallTimer()
        setSpeakerphone(state.isVideo)
    }

    fun rejectIncomingCall() {
        val state = _callState.value as? CallState.IncomingRinging ?: return
        stopVibrating()
        onSendSignal(
            CallSignalData(
                callId = state.callId,
                callerId = state.callerId,
                callerName = state.callerName,
                targetId = state.callerId,
                isVideo = state.isVideo,
                signalType = "REJECT"
            )
        )
        endCall("Call rejected")
    }

    private fun setupLocalMedia(isVideo: Boolean) {
        val fac = factory ?: return
        try {
            // Local Audio Track
            if (localAudioTrack == null) {
                val audioConstraints = MediaConstraints()
                localAudioSource = fac.createAudioSource(audioConstraints)
                localAudioTrack = fac.createAudioTrack("HL_AUDIO_TRACK", localAudioSource)
                localAudioTrack?.setEnabled(true)
            }

            // Local Video Track
            if (isVideo && localVideoTrack == null) {
                val enumerator = Camera2Enumerator(context)
                val deviceNames = enumerator.deviceNames
                val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()

                if (frontCamera != null) {
                    videoCapturer = enumerator.createCapturer(frontCamera, null)
                    surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)
                    localVideoSource = fac.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
                    videoCapturer!!.startCapture(640, 480, 30)

                    localVideoTrack = fac.createVideoTrack("HL_VIDEO_TRACK", localVideoSource)
                    localVideoTrack?.setEnabled(true)
                    localRenderer?.let { localVideoTrack?.addSink(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up local media", e)
        }
    }

    private fun createPeerConnection(
        peerId: String,
        callId: String,
        isCaller: Boolean,
        isVideo: Boolean
    ): PeerConnection? {
        val fac = factory ?: return null

        // LAN P2P: No STUN or TURN needed! Empty IceServer list forces Host candidates only.
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(tag, "Local ICE host candidate found: ${candidate.sdp}")
                onSendSignal(
                    CallSignalData(
                        callId = callId,
                        callerId = "",
                        callerName = "",
                        targetId = peerId,
                        isVideo = isVideo,
                        signalType = "CANDIDATE",
                        sdp = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(tag, "Remote media stream added from $peerId: videoTracks=${stream.videoTracks.size}")
                if (stream.videoTracks.isNotEmpty()) {
                    val remoteTrack = stream.videoTracks[0]
                    scope.launch {
                        remoteRenderer?.let { remoteTrack.addSink(it) }
                    }
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(tag, "ICE Connection State: $state")
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    scope.launch {
                        val current = _callState.value
                        if (current is CallState.OutgoingRinging) {
                            _callState.value = CallState.Connected(
                                callId = current.callId,
                                partnerId = current.targetId,
                                partnerName = current.targetName,
                                isVideo = current.isVideo,
                                isSpeakerOn = current.isVideo
                            )
                            startCallTimer()
                            setSpeakerphone(current.isVideo)
                        }
                    }
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED
                ) {
                    Log.w(tag, "PeerConnection disconnected or failed")
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dataChannel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        }

        val pc = fac.createPeerConnection(rtcConfig, observer) ?: return null
        peerConnections[peerId] = pc

        // Attach local tracks
        localAudioTrack?.let { pc.addTrack(it) }
        localVideoTrack?.let { pc.addTrack(it) }

        if (isCaller) {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
            }
            pc.createOffer(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    pc.setLocalDescription(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(tag, "Local offer set successfully, sending to $peerId")
                            onSendSignal(
                                CallSignalData(
                                    callId = callId,
                                    callerId = "",
                                    callerName = "",
                                    targetId = peerId,
                                    isVideo = isVideo,
                                    signalType = "OFFER",
                                    sdp = desc.description
                                )
                            )
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(err: String?) { Log.e(tag, "Create offer failed: $err") }
                override fun onSetFailure(err: String?) {}
            }, constraints)
        }

        return pc
    }

    fun handleIncomingSignal(signal: CallSignalData) {
        val pc = peerConnections[signal.callerId]

        when (signal.signalType) {
            "OFFER" -> {
                onIncomingCall(signal.callId, signal.callerId, signal.callerName, signal.isVideo)
                // We'll set remote description when accepted
                pendingRemoteOffer = signal
            }
            "ANSWER" -> {
                pc?.let {
                    val desc = SessionDescription(SessionDescription.Type.ANSWER, signal.sdp)
                    it.setRemoteDescription(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() { Log.d(tag, "Remote answer set successfully") }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) { Log.e(tag, "Failed to set answer: $err") }
                    }, desc)
                }
            }
            "CANDIDATE" -> {
                val candidate = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.sdp)
                pc?.addIceCandidate(candidate)
            }
            "END", "REJECT", "BUSY" -> {
                endCall("Call ended: ${signal.signalType}")
            }
        }
    }

    private var pendingRemoteOffer: CallSignalData? = null

    fun processPendingOffer(partnerId: String, callId: String, isVideo: Boolean) {
        val offer = pendingRemoteOffer ?: return
        val pc = peerConnections[partnerId] ?: return

        val desc = SessionDescription(SessionDescription.Type.OFFER, offer.sdp)
        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(tag, "Remote offer set, creating answer...")
                val constraints = MediaConstraints()
                pc.createAnswer(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(ansDesc: SessionDescription) {
                        pc.setLocalDescription(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(tag, "Local answer set, sending...")
                                onSendSignal(
                                    CallSignalData(
                                        callId = callId,
                                        callerId = "",
                                        callerName = "",
                                        targetId = partnerId,
                                        isVideo = isVideo,
                                        signalType = "ANSWER",
                                        sdp = ansDesc.description
                                    )
                                )
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, ansDesc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) { Log.e(tag, "Failed to create answer: $err") }
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) { Log.e(tag, "Set remote offer failure: $err") }
        }, desc)
        pendingRemoteOffer = null
    }

    fun toggleMute(): Boolean {
        val current = _callState.value as? CallState.Connected ?: return false
        val newMuted = !current.isMuted
        localAudioTrack?.setEnabled(!newMuted)
        _callState.value = current.copy(isMuted = newMuted)
        return newMuted
    }

    fun toggleCamera(): Boolean {
        val current = _callState.value as? CallState.Connected ?: return false
        val newCamOff = !current.isCameraOff
        localVideoTrack?.setEnabled(!newCamOff)
        _callState.value = current.copy(isCameraOff = newCamOff)
        return newCamOff
    }

    fun flipCamera() {
        val current = _callState.value as? CallState.Connected ?: return
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _callState.value = current.copy(isFrontCamera = isFront)
            }
            override fun onCameraSwitchError(err: String?) {
                Log.e(tag, "Error flipping camera: $err")
            }
        })
    }

    fun toggleSpeaker(): Boolean {
        val current = _callState.value as? CallState.Connected ?: return false
        val newSpeaker = !current.isSpeakerOn
        setSpeakerphone(newSpeaker)
        _callState.value = current.copy(isSpeakerOn = newSpeaker)
        return newSpeaker
    }

    private fun setSpeakerphone(enable: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = enable
        } catch (_: Exception) {}
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        _callDurationSec.value = 0
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                _callDurationSec.value += 1
            }
        }
    }

    private fun startVibrating() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibrating() {
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    fun endCall(reason: String = "Call ended") {
        timerJob?.cancel()
        timerJob = null
        stopVibrating()

        val current = _callState.value
        if (current is CallState.Connected) {
            onSendSignal(
                CallSignalData(
                    callId = current.callId,
                    callerId = "",
                    callerName = "",
                    targetId = current.partnerId,
                    isVideo = current.isVideo,
                    signalType = "END"
                )
            )
        } else if (current is CallState.OutgoingRinging) {
            onSendSignal(
                CallSignalData(
                    callId = current.callId,
                    callerId = "",
                    callerName = "",
                    targetId = current.targetId,
                    isVideo = current.isVideo,
                    signalType = "END"
                )
            )
        }

        peerConnections.values.forEach { pc ->
            try {
                pc.close()
                pc.dispose()
            } catch (_: Exception) {}
        }
        peerConnections.clear()

        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (_: Exception) {}
        videoCapturer = null

        localVideoTrack?.dispose()
        localVideoTrack = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        localAudioSource?.dispose()
        localAudioSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        _callState.value = CallState.Ended(reason)
        scope.launch {
            delay(1500L)
            _callState.value = CallState.Idle
        }
    }
}
