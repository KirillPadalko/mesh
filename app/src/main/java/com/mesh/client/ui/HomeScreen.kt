package com.mesh.client.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MeshViewModel,
    onChatSelected: (String) -> Unit,
    onProfileSelected: () -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    val meshId by viewModel.meshId.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { error ->
            snackbarHostState.showSnackbar(message = error)
        }
    }
    
    // String resources
    val strAddContactTitle = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.add_contact_dialog_title)
    val strEnterMeshId = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.enter_mesh_id)
    val strAdd = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.add)
    val strCancel = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.cancel)
    val strAddContact = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.add_contact)
    val strProfile = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.profile)
    val strInviteFriend = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.invite_friend)
    val strRenameContact = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.rename_contact)
    val strNewNickname = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.new_nickname)
    val strSave = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.save)
    val strDeleteContact = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.delete_contact)
    val strNoContacts = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.no_contacts_message)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var showAddDialog by remember { mutableStateOf(false) }
            var targetId by remember { mutableStateOf("") }
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text(strAddContactTitle) },
                    text = {
                        TextField(
                            value = targetId,
                            onValueChange = { targetId = it },
                            placeholder = { Text(strEnterMeshId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (targetId.isNotBlank()) {
                                viewModel.handleInvite(targetId.trim())
                                showAddDialog = false
                                targetId = ""
                            }
                        }) {
                            Text(strAdd)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text(strCancel)
                        }
                    }
                )
            }

            TopAppBar(
                title = { 
                    Column {
                        val localNickname by viewModel.localNickname.collectAsState()
                        Text(localNickname, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (meshId ?: "").take(12) + "...", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                meshId?.let { 
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                }
                            }
                        )
                    }
                },
                actions = {
                    // Connection Status
                    Box(modifier = Modifier.padding(end = 16.dp), contentAlignment = Alignment.Center) {
                         val color = if (isConnected) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                         androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                             drawCircle(color = color)
                         }
                    }

                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = strAddContact)
                    }

                    IconButton(onClick = onProfileSelected) {
                        Icon(Icons.Default.Person, contentDescription = strProfile)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    com.mesh.client.utils.ShareUtils.shareInvite(context, meshId, viewModel.localNickname.value)
                },
                containerColor = androidx.compose.ui.graphics.Color.Green,
                contentColor = androidx.compose.ui.graphics.Color.Black
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = strInviteFriend
                )
            }
        },

    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(contacts) { contact ->
                val unread = contact.unreadCount > 0
                var showMenu by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var newNickname by remember { mutableStateOf(contact.nickname) }

                if (showRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text(strRenameContact) },
                        text = {
                            TextField(
                                value = newNickname,
                                onValueChange = { newNickname = it },
                                label = { Text(strNewNickname) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (newNickname.isNotBlank()) {
                                    viewModel.renameContact(contact.meshId, newNickname.trim())
                                    showRenameDialog = false
                                }
                            }) {
                                Text(strSave)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text(strCancel)
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    tonalElevation = 1.dp
                ) {
                    Box {
                        ListItem(
                            headlineContent = { 
                                Text(
                                    contact.nickname, 
                                    fontWeight = if (unread) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal 
                                ) 
                            },
                            supportingContent = { 
                                val preview = contact.lastMessage ?: (contact.meshId.take(8) + "...")
                                val textColor = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    preview, 
                                    style = MaterialTheme.typography.bodySmall, 
                                    maxLines = 1, 
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = textColor
                                ) 
                            },
                            leadingContent = {
                                Box(contentAlignment = Alignment.Center) {
                                     Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    if (contact.lastMessageTime != null) {
                                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(contact.lastMessageTime))
                                        Text(time, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (unread) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(contact.unreadCount.toString())
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { onChatSelected(contact.meshId) },
                                onLongClick = { showMenu = true }
                            ),
                            colors = ListItemDefaults.colors(
                                containerColor = if (unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strRenameContact) },
                                onClick = {
                                    showRenameDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = strRenameContact
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strDeleteContact) },
                                onClick = {
                                    viewModel.deleteContact(contact.meshId)
                                    showMenu = false
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Delete, 
                                        contentDescription = strDeleteContact
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            if (contacts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(strNoContacts, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
    

