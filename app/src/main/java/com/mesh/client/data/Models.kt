package com.mesh.client.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * The end-to-end encrypted payload.
 */
@Keep
data class EncryptedMessage(
    @SerializedName("ciphertext") val ciphertext: String, // Base64
    @SerializedName("nonce") val nonce: String,           // Base64
    @SerializedName("timestamp") val timestamp: Long
)

/**
 * WebSocket signaling encapsulation.
 */
@Keep
data class SignalingMessage(
    @SerializedName("type") val type: String,               // "auth", "offer", "answer", "ice"
    @SerializedName("payload") val payload: String? = null, // SDP or ICE candidate JSON
    @SerializedName("user_id") val userId: String? = null,  // For auth (Server expects "user_id")
    @SerializedName("client_version") val clientVersion: String? = null // For auth
)

/**
 * Fallback message routed via server.
 */
@Keep
data class ServerMessage(
    @SerializedName("type") val type: String = "server_message",
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("payload") val payload: String, // Base64 of serialized EncryptedMessage
    @SerializedName("msg_id") val msgId: String? = null // For ACK tracking
)

/**
 * Decrypted payload wrapper to distinguish content types.
 * This is what gets encrypted inside EncryptedMessage.
 */
@Keep
data class ProtocolPayload(
    @SerializedName("type") val type: String, // "chat", "invite", "invite_ack", "l2_notify", "call", "ack"
    @SerializedName("content") val content: String, // JSON payload or plain text
    @SerializedName("msg_id") val msgId: String = java.util.UUID.randomUUID().toString() // Unique tracking ID
)
