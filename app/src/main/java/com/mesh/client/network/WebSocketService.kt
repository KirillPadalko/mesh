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
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS) // Send PING frame every 15s
        .build()
    
    // Manual handler not needed for native ping
    // private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    var listener: Listener? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Connected to $serverUrl")
        // Send Auth
        val auth = SignalingMessage(
            type = "auth",
            userId = myMeshId,
            clientVersion = clientVersion
        )
        webSocket.send(gson.toJson(auth))
        listener?.onConnected()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            // Ignore ping/pong messages
            if (text == "ping" || text == "pong") {
                return
            }
            
            Log.d(TAG, "Received message: ${text.take(100)}...")
            
            // Parse as generic JSON to check type
            val msgMap = gson.fromJson(text, Map::class.java) as? Map<*, *>
            val msgType = msgMap?.get("type") as? String
            
            when {
                msgType == "error" -> {
                    // Server error response (e.g., recipient offline)
                    val error = msgMap["error"] as? String ?: "unknown"
                    val message = msgMap["message"] as? String ?: "Server error"
                    Log.w(TAG, "Server error: $error - $message")
                    listener?.onError(message)
                }
                text.contains("server_message") -> {
                    val msg = gson.fromJson(text, ServerMessage::class.java)
                    // Payload is Base64 encoded JSON of EncryptedMessage
                    val encryptedJson = String(android.util.Base64.decode(msg.payload, android.util.Base64.NO_WRAP))
                    val encryptedMsg = gson.fromJson(encryptedJson, EncryptedMessage::class.java)
                    listener?.onEncryptedMessageReceived(msg.from, encryptedMsg)
                }
                else -> {
                    // Signaling or other message types
                    val sig = gson.fromJson(text, SignalingMessage::class.java)
                    listener?.onSignalingMessage(sig.userId ?: "unknown", sig.type, sig.payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Disconnected: $reason")
        handler.removeCallbacks(pingRunnable)
        listener?.onDisconnected()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Connection failure: ${t.message}")
        handler.removeCallbacks(pingRunnable)
        listener?.onDisconnected()
    }

    fun sendSignaling(toMeshId: String, type: String, payload: String?) {
        // Send a message that the server routes to `toMeshId`.
        // We'll wrap it in a structure the server understands for routing.
        // Assuming server expects { "to": "...", "type": "...", ... }
        // We will define a routed signaling packet or reuse ServerMessage structure for routing signaling too?
        // Prompt says "Server routes webrtc_offer...".
        // Let's use a generic structure:
        val map = mapOf(
            "to" to toMeshId,
            "type" to type,
            "payload" to payload
        )
        webSocket?.send(gson.toJson(map))
    }

    fun sendEncryptedMessage(toMeshId: String, message: EncryptedMessage) {
        Log.d(TAG, "sendEncryptedMessage to $toMeshId")
        val msgJson = gson.toJson(message)
        val payloadBase64 = android.util.Base64.encodeToString(msgJson.toByteArray(), android.util.Base64.NO_WRAP)
        
        val serverMsg = ServerMessage(
            from = myMeshId,
            to = toMeshId,
            payload = payloadBase64
        )
        val serverMsgJson = gson.toJson(serverMsg)
        Log.d(TAG, "Sending ServerMessage: from=${serverMsg.from}, to=${serverMsg.to}, payload_length=${payloadBase64.length}")
        
        val success = webSocket?.send(serverMsgJson) ?: false
        if (success) {
            Log.d(TAG, "Message queued to WebSocket successfully")
        } else {
            Log.e(TAG, "Failed to send message - WebSocket is ${if (webSocket == null) "null" else "closed/failed"}")
        }
    }

    companion object {
        private const val TAG = "WebSocketService"
    }
}
