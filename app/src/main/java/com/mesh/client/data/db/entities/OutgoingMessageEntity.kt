package com.mesh.client.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outgoing_queue")
data class OutgoingMessageEntity(
    @PrimaryKey val msgId: String,
    val toPeerId: String,
    val encryptedJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val status: String = "pending" // pending, sent
)
