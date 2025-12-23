package com.mesh.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MeshViewModel,
    onBack: () -> Unit,
    onMap: () -> Unit
) {
    val meshId by viewModel.meshId.collectAsState()
    val meshScore by viewModel.meshScore.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showSeed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Identity Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Mesh Node", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayId = meshId ?: "Loading..."
                    Text(
                        text = displayId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            if (meshId != null) {
                                clipboardManager.setText(AnnotatedString(meshId!!))
                                android.widget.Toast.makeText(context, "ID Copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("(Tap to copy)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mesh Signal Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Mesh Signal", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val level = viewModel.getSignalLevel(meshScore)
                    MeshSignalIcon(
                        level = level,
                        modifier = Modifier.size(80.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Level $level",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Score: ${String.format("%.1f", meshScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                Text("View Network Map")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("Backup Identity") },
                supportingContent = { Text("Show your private seed phrase") },
                trailingContent = {
                    Button(onClick = { showSeed = !showSeed }) {
                        Text(if (showSeed) "Hide" else "Show")
                    }
                }
            )
            
            if (showSeed) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DO NOT SHARE THIS", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val backup = viewModel.identityManager.exportMnemonic()
                        if (backup != null) {
                            // Show mnemonic in grid
                            Text("Recovery Phrase:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            val words = backup.split(" ")
                            words.chunked(3).forEach { rowWords ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    rowWords.forEach { word ->
                                        val index = words.indexOf(word) + 1
                                        Text(
                                            text = "$index. $word",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Legacy hex seed
                            Text("Seed Hex (Legacy):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            val seedHex = try { viewModel.identityManager.exportSeedHex() } catch(e:Exception){"Error"}
                            SelectionContainer {
                                Text(seedHex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
            

        }
    }
}
