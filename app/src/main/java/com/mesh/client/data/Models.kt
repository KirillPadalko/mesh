package com.mesh.client.data

import com.google.gson.annotations.SerializedName

/**
 * The end-to-end encrypted payload.
 */
data class EncryptedMessage(
    @SerializedName("ciphertext") val ciphertext: String, // Base64
    @SerializedName("nonce") val nonce: String,           // Base64
    @SerializedName("timestamp") val timestamp: Long
)

/**
 * WebSocket signaling encapsulation.
 */
data class SignalingMessage(
    @SerializedName("type") val type: String,               // "auth", "offer", "answer", "ice"
    @SerializedName("payload") val payload: String? = null, // SDP or ICE candidate JSON
    @SerializedName("mesh_id") val meshId: String? = null,  // For auth
    @SerializedName("client_version") val clientVersion: String? = null // For auth
)

/**
 * Fallback message routed via server.
 */
data class ServerMessage(
    @SerializedName("type") val type: String = "server_message",
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("payload") val payload: String // Base64 of serialized EncryptedMessage
)
