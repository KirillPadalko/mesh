package com.mesh.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mesh.client.data.db.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("""
        SELECT 
            c.*, 
            m.text AS lastMessageText, 
            m.timestamp AS lastMessageTime,
            (SELECT COUNT(*) FROM messages WHERE peerId = c.meshId AND status = 'received') AS unreadCount
        FROM contacts c
        LEFT JOIN messages m ON m.peerId = c.meshId AND m.timestamp = (
            SELECT MAX(timestamp) FROM messages WHERE peerId = c.meshId
        )
        ORDER BY m.timestamp DESC
    """)
    fun getContactsWithPreview(): Flow<List<com.mesh.client.data.db.entities.ContactWithPreview>>

    @Query("SELECT * FROM contacts WHERE meshId = :meshId")
    suspend fun getContact(meshId: String): ContactEntity?

    @Query("DELETE FROM contacts WHERE meshId = :meshId")
    suspend fun deleteContact(meshId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)
}
