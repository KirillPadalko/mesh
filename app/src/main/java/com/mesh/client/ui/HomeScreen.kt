package com.mesh.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MeshViewModel,
    onChatSelected: (String) -> Unit,
    onProfileSelected: () -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    val meshId by viewModel.meshId.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // var showAddDialog by remember { mutableStateOf(false) } // Removed for now

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh") },
                actions = {
                    IconButton(onClick = onProfileSelected) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val link = "mesh://invite/$meshId"
                    val sendIntent: android.content.Intent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Join me on Mesh! Click to connect: $link")
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Invite via")
                    context.startActivity(shareIntent)
                },
                icon = { Icon(Icons.Default.Share, contentDescription = "Invite Friend") },
                text = { Text("Invite Friend") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(contacts) { contact ->
                ListItem(
                    headlineContent = { Text(contact.nickname) },
                    supportingContent = { Text(contact.meshId.take(8) + "...", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = {
                        Text("●", color = MaterialTheme.colorScheme.primary) 
                    },
                    modifier = Modifier.clickable { onChatSelected(contact.meshId) }
                )
            }
            if (contacts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No contacts yet. Invite a friend to start chatting.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
    
    // Add Dialog hidden/removed per request
    /*
    if (showAddDialog) { ... }
    */
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit, onInvite: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column {
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Mesh-ID") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nick, onValueChange = { nick = it }, label = { Text("Nickname (Optional)") })
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onInvite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite Friend")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (id.isNotEmpty()) onAdd(id, nick.ifEmpty { "User" }) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
