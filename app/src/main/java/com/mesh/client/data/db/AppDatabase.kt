package com.mesh.client.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mesh.client.data.db.dao.ContactDao
import com.mesh.client.data.db.dao.MessageDao
import com.mesh.client.data.db.entities.ContactEntity
import com.mesh.client.data.db.entities.MessageEntity

@Database(entities = [ContactEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mesh_database"
                )
                .fallbackToDestructiveMigration() // For MVP simplicity
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
