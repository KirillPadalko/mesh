package com.mesh.client.data.protocol

import com.google.gson.annotations.SerializedName

/**
 * P2P Protocol definitions for Mesh Invite & Score System.
 * See SCHEMAS.md for details.
 */

data class Invite(
    @SerializedName("type") val type: String = "invite",
    @SerializedName("from") val from: String, // Mesh-ID (Base58)
    @SerializedName("to") val to: String,     // Mesh-ID (Base58)
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("signature") val signature: String, // Hex
    @SerializedName("parent_invite") val parentInvite: Invite? = null
)

data class InviteAck(
    @SerializedName("type") val type: String = "invite_ack",
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("invite_hash") val inviteHash: String, // SHA-256 Hex of the Invite JSON
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("signature") val signature: String // Hex
)

data class L2Notify(
    @SerializedName("type") val type: String = "l2_notify",
    @SerializedName("origin") val origin: String, // The new node (C)
    @SerializedName("via") val via: String,       // The intermediary (B)
    @SerializedName("proof") val proof: ProofChain,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("signature") val signature: String // Hex
)

data class ProofChain(
    @SerializedName("invite_ab") val inviteAB: Invite,
    @SerializedName("invite_bc") val inviteBC: Invite
    // Optional: @SerializedName("ack_cb") val ackCB: InviteAck? = null
)
