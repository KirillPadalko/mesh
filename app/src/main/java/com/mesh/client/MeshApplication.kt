package com.mesh.client

import android.app.Application
import com.mesh.client.data.db.AppDatabase

class MeshApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
    }
}
