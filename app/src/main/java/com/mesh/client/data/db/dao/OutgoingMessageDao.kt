package com.mesh.client.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mesh.client.data.db.entities.OutgoingMessageEntity

@Dao
interface OutgoingMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutgoingMessageEntity)

    @Query("SELECT * FROM outgoing_queue ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<OutgoingMessageEntity>

    @Query("DELETE FROM outgoing_queue WHERE msgId = :msgId")
    suspend fun delete(msgId: String)

    @Query("UPDATE outgoing_queue SET retryCount = retryCount + 1 WHERE msgId = :msgId")
    suspend fun incrementRetry(msgId: String)
}
