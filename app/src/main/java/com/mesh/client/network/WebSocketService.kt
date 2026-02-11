package com.mesh.client.network

import android.util.Log
import com.google.gson.Gson
import com.mesh.client.data.EncryptedMessage
import com.mesh.client.data.ServerMessage
import com.mesh.client.data.SignalingMessage
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketService(
    private val serverUrl: String,
    private val myMeshId: String,
    private val clientVersion: String = "0.1.0"
) : WebSocketListener() {

    interface Listener {
        fun onSignalingMessage(fromMeshId: String, type: String, payload: String?)
        fun onEncryptedMessageReceived(fromMeshId: String, message: EncryptedMessage)
        fun onDeliveryAck(msgId: String, status: String) // New
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .pingInterval(java.time.Duration.ofSeconds(10))
        .build()
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    var listener: Listener? = null
    
    private var isExplicitDisconnect = false
    private var reconnectAttempt = 0
    private val RECONNECT_MAX_DELAY = 60_000L
    private val RECONNECT_BASE_DELAY = 2_000L
    private val CONNECTION_TIMEOUT = 15_000L // 15 seconds grace period

    private val messageQueue = java.util.concurrent.LinkedBlockingQueue<String>()
    private var isConnected = false
    private var lastMessageTime = 0L

    fun connect() {
        if (webSocket != null) return
        isExplicitDisconnect = false
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun disconnect() {
        isExplicitDisconnect = true
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Connected to $serverUrl")
        reconnectAttempt = 0
        isConnected = true
        
        // 1. Authenticate
        val auth = SignalingMessage(
            type = "auth",
            userId = myMeshId,
            clientVersion = clientVersion
        )
        webSocket.send(gson.toJson(auth))
        
        // 2. Flush Queue
        while (messageQueue.isNotEmpty()) {
            val pending = messageQueue.poll()
            if (pending != null) {
                Log.d(TAG, "Flushing queued message (${pending.length} bytes)")
                webSocket.send(pending)
            }
        }
        
        listener?.onConnected()
    }
    
    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            lastMessageTime = System.currentTimeMillis() // Track message for connection health
            
            // Very low level check for keepalives
            val trimmed = text.trim()
            if (trimmed == "ping" || trimmed == "pong" || trimmed == "{\"type\":\"ping\"}" || trimmed == "{\"type\":\"pong\"}") {
                return
            }
            
            Log.d(TAG, "RAW WS MSG: ${if (text.length > 500) text.take(500) + "..." else text}")
            
            // Avoid parsing twice if possible, but for dynamic dispatch we check type first
            val jsonObject = gson.fromJson(text, com.google.gson.JsonObject::class.java)
            val msgType = jsonObject.get("type")?.asString
            
            Log.d(TAG, "Msg Type Identified: $msgType")
            
            when (msgType) {
                "error" -> {
                    val error = jsonObject.get("error")?.asString ?: "unknown"
                    if (error == "recipient_offline") {
                         Log.i(TAG, "Recipient offline - server should handle storage.")
                         return
                    }
                    val message = jsonObject.get("message")?.asString ?: "Server error"
                    Log.w(TAG, "Server error details: $error - $message")
                    listener?.onError(message)
                }
                "delivery_ack" -> {
                    val msgId = jsonObject.get("msg_id")?.asString
                    val status = jsonObject.get("status")?.asString
                    Log.d(TAG, "Received Delivery ACK: $msgId ($status)")
                    if (msgId != null && status != null) {
                        listener?.onDeliveryAck(msgId, status)
                    }
                }
                "server_message" -> {
                    Log.d(TAG, "Processing server_message carrier")
                    val msg = gson.fromJson(text, ServerMessage::class.java)
                    if (msg.payload.isNullOrBlank()) {
                        Log.e(TAG, "Received empty server_message payload from ${msg.from}")
                        return
                    }
                    try {
                        val encryptedJson = String(android.util.Base64.decode(msg.payload, android.util.Base64.NO_WRAP))
                        val encryptedMsg = gson.fromJson(encryptedJson, EncryptedMessage::class.java)
                        Log.d(TAG, "Success: EncryptedMessage from ${msg.from} decrypted locally, passing to listener")
                        listener?.onEncryptedMessageReceived(msg.from, encryptedMsg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Critical failure decoding server_message payload: ${e.message}")
                        // Consider logging the raw payload (carefully) if this persists
                    }
                }
                "auth_success" -> {
                    val userId = jsonObject.get("user_id")?.asString
                    Log.i(TAG, "Auth successful for user: $userId")
                    // Do NOT pass this to signaling listener
                }
                else -> {
                    // This handles signaling (offer, answer, ice) and any other types
                    Log.d(TAG, "Handling as signaling/generic message: $msgType")
                    val sig = gson.fromJson(text, SignalingMessage::class.java)
                    val senderId = jsonObject.get("from")?.asString ?: sig.userId ?: "unknown"
                    listener?.onSignalingMessage(senderId, sig.type, sig.payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Global onMessage parsing error", e)
            Log.e(TAG, "Faulty text: ${text.take(200)}")
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Disconnected: $reason")
        this.webSocket = null
        this.isConnected = false
        listener?.onDisconnected()
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Connection failure: ${t.message}")
        this.webSocket = null
        this.isConnected = false
        listener?.onDisconnected()
        scheduleReconnect()
    }
    
    private fun scheduleReconnect() {
        if (isExplicitDisconnect) return
        
        val delay = (RECONNECT_BASE_DELAY * (1L shl reconnectAttempt.coerceAtMost(30))).coerceAtMost(RECONNECT_MAX_DELAY)
        Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt ${reconnectAttempt + 1})")
        
        handler.postDelayed({
            if (!isExplicitDisconnect) {
                // Check if we're trying to reconnect while actually connected
                val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime
                if (isConnected && timeSinceLastMessage < CONNECTION_TIMEOUT) {
                    Log.d(TAG, "Skipping reconnect - connection is alive (last message ${timeSinceLastMessage}ms ago)")
                    reconnectAttempt = 0
                    return@postDelayed
                }
                
                Log.d(TAG, "Attempting reconnection...")
                connect()
            }
        }, delay)
        
        reconnectAttempt++
    }

    fun sendSignaling(toMeshId: String, type: String, payload: String?) {
        val map = mapOf(
            "to" to toMeshId,
            "from" to myMeshId,
            "type" to type,
            "payload" to payload
        )
        val json = gson.toJson(map)
        Log.d(TAG, "Outgoing Signaling: type=$type to ${toMeshId.take(8)}")
        sendRaw(json)
    }

    fun reconnect() {
        Log.d(TAG, "Forcing reconnect...")
        disconnect() // Clean up existing
        isExplicitDisconnect = false // Reset flag since disconnect() sets it to true
        connect()
    }

    private fun sendRaw(json: String): Boolean {
        if (isConnected && webSocket != null) {
            return webSocket?.send(json) ?: false
        } else {
            Log.i(TAG, "Socket not ready, queuing message (${json.length} bytes)")
            messageQueue.offer(json)
            
            // If we are not connected, try to connect immediately
            if (!isConnected) {
                Log.d(TAG, "Triggering connection for queued message")
                // We don't call reconnect() here to avoid loops, just connect() which is safe
                handler.post { connect() }
            }
            return true 
        }
    }

    fun sendEncryptedMessage(toMeshId: String, message: EncryptedMessage, msgId: String? = null): Boolean {
        Log.d(TAG, "sendEncryptedMessage to $toMeshId (ID: ${msgId?.take(8)})")
        val msgJson = gson.toJson(message)
        val payloadBase64 = android.util.Base64.encodeToString(msgJson.toByteArray(), android.util.Base64.NO_WRAP)
        
        val serverMsg = ServerMessage(
            from = myMeshId,
            to = toMeshId,
            payload = payloadBase64,
            msgId = msgId
        )
        val serverMsgJson = gson.toJson(serverMsg)
        return sendRaw(serverMsgJson)
    }

    companion object {
        private const val TAG = "WebSocketService"
    }
}
