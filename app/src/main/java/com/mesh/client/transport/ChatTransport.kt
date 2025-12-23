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
    }

    var messageListener: MessageListener? = null

    init {
        // Register as listener
        webSocketService.listener = this
        webRtcManager.listener = this
    }

    fun sendMessage(peerId: String, plaintext: String) {
        try {
            // 1. Encrypt
            val encrypted = cryptoManager.encryptMessage(plaintext, peerId)

            // 2. Check Transport
            if (webRtcManager.isConnected(peerId)) {
                Log.d(TAG, "Sending via P2P to $peerId")
                val sent = webRtcManager.sendP2PMessage(peerId, encrypted)
                if (!sent) {
                    // Fallback if send failed despite state check
                    Log.w(TAG, "P2P send failed, fallback to server")
                    webSocketService.sendEncryptedMessage(peerId, encrypted)
                }
            } else {
                Log.d(TAG, "Sending via Server to $peerId")
                webSocketService.sendEncryptedMessage(peerId, encrypted)
                // Try to establish P2P for future
                webRtcManager.connectToPeer(peerId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
        }
    }

    // WebSocketService.Listener
    override fun onSignalingMessage(fromMeshId: String, type: String, payload: String?) {
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
            val plaintext = cryptoManager.decryptMessage(message, fromMeshId)
            messageListener?.onMessageReceived(fromMeshId, plaintext, message.timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed from $fromMeshId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ChatTransport"
    }
}
