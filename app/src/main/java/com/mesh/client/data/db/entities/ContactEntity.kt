package com.mesh.client.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val meshId: String,
    val nickname: String,
    // Future fields: publicKey, avatar, etc.
)
