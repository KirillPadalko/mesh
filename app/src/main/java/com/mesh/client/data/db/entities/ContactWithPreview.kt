package com.mesh.client.data.db.entities

import androidx.room.Embedded

data class ContactWithPreview(
    @Embedded val contact: ContactEntity,
    val lastMessageText: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int
)
