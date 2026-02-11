package com.mesh.client.transport

import android.util.Log
import com.mesh.client.crypto.CryptoManager
import com.mesh.client.data.EncryptedMessage
import com.mesh.client.network.WebRtcManager
import com.mesh.client.network.WebSocketService
import kotlinx.coroutines.launch

class ChatTransport(
    private val cryptoManager: CryptoManager,
    val webRtcManager: WebRtcManager,
    private val webSocketService: WebSocketService,
    private val myMeshId: String,
    private val outgoingDao: com.mesh.client.data.db.dao.OutgoingMessageDao,
    private val scope: kotlinx.coroutines.CoroutineScope
) : WebSocketService.Listener, WebRtcManager.Listener {

    interface MessageListener {
        fun onMessageReceived(fromMeshId: String, text: String, timestamp: Long, transportType: String)
        fun onMessageStatusChanged(peerId: String, isP2P: Boolean)
        
        // Protocol Events
        fun onInviteReceived(fromMeshId: String, inviteJson: String)
        fun onInviteAckReceived(fromMeshId: String, ackJson: String)
        fun onL2NotifyReceived(fromMeshId: String, notifyJson: String)
        
        fun onTransportError(message: String)
        fun onIncomingCall(peerId: String)
        fun onHangupReceived(peerId: String)
        fun onMessageDelivered(msgId: String) // New: for UI feedback
    }

    private val gson = com.google.gson.Gson()
    var messageListener: MessageListener? = null
    private var isConnected = false

    init {
        webRtcManager.listener = this
    }

    // --- Public Sending API ---
    
    fun sendMessage(peerId: String, plaintext: String) {
        val payload = com.mesh.client.data.ProtocolPayload("chat", plaintext)
        enqueueMessage(peerId, payload)
    }

    fun startCall(peerId: String) {
        // Ephemeral: try P2P first, then Server. No persistence.
        val payload = com.mesh.client.data.ProtocolPayload("call", "audio")
        sendEphemeral(peerId, payload)
        
        // Then establish WebRTC with audio track
        webRtcManager.startCall(peerId)
    }

    fun sendHangup(peerId: String) {
        val payload = com.mesh.client.data.ProtocolPayload("hangup", "ended")
        sendEphemeral(peerId, payload)
        webRtcManager.stopCall(peerId)
    }
    
    fun sendInvite(peerId: String, inviteJson: String) {
        // Invites should be persistent
        val payload = com.mesh.client.data.ProtocolPayload("invite", inviteJson)
        enqueueMessage(peerId, payload)
    }
    
    fun sendInviteAck(peerId: String, ackJson: String) {
        // ACKs can be ephemeral or persistent. Let's make invite ACKs persistent to be safe.
        val payload = com.mesh.client.data.ProtocolPayload("invite_ack", ackJson)
        enqueueMessage(peerId, payload)
    }

    private fun enqueueMessage(peerId: String, payload: com.mesh.client.data.ProtocolPayload) {
        scope.launch {
            try {
                val payloadJson = gson.toJson(payload)
                val encrypted = cryptoManager.encryptMessage(payloadJson, peerId)
                val encryptedJson = gson.toJson(encrypted)
                
                val entity = com.mesh.client.data.db.entities.OutgoingMessageEntity(
                    msgId = payload.msgId,
                    toPeerId = peerId,
                    encryptedJson = encryptedJson,
                    createdAt = System.currentTimeMillis()
                )
                outgoingDao.insert(entity)
                Log.d(TAG, "Enqueued message ${payload.msgId.take(8)} for $peerId")
                
                // Trigger sending immediately
                processOutbox()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue message", e)
                messageListener?.onTransportError("Failed to encrypt/queue message")
            }
        }
    }
    
    fun processOutbox() {
        scope.launch {
            try {
                val pending = outgoingDao.getAllPending()
                if (pending.isEmpty()) return@launch
                
                Log.d(TAG, "Processing outbox: ${pending.size} messages")
                
                for (msg in pending) {
                    // Strategy: Server-First for reliability
                    // We send "server_message" via WebSocket.
                    
                    if (!isConnected) {
                        Log.d(TAG, "Cannot process outbox - WS disconnected")
                        break // Stop processing if no connection
                    }

                    try {
                        val encrypted = gson.fromJson(msg.encryptedJson, EncryptedMessage::class.java)
                        
                        // Send via Server (Reliable channel)
                        // Note: we pass msgId to the server envelope so we can get a persistent ACK
                        val success = webSocketService.sendEncryptedMessage(msg.toPeerId, encrypted, msg.msgId)
                        
                        if (success) {
                            Log.d(TAG, "Sent pending ${msg.msgId.take(8)} to server, waiting for release")
                            // We do NOT delete here. We wait for delivery_ack or offline_storage confirmation from server.
                        } else {
                            Log.w(TAG, "Failed to write ${msg.msgId.take(8)} to WS buffer")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending pending message ${msg.msgId}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing outbox", e)
            }
        }
    }

    private fun sendEphemeral(peerId: String, payload: com.mesh.client.data.ProtocolPayload) {
        Log.i(TAG, "Sending ephemeral [${payload.type}] ID=${payload.msgId.take(8)} to $peerId")
        try {
            val payloadJson = gson.toJson(payload)
            val encrypted = cryptoManager.encryptMessage(payloadJson, peerId)

            // Try P2P first for ephemeral (latency sensitive)
            if (webRtcManager.isConnected(peerId)) {
                val sent = webRtcManager.sendP2PMessage(peerId, encrypted)
                if (!sent) webSocketService.sendEncryptedMessage(peerId, encrypted)
            } else {
                webSocketService.sendEncryptedMessage(peerId, encrypted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ephemeral", e)
        }
    }

    // WebSocketService.Listener
    override fun onSignalingMessage(fromMeshId: String, type: String, payload: String?) {
        Log.d(TAG, "Signal [${type}] from ${fromMeshId.take(8)}")
        try {
            if (type == "error") return
            webRtcManager.handleSignaling(fromMeshId, type, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling signaling", e)
        }
    }

    override fun onEncryptedMessageReceived(fromMeshId: String, message: EncryptedMessage) {
        handleIncomingMessage(fromMeshId, message, "SERVER")
    }
    
    override fun onDeliveryAck(msgId: String, status: String) {
        handleServerAck(msgId, status)
    }
    
    override fun onError(message: String) {
        Log.e(TAG, "Transport Error: $message")
        messageListener?.onTransportError(message)
    }

    override fun onConnected() {
        Log.i(TAG, "Transport connected to Mesh Server")
        isConnected = true
        // Flush queue on reconnect
        processOutbox()
    }

    override fun onDisconnected() {
        Log.w(TAG, "Transport disconnected from Mesh Server")
        isConnected = false
    }

    // WebRtcManager.Listener
    override fun onP2PMessageReceived(fromMeshId: String, message: EncryptedMessage) {
        handleIncomingMessage(fromMeshId, message, "P2P")
    }

    override fun onP2PConnectionStateChange(peerId: String, isConnected: Boolean) {
        Log.i(TAG, "P2P State for ${peerId.take(8)}: $isConnected")
        messageListener?.onMessageStatusChanged(peerId, isConnected)
    }

    private fun handleIncomingMessage(fromMeshId: String, message: EncryptedMessage, source: String) {
        // Decrypt... same as before
        // But also check for delivery_ack inside? 
        // No, delivery_ack comes as a separate signaling/protocol message usually, 
        // OR as a payload inside an encrypted message?
        // Wait, main.py sends 'delivery_ack' as a personal message (JSON).
        // It is NOT encrypted as part of 'server_message' payload.
        // It is a top-level message type like 'webrtc_offer'.
        // So it comes via onSignalingMessage NO wait.
        
        // main.py calls manager.send_personal_message(ack).
        // ACK is { type: "delivery_ack", ... }
        // WebSocketService.kt sees "delivery_ack" in msgType.
        // It falls into 'else' -> onSignalingMessage.
        // So we need to handle "delivery_ack" in onSignalingMessage!
        
        try {
            val decryptedJson = cryptoManager.decryptMessage(message, fromMeshId)
            val payload = try {
                gson.fromJson(decryptedJson, com.mesh.client.data.ProtocolPayload::class.java)
            } catch (e: Exception) {
                com.mesh.client.data.ProtocolPayload("chat", decryptedJson)
            }

            Log.i(TAG, "Incoming [${payload.type}] ID=${payload.msgId.take(8)} from ${fromMeshId.take(8)}")
            
            // Auto-ACK for chat messages (Application Level ACK) purely for read receipt later
            // For now we rely on Transport ACK.

            when (payload.type) {
                "chat" -> messageListener?.onMessageReceived(fromMeshId, payload.content, message.timestamp, source.lowercase())
                "invite" -> messageListener?.onInviteReceived(fromMeshId, payload.content)
                "invite_ack" -> messageListener?.onInviteAckReceived(fromMeshId, payload.content)
                "l2_notify" -> messageListener?.onL2NotifyReceived(fromMeshId, payload.content)
                "call" -> messageListener?.onIncomingCall(fromMeshId)
                "hangup" -> {
                    webRtcManager.stopCall(fromMeshId)
                    messageListener?.onHangupReceived(fromMeshId)
                }
                else -> Log.w(TAG, "Unknown protocol type: ${payload.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process incoming message", e)
        }
    }
    
    // We also need to handle the Server Delivery ACK which comes via onSignalingMessage/Protocol
    // See comment above.
    
    fun handleServerAck(msgId: String, status: String) {
        scope.launch {
            Log.d(TAG, "Server ACK for $msgId: $status")
            // If delivered or stored_offline, we consider it "sent" from client perspective.
            // We remove from outbox.
            outgoingDao.delete(msgId)
            messageListener?.onMessageDelivered(msgId)
        }
    }

    companion object {
        private const val TAG = "ChatTransport"
    }
}
