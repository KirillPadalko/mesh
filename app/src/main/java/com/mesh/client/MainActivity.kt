package com.mesh.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mesh.client.ui.MeshApp
import com.mesh.client.ui.theme.MeshTheme

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import com.mesh.client.viewmodel.MeshViewModel
import com.mesh.client.updates.UpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for updates on startup
        val updateManager = UpdateManager(this)
        lifecycleScope.launch {
            updateManager.checkForUpdates()
        }
        
        val viewModel = ViewModelProvider(this)[MeshViewModel::class.java]
        handleIntent(intent, viewModel)
        
        setContent {
            MeshTheme {
                MeshApp(viewModel)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val viewModel = ViewModelProvider(this)[MeshViewModel::class.java]
        handleIntent(intent, viewModel)
    }
    
    private fun handleIntent(intent: Intent?, viewModel: MeshViewModel) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data?.scheme == "mesh" && data.host == "invite") {
                // Path is /<peerId>
                val peerId = data.path?.substring(1) // remove leading /
                if (!peerId.isNullOrBlank()) {
                    viewModel.handleInvite(peerId)
                }
            }
        }
    }
}
