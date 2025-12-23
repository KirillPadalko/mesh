package com.mesh.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mesh.client.viewmodel.MeshViewModel

@Composable
fun MeshApp(viewModel: MeshViewModel = viewModel()) {
    val navController = rememberNavController()
    // val viewModel: MeshViewModel = viewModel() // Use passed instance
    val meshId by viewModel.meshId.collectAsState()

    // Determine start destination based on identity presence
    // Ideally Splash handles this, but for simple setup:
    val startRoute = if (meshId != null) "home" else "intro"

    NavHost(navController = navController, startDestination = "splash") {
        
        composable("splash") {
            SplashScreen(
                onIdentityFound = { navController.navigate("home") { popUpTo("splash") { inclusive = true } } },
                onIdentityMissing = { navController.navigate("intro") { popUpTo("splash") { inclusive = true } } },
                viewModel = viewModel
            )
        }

        composable("intro") {
            OnboardingScreen(
                viewModel = viewModel,
                onComplete = { 
                    navController.navigate("home") { popUpTo("intro") { inclusive = true } }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onChatSelected = { peerId -> navController.navigate("chat/$peerId") },
                onProfileSelected = { navController.navigate("profile") }
            )
        }

        composable("chat/{peerId}") { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            ChatScreen(
                peerId = peerId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("profile") {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMap = { navController.navigate("map") }
            )
        }
        
        composable("map") {
             MeshMapScreen(
                 viewModel = viewModel,
                 onBack = { navController.popBackStack() }
             )
        }
    }
}
