package com.example.network

import android.util.Log
import com.example.data.model.HandshakeData
import com.example.data.model.NetworkPayload
import com.example.data.model.PresenceData
import com.squareup.moshi.Moshi
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class HomeLinkServer(
    private val expectedPinHash: String,
    private val hubUserId: String,
    private val hubUserName: String,
    private val port: Int = 8888,
    private val onMessageReceived: (NetworkPayload) -> Unit = {}
) {
    private val tag = "HomeLinkServer"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverEngine: ApplicationEngine? = null

    private val moshi = Moshi.Builder().build()
    private val payloadAdapter = moshi.adapter(NetworkPayload::class.java)
    private val handshakeAdapter = moshi.adapter(HandshakeData::class.java)
    private val presenceAdapter = moshi.adapter(PresenceData::class.java)

    // Map userId -> Client Session info
    data class ConnectedClient(
        val userId: String,
        val userName: String,
        val avatarColor: Long,
        val session: DefaultWebSocketServerSession,
        var isOnline: Boolean = true,
        var statusMessage: String = "Home",
        var isDnd: Boolean = false,
        var ip: String = ""
    )

    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        scope.launch {
            try {
                Log.d(tag, "Starting HomeLink Ktor CIO server on port $port...")
                serverEngine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    install(WebSockets) {
                        pingPeriodMillis = 15000L
                        timeoutMillis = 30000L
                        maxFrameSize = 10 * 1024 * 1024 // 10MB frames for media transfer
                    }

                    routing {
                        get("/api/ping") {
                            call.respondText("pong")
                        }

                        get("/api/status") {
                            call.respondText("""{"status":"ok","hub":"$hubUserName","clients":${connectedClients.size}}""")
                        }

                        webSocket("/ws") {
                            var clientUserId: String? = null
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        val payload = payloadAdapter.fromJson(text) ?: continue

                                        if (payload.type == "HANDSHAKE") {
                                            val handshake = try {
                                                handshakeAdapter.fromJson(payload.payloadJson)
                                            } catch (e: Exception) { null }

                                            if (handshake == null || handshake.pinHash != expectedPinHash) {
                                                Log.w(tag, "Rejecting client: Invalid PIN hash from ${payload.senderName}")
                                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid PIN"))
                                                return@webSocket
                                            }

                                            clientUserId = payload.senderId
                                            val client = ConnectedClient(
                                                userId = payload.senderId,
                                                userName = handshake.userName,
                                                avatarColor = handshake.avatarColor,
                                                session = this@webSocket,
                                                isOnline = true,
                                                statusMessage = handshake.statusMessage,
                                                isDnd = handshake.isDnd
                                            )
                                            connectedClients[payload.senderId] = client
                                            Log.d(tag, "Client authenticated: ${handshake.userName} (${payload.senderId})")

                                            // Send list of all currently connected clients to this new client
                                            broadcastAllPresence()
                                            continue
                                        }

                                        // Relay or broadcast message
                                        handleIncomingPayload(payload, this@webSocket)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Client exception: ${e.message}")
                            } finally {
                                if (clientUserId != null) {
                                    connectedClients.remove(clientUserId)
                                    Log.d(tag, "Client disconnected: $clientUserId")
                                    broadcastPresence(clientUserId, isOnline = false)
                                }
                            }
                        }
                    }
                }.start(wait = false)
                isRunning = true
                Log.d(tag, "HomeLink Ktor CIO server started successfully on port $port")
            } catch (e: Exception) {
                Log.e(tag, "Error starting server", e)
                isRunning = false
            }
        }
    }

    private suspend fun handleIncomingPayload(
        payload: NetworkPayload,
        senderSession: DefaultWebSocketServerSession
    ) {
        onMessageReceived(payload)

        val targetId = payload.recipientId
        val json = payloadAdapter.toJson(payload)

        if (targetId.isNullOrEmpty() || targetId == "all" || payload.type == "BROADCAST" || payload.type == "SHARED_ITEM_SYNC") {
            // Broadcast to all connected clients except the sender
            connectedClients.values.forEach { client ->
                if (client.session != senderSession) {
                    try {
                        client.session.send(Frame.Text(json))
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to relay to ${client.userId}", e)
                    }
                }
            }
        } else {
            // Direct 1-to-1 message or WebRTC signaling
            val recipient = connectedClients[targetId]
            if (recipient != null) {
                try {
                    recipient.session.send(Frame.Text(json))
                } catch (e: Exception) {
                    Log.e(tag, "Error delivering 1-to-1 payload to $targetId", e)
                }
            } else {
                Log.d(tag, "Target $targetId is currently offline, message will remain in local sync")
            }
        }
    }

    private suspend fun broadcastAllPresence() {
        val allUsers = connectedClients.values.map { client ->
            PresenceData(
                userId = client.userId,
                userName = client.userName,
                avatarColor = client.avatarColor,
                isOnline = client.isOnline,
                statusMessage = client.statusMessage,
                isDnd = client.isDnd,
                ipAddress = client.ip,
                port = port,
                isHub = client.userId == hubUserId
            )
        }
        allUsers.forEach { user ->
            val presenceJson = presenceAdapter.toJson(user)
            val payload = NetworkPayload(
                type = "PRESENCE",
                senderId = user.userId,
                senderName = user.userName,
                recipientId = "all",
                payloadJson = presenceJson
            )
            val json = payloadAdapter.toJson(payload)
            connectedClients.values.forEach { client ->
                try { client.session.send(Frame.Text(json)) } catch (_: Exception) {}
            }
        }
    }

    private suspend fun broadcastPresence(userId: String, isOnline: Boolean) {
        val user = PresenceData(
            userId = userId,
            userName = "",
            avatarColor = 0L,
            isOnline = isOnline,
            lastSeen = System.currentTimeMillis()
        )
        val payload = NetworkPayload(
            type = "PRESENCE",
            senderId = userId,
            senderName = "",
            recipientId = "all",
            payloadJson = presenceAdapter.toJson(user)
        )
        val json = payloadAdapter.toJson(payload)
        connectedClients.values.forEach { client ->
            try { client.session.send(Frame.Text(json)) } catch (_: Exception) {}
        }
    }

    suspend fun broadcastFromHub(payload: NetworkPayload) {
        val json = payloadAdapter.toJson(payload)
        val targetId = payload.recipientId
        if (targetId.isNullOrEmpty() || targetId == "all") {
            connectedClients.values.forEach { client ->
                try { client.session.send(Frame.Text(json)) } catch (_: Exception) {}
            }
        } else {
            connectedClients[targetId]?.let { client ->
                try { client.session.send(Frame.Text(json)) } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            connectedClients.clear()
            isRunning = false
            Log.d(tag, "HomeLink server stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping server", e)
        }
    }
}
