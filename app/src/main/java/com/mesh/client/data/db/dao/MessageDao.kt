package com.mesh.client.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mesh.client.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET status = 'read' WHERE peerId = :peerId AND status = 'received'")
    suspend fun markMessagesAsRead(peerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): MessageEntity?
}
