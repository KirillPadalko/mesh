package com.mesh.client.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    peerId: String,
    viewModel: MeshViewModel
) {
    val contacts by viewModel.contacts.collectAsState()
    val contact = contacts.find { it.meshId == peerId }
    val strUserPrefix = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.user_prefix, peerId.take(4))
    val nickname = contact?.nickname ?: strUserPrefix
    
    val callState by viewModel.callState.collectAsState()
    
    // String resources
    val strIncomingCall = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.incoming_call)
    val strDecline = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.decline)
    val strAccept = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.accept)
    val strEndCall = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.end_call)

    Scaffold(
        containerColor = Color(0xFF0F1A30) // Matches logo background #0F1A30
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (callState == MeshViewModel.CallState.INCOMING) {
                 Text(
                    text = strIncomingCall,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(64.dp))
                MeshCallAnimation(modifier = Modifier.size(200.dp))
                Spacer(modifier = Modifier.height(64.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Decline
                    FilledIconButton(
                        onClick = { viewModel.declineCall() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Red)
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = strDecline,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Accept
                    FilledIconButton(
                        onClick = { viewModel.acceptCall() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Green)
                    ) {
                        Icon(
                            Icons.Default.Call, 
                            contentDescription = strAccept,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                // Active Call UI
                // Timer Logic
                var seconds by remember { mutableStateOf(0L) }
                LaunchedEffect(Unit) {
                    val startTime = System.currentTimeMillis()
                    while(true) {
                        val now = System.currentTimeMillis()
                        seconds = (now - startTime) / 1000
                        delay(500)
                    }
                }
                
                val formattedTime = remember(seconds) {
                    val m = (seconds / 60) % 60
                    val s = seconds % 60
                    String.format("%02d:%02d", m, s)
                }
            
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                MeshCallAnimation(modifier = Modifier.size(200.dp))
                Spacer(modifier = Modifier.height(64.dp))
                
                // End Call Button
                FilledIconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Red)
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = strEndCall,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MeshCallAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh params")
    val strMeshLogo = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.mesh_logo)
    
    // Animate scale/pulse
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
         // Logo
         // Removed border and glow to blend completely
         Surface(
             shape = CircleShape,
             color = Color(0xFF0F1A30), // Matches screen background
             modifier = Modifier
                 .fillMaxSize()
                 .graphicsLayer {
                     scaleX = scale
                     scaleY = scale
                 }
         ) {
             androidx.compose.foundation.Image(
                 painter = androidx.compose.ui.res.painterResource(id = com.mesh.client.R.drawable.call_logo),
                 contentDescription = strMeshLogo,
                 modifier = Modifier.fillMaxSize(), 
                 contentScale = androidx.compose.ui.layout.ContentScale.Fit
             )
         }
    }
}
