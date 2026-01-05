package com.mesh.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mesh.client.ui.MeshApp
import com.mesh.client.ui.theme.MeshTheme

import android.content.Intent
import android.os.Build
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

        // Request Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
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
            val data = intent.data ?: return
            
            when {
                // Handle mesh://invite/{meshId} deep link
                data.scheme == "mesh" && data.host == "invite" -> {
                    val meshId = data.path?.substring(1) // remove leading /
                    val nickname = data.getQueryParameter("n") // Get nickname from query param
                    if (!meshId.isNullOrBlank()) {
                        viewModel.handleInvite(meshId, nickname)
                    }
                }
                // Handle http(s)://server/invite/{meshId}?v={version} universal link
                (data.scheme == "http" || data.scheme == "https") && 
                data.path?.startsWith("/invite/") == true -> {
                    val pathSegments = data.pathSegments
                    if (pathSegments.size >= 2 && pathSegments[0] == "invite") {
                        val meshId = pathSegments[1]
                        val nickname = data.getQueryParameter("n") // Get nickname from query
                        if (meshId.isNotBlank()) {
                            viewModel.handleInvite(meshId, nickname)
                        }
                    }
                }
            }
        }
    }
}
