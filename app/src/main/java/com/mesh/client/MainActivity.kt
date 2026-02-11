package com.mesh.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mesh.client.ui.MeshApp
import com.mesh.client.ui.theme.MeshTheme
import androidx.compose.foundation.layout.fillMaxSize

import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import com.mesh.client.viewmodel.MeshViewModel
// import com.mesh.client.updates.UpdateManager
// import androidx.lifecycle.lifecycleScope
// import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-update disabled (requires REQUEST_INSTALL_PACKAGES permission)
        // Updates will be handled by Google Play Store
        // val updateManager = UpdateManager(this)
        // lifecycleScope.launch {
        //     updateManager.checkForUpdates()
        // }

        // Request Permissions
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.RECORD_AUDIO)
        
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 101)
        }
        
        val viewModel = ViewModelProvider(this)[MeshViewModel::class.java]
        handleIntent(intent, viewModel)
        
        setContent {
            MeshTheme {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    MeshApp(viewModel)
                }
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
