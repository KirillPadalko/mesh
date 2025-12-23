package com.mesh.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshMapScreen(
    viewModel: MeshViewModel,
    onBack: () -> Unit
) {
    val l1 by viewModel.l1Items.collectAsState()
    val l2 by viewModel.l2Items.collectAsState()
    val score by viewModel.meshScore.collectAsState()
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Network Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF101010)) // Dark bg
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2 + offset.x
                val cy = size.height / 2 + offset.y
                
                // Draw Connections First (Background)
                
                // Me -> L1
                val l1Radius = 300f * scale
                val l1Count = l1.size
                
                val l1Positions = mutableMapOf<String, Offset>()
                
                l1.forEachIndexed { index, id ->
                    val angle = (2 * Math.PI * index / l1Count.coerceAtLeast(1)) - (Math.PI / 2)
                    val x = cx + l1Radius * cos(angle).toFloat()
                    val y = cy + l1Radius * sin(angle).toFloat()
                    l1Positions[id] = Offset(x, y)
                    
                    // Line Me -> L1
                    drawLine(
                        color = Color.Cyan.copy(alpha = 0.5f),
                        start = Offset(cx, cy),
                        end = Offset(x, y),
                        strokeWidth = 2f * scale
                    )
                }
                
                // L1 -> L2
                val l2RadiusOffset = 200f * scale
                // Simple visualization: L2s radiate from their L1 parent
                
                l2.forEach { (l1Id, l2Set) ->
                    val parentPos = l1Positions[l1Id] ?: return@forEach
                    val count = l2Set.size
                    
                    l2Set.forEachIndexed { i, l2Id ->
                         // Fan out from parent, away from center
                         // Vector Center -> Parent
                         val vx = parentPos.x - cx
                         val vy = parentPos.y - cy
                         val baseAngle = kotlin.math.atan2(vy, vx)
                         
                         // Spread angle
                         val spread = 1.0 // radians
                         val subAngle = baseAngle - (spread/2) + (spread * i / count.coerceAtLeast(1))
                         
                         val l2x = parentPos.x + l2RadiusOffset * cos(subAngle).toFloat()
                         val l2y = parentPos.y + l2RadiusOffset * sin(subAngle).toFloat()
                         
                         drawLine(
                            color = Color.Magenta.copy(alpha = 0.3f),
                            start = parentPos,
                            end = Offset(l2x, l2y),
                            strokeWidth = 1f * scale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        
                        // Draw L2 Node
                        drawCircle(Color.Magenta, radius = 5f * scale, center = Offset(l2x, l2y))
                    }
                }
                
                // Draw Nodes
                
                // L1 Nodes
                l1Positions.values.forEach { pos ->
                    drawCircle(Color.Cyan, radius = 10f * scale, center = pos)
                    drawCircle(Color.White, radius = 12f * scale, center = pos, style = Stroke(width=2f))
                }
                
                // Me (Center)
                val myRadius = (15f + score.toFloat()) * scale
                drawCircle(Color.Green, radius = myRadius, center = Offset(cx, cy))
                // Glow
                drawCircle(Color.Green.copy(alpha=0.2f), radius = myRadius * 1.5f, center = Offset(cx, cy))
            }
            
            // Current Score Overlay
            Card(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.8f))
            ) {
                Text(
                    text = "Nodes: ${1 + l1.size + l2.values.sumOf { it.size }}", 
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
