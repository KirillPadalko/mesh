package com.mesh.client.transport

import android.util.Log
import com.mesh.client.crypto.CryptoManager
import com.mesh.client.data.EncryptedMessage
import com.mesh.client.network.WebRtcManager
import com.mesh.client.network.WebSocketService

class ChatTransport(
    private val cryptoManager: CryptoManager,
    private val webRtcManager: WebRtcManager,
    private val webSocketService: WebSocketService,
    private val myMeshId: String
) : WebSocketService.Listener, WebRtcManager.Listener {

    interface MessageListener {
        fun onMessageReceived(fromMeshId: String, text: String, timestamp: Long)
        fun onMessageStatusChanged(peerId: String, isP2P: Boolean)
        
        // Protocol Events
        fun onInviteReceived(fromMeshId: String, inviteJson: String)
        fun onInviteAckReceived(fromMeshId: String, ackJson: String)
        fun onL2NotifyReceived(fromMeshId: String, notifyJson: String)
    }

    var messageListener: MessageListener? = null

    init {
        // Register as listener
        // Register as listener
        // webSocketService.listener = this // Moved to ViewModel to intercept connection status
        webRtcManager.listener = this
    }

    // --- Public Sending API ---
    
    fun sendMessage(peerId: String, plaintext: String) {
        val payload = com.mesh.client.data.ProtocolPayload("chat", plaintext)
        sendProtocolPayload(peerId, payload)
    }
    
    fun sendInvite(peerId: String, inviteJson: String) {
        val payload = com.mesh.client.data.ProtocolPayload("invite", inviteJson)
        sendProtocolPayload(peerId, payload)
    }
    
    fun sendInviteAck(peerId: String, ackJson: String) {
        val payload = com.mesh.client.data.ProtocolPayload("invite_ack", ackJson)
        sendProtocolPayload(peerId, payload)
    }

    private fun sendProtocolPayload(peerId: String, payload: com.mesh.client.data.ProtocolPayload) {
        try {
            val gson = com.google.gson.Gson()
            val payloadJson = gson.toJson(payload)
            
            // 1. Encrypt
            val encrypted = cryptoManager.encryptMessage(payloadJson, peerId)

            // 2. Check Transport
            if (webRtcManager.isConnected(peerId)) {
                Log.d(TAG, "Sending ${payload.type} via P2P to $peerId")
                val sent = webRtcManager.sendP2PMessage(peerId, encrypted)
                if (!sent) {
                    // Fallback if send failed despite state check
                    Log.w(TAG, "P2P send failed, fallback to server")
                    webSocketService.sendEncryptedMessage(peerId, encrypted)
                }
            } else {
                Log.d(TAG, "Sending ${payload.type} via Server to $peerId")
                webSocketService.sendEncryptedMessage(peerId, encrypted)
                // Try to establish P2P for future
                webRtcManager.connectToPeer(peerId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending protocol message", e)
        }
    }

    // WebSocketService.Listener
    override fun onSignalingMessage(fromMeshId: String, type: String, payload: String?) {
        if (type == "error") {
             Log.e(TAG, "Server Error: Peer $fromMeshId might be offline. Details: $payload")
             // TODO: Propagate to UI via callback
             // For now, log is sufficient for debugging "Silence"
             return
        }
        webRtcManager.handleSignaling(fromMeshId, type, payload)
    }

    override fun onEncryptedMessageReceived(fromMeshId: String, message: EncryptedMessage) {
        handleIncomingMessage(fromMeshId, message)
    }

    override fun onConnected() {
        Log.d(TAG, "WS Connected")
    }

    override fun onDisconnected() {
        Log.d(TAG, "WS Disconnected")
    }

    // WebRtcManager.Listener
    override fun onP2PMessageReceived(fromMeshId: String, message: EncryptedMessage) {
        handleIncomingMessage(fromMeshId, message)
    }

    override fun onP2PConnectionStateChange(peerId: String, isConnected: Boolean) {
        messageListener?.onMessageStatusChanged(peerId, isConnected)
    }

    private fun handleIncomingMessage(fromMeshId: String, message: EncryptedMessage) {
        try {
            val decryptedJson = cryptoManager.decryptMessage(message, fromMeshId)
            
            // Try parse as ProtocolPayload
            val gson = com.google.gson.Gson()
            try {
                val payload = gson.fromJson(decryptedJson, com.mesh.client.data.ProtocolPayload::class.java)
                when (payload.type) {
                    "chat" -> messageListener?.onMessageReceived(fromMeshId, payload.content, message.timestamp)
                    "invite" -> messageListener?.onInviteReceived(fromMeshId, payload.content)
                    "invite_ack" -> messageListener?.onInviteAckReceived(fromMeshId, payload.content)
                    "l2_notify" -> messageListener?.onL2NotifyReceived(fromMeshId, payload.content)
                    else -> Log.w(TAG, "Unknown protocol type: ${payload.type}")
                }
            } catch (e: Exception) {
                // Backward compatibility: assume plain text chat if parsing fails
                Log.w(TAG, "Failed to parse ProtocolPayload, assuming legacy chat: ${e.message}")
                messageListener?.onMessageReceived(fromMeshId, decryptedJson, message.timestamp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption or processing failed from $fromMeshId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ChatTransport"
    }
}
