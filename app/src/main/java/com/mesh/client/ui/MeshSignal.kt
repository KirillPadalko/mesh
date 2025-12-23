package com.mesh.client.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshSignalIcon(
    level: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Cyan // Default neon color
) {
    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val cy = h / 2
            val r = (kotlin.math.min(w, h) / 2) * if (level == 5) scale else 1.0f // Only level 5 pulses size aggressively? Or all? SPEC: "pulsating weak neon glow"

            // Glow effect (simplified as alpha circle behind)
            drawCircle(
                color = color.copy(alpha = alpha * 0.3f),
                radius = r * 1.2f
            )

            // Main Shape
            val shapeColor = if (level == 5) Color.Yellow else color // Level 5 different color
            
            when (level) {
                0 -> { // Dot
                    drawCircle(shapeColor, radius = r * 0.2f)
                }
                1 -> { // Two dots
                    drawCircle(shapeColor, radius = r * 0.15f, center = Offset(cx - r*0.4f, cy))
                    drawCircle(shapeColor, radius = r * 0.15f, center = Offset(cx + r*0.4f, cy))
                }
                2 -> { // Triangle
                    val path = Path().apply {
                        moveTo(cx, cy - r * 0.6f)
                        lineTo(cx + r * 0.6f, cy + r * 0.4f)
                        lineTo(cx - r * 0.6f, cy + r * 0.4f)
                        close()
                    }
                    drawPath(path, shapeColor)
                }
                3 -> { // Square
                    drawRect(
                        color = shapeColor,
                        topLeft = Offset(cx - r * 0.4f, cy - r * 0.4f),
                        size = androidx.compose.ui.geometry.Size(r * 0.8f, r * 0.8f)
                    )
                }
                4 -> { // Pentagon
                    val path = Path()
                    val sides = 5
                    val radius = r * 0.6f
                    for (i in 0 until sides) {
                        val angle = (i * 2 * Math.PI / sides) - (Math.PI / 2)
                        val x = cx + radius * cos(angle).toFloat()
                        val y = cy + radius * sin(angle).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, shapeColor)
                }
                5 -> { // Super Dot
                    drawCircle(shapeColor, radius = r * 0.4f)
                    // Extra glow ring
                    drawCircle(shapeColor, radius = r * 0.6f, style = Stroke(width = 2.dp.toPx()))
                }
                else -> { // Fallback dot
                     drawCircle(Color.Gray, radius = r * 0.2f)
                }
            }
        }
    }
}
