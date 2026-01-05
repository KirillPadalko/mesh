package com.mesh.client.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val peerId: String,       // The contact this message is associated with
    val isIncoming: Boolean,
    val text: String,
    val timestamp: Long,
    val status: String = "sent" // sent, delivered, read, failed
)
