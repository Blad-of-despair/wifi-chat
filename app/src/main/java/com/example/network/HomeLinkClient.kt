package com.example.network

import android.util.Log
import com.example.data.model.HandshakeData
import com.example.data.model.NetworkPayload
import com.squareup.moshi.Moshi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ConnectException

class HomeLinkClient(
    private val userId: String,
    private val userName: String,
    private val avatarColor: Long,
    private val pinHash: String,
    private val onPayloadReceived: (NetworkPayload) -> Unit
) {
    private val tag = "HomeLinkClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val moshi = Moshi.Builder().build()
    private val payloadAdapter = moshi.adapter(NetworkPayload::class.java)
    private val handshakeAdapter = moshi.adapter(HandshakeData::class.java)

    private val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 15000L
            maxFrameSize = 10 * 1024 * 1024
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var pingJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val _currentHubHost = MutableStateFlow<String?>(null)
    val currentHubHost: StateFlow<String?> = _currentHubHost.asStateFlow()

    fun connect(host: String, port: Int = 8888) {
        if (_isConnected.value && _currentHubHost.value == host) return
        disconnect()

        _currentHubHost.value = host
        connectionJob = scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                try {
                    Log.d(tag, "Connecting to ws://$host:$port/ws ...")
                    httpClient.webSocket(host = host, port = port, path = "/ws") {
                        session = this
                        _isConnected.value = true
                        retryDelay = 1000L
                        Log.d(tag, "WebSocket connected! Sending handshake...")

                        // Send handshake
                        val handshake = HandshakeData(
                            pinHash = pinHash,
                            userName = userName,
                            avatarColor = avatarColor
                        )
                        val handshakePayload = NetworkPayload(
                            type = "HANDSHAKE",
                            senderId = userId,
                            senderName = userName,
                            recipientId = "hub",
                            payloadJson = handshakeAdapter.toJson(handshake)
                        )
                        send(Frame.Text(payloadAdapter.toJson(handshakePayload)))

                        // Start latency ping monitor
                        startPingMonitor(host, port)

                        // Listen for incoming messages
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val payload = try {
                                    payloadAdapter.fromJson(text)
                                } catch (e: Exception) { null }

                                if (payload != null) {
                                    onPayloadReceived(payload)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: ConnectException) {
                    Log.w(tag, "Connection failed to $host:$port: ${e.message}")
                } catch (e: Exception) {
                    Log.e(tag, "WebSocket exception: ${e.message}")
                } finally {
                    _isConnected.value = false
                    session = null
                    _latencyMs.value = null
                    pingJob?.cancel()
                }

                if (isActive) {
                    Log.d(tag, "Reconnecting in ${retryDelay / 1000}s...")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(10000L)
                }
            }
        }
    }

    private fun startPingMonitor(host: String, port: Int) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _isConnected.value) {
                try {
                    val start = System.currentTimeMillis()
                    val response = httpClient.get("http://$host:$port/api/ping").bodyAsText()
                    if (response.contains("pong")) {
                        _latencyMs.value = System.currentTimeMillis() - start
                    }
                } catch (_: Exception) {}
                delay(10000L)
            }
        }
    }

    suspend fun sendPayload(payload: NetworkPayload): Boolean {
        val s = session
        if (s == null || !_isConnected.value) {
            Log.w(tag, "Cannot send: Not connected to hub")
            return false
        }
        return try {
            val json = payloadAdapter.toJson(payload)
            s.send(Frame.Text(json))
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending payload: ${e.message}")
            false
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        pingJob?.cancel()
        pingJob = null
        scope.launch {
            try {
                session?.close()
            } catch (_: Exception) {}
            session = null
            _isConnected.value = false
            _latencyMs.value = null
        }
    }
}
