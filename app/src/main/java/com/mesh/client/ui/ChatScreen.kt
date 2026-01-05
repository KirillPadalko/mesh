package com.mesh.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.mesh.client.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    viewModel: MeshViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val p2pStatus by viewModel.p2pStatus.collectAsState()
    
    // Initial load
    LaunchedEffect(peerId) {
        viewModel.loadMessages(peerId)
    }

    var text by remember { mutableStateOf("") }
    val isP2P = p2pStatus[peerId] == true
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("User ${peerId.take(4)}", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = if (isP2P) Color(0xFF4CAF50) else Color(0xFFFFC107)) { } // Green or Amber dot
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isP2P) "Direct connection" else "Relayed via network",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), reverseLayout = false) {
                // Filter messages for this peer locally (ViewModel should ideally filter, but StateFlow provided all?)
                // ViewModel provides "filtered" list via loadMessages update? 
                // Ah, ViewModel has single _messages flow. It replaces content when loadMessages is called.
                // Simple MVP approach.
                items(messages) { msg ->
                    MessageBubble(msg.text, msg.isIncoming)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Input
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (text.isNotEmpty()) {
                        viewModel.sendMessage(peerId, text)
                        text = ""
                        keyboardController?.hide()
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(text: String, isIncoming: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
