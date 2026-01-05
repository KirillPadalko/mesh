package com.mesh.client.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- NEW THEME ---
private val NeonMeshScheme = darkColorScheme(
    primary = NeonWhite,      // Main actions (Buttons) are now Stark White
    onPrimary = Color.Black,  // Text on buttons is Black
    secondary = NeonBlue,
    tertiary = NeonGlow,
    background = VoidBlack,   // Deep dark background
    surface = SurfaceDark,    // Cards/Dialogs
    onBackground = NeonWhite, // Text on background
    onSurface = NeonWhite     // Text on cards
)

// --- LEGACY THEME (Rollback) ---
private val GreenMeshScheme = darkColorScheme(
    primary = MeshGreen,
    secondary = MeshGreenLight,
    tertiary = MeshGreenDark,
    background = LegacyBackground,
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun MeshTheme(
    // Toggle this boolean to true to revert to the old design instantly
    useLegacyTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useLegacyTheme) GreenMeshScheme else NeonMeshScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
