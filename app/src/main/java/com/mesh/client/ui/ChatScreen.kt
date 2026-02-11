package com.mesh.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mesh.client.data.LocalStorage
import com.mesh.client.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Mark active chat for notification suppression
    DisposableEffect(peerId) {
        onDispose {
            viewModel.clearActiveChat()
        }
    }

    var text by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<LocalStorage.StoredMessage?>(null) }
    var replyingTo by remember { mutableStateOf<LocalStorage.StoredMessage?>(null) }
    
    val isP2P = p2pStatus[peerId] == true
    val keyboardController = LocalSoftwareKeyboardController.current

    val contacts by viewModel.contacts.collectAsState()
    val contact = contacts.find { it.meshId == peerId }
    val strUserPrefix = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.user_prefix, peerId.take(4))
    val nickname = contact?.nickname ?: strUserPrefix
    
    // String resources
    val strReactToMessage = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.react_to_message)
    val strReply = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.reply)
    val strReplyingTo = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.replying_to)
    val strCancelReply = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.cancel_reply)
    val strMessagePlaceholder = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.message_placeholder)
    val strSend = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.send)
    val strBack = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.back)
    val strCall = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.call)
    val strDirectConnection = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.direct_connection)
    val strRelayedViaNetwork = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.relayed_via_network)

    // Group messages by date
    val groupedItems = remember(messages) {
        val list = mutableListOf<Any>()
        var lastDate = ""
        val dateFormat = SimpleDateFormat("dd MMMM", Locale.getDefault())
        
        // Ensure messages are sorted by timestamp just in case
        val sortedMessages = messages.sortedBy { it.timestamp }

        sortedMessages.forEach { msg ->
            val date = dateFormat.format(Date(msg.timestamp))
            if (date != lastDate) {
                list.add(date)
                lastDate = date
            }
            list.add(msg)
        }
        list
    }

    if (selectedMessage != null) {
        ModalBottomSheet(onDismissRequest = { selectedMessage = null }) {
            Column(Modifier.padding(16.dp)) {
                Text(strReactToMessage, style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val emojis = listOf("👍", "❤️", "😂", "😲", "😢", "😡")
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.clickable {
                                selectedMessage?.let { viewModel.reactToMessage(it.id, emoji) }
                                selectedMessage = null
                            }
                        )
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        replyingTo = selectedMessage
                        selectedMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strReply)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var hasInitialScrolled by remember { mutableStateOf(false) }

    // Initial Scroll to found unread or bottom
    LaunchedEffect(groupedItems) {
        if (groupedItems.isNotEmpty() && !hasInitialScrolled) {
            val contactForReads = contacts.find { it.meshId == peerId }
            val unreadCount = contactForReads?.unreadCount ?: 0
            
            if (unreadCount > 0) {
                 val sortedMessages = messages.sortedBy { it.timestamp }
                 val firstUnreadIndex = sortedMessages.size - unreadCount
                 if (firstUnreadIndex >= 0 && firstUnreadIndex < sortedMessages.size) {
                     val firstUnreadMsg = sortedMessages[firstUnreadIndex]
                     val indexInGrouped = groupedItems.indexOfFirst { 
                        it is LocalStorage.StoredMessage && it.id == firstUnreadMsg.id 
                     }
                     if (indexInGrouped != -1) {
                         listState.scrollToItem(indexInGrouped)
                     } else {
                         listState.scrollToItem(groupedItems.size - 1)
                     }
                 } else {
                     listState.scrollToItem(groupedItems.size - 1)
                 }
            } else {
                listState.scrollToItem(groupedItems.size - 1)
            }
            
            hasInitialScrolled = true
            viewModel.markAsRead(peerId)
        }
    }

    // Auto-scroll on new message (incoming or outgoing)
    LaunchedEffect(messages.size) {
        if (hasInitialScrolled && messages.isNotEmpty()) {
             listState.animateScrollToItem(groupedItems.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(nickname, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = if (isP2P) Color(0xFF4CAF50) else Color(0xFFFFC107)) { } // Green or Amber dot
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isP2P) strDirectConnection else strRelayedViaNetwork,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strBack)
                    }
                },
                actions = {
                    // Disable call button for now as per user request
                    // IconButton(onClick = { viewModel.startCall(peerId) }) {
                    //    Icon(Icons.Default.Call, contentDescription = strCall)
                    // }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                reverseLayout = false
            ) {
                items(groupedItems) { item ->
                    if (item is String) {
                        DateHeader(dateText = item)
                    } else if (item is LocalStorage.StoredMessage) {
                        MessageBubble(item, onLongClick = { selectedMessage = it })
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Reply Preview
            replyingTo?.let { reply ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(strReplyingTo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(reply.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Default.Close, contentDescription = strCancelReply)
                        }
                    }
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
                    placeholder = { Text(strMessagePlaceholder) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (text.isNotEmpty()) {
                        viewModel.sendMessage(peerId, text, replyingTo?.id, replyingTo?.text)
                        text = ""
                        replyingTo = null
                        keyboardController?.hide()
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = strSend, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun DateHeader(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MessageBubble(message: LocalStorage.StoredMessage, onLongClick: (LocalStorage.StoredMessage) -> Unit) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (message.isIncoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { onLongClick(message) } // Using tap as per request ("tap on any message")
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Reply Quote
                if (message.replyToText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) { // Quote box
                        Row(Modifier.padding(4.dp).height(IntrinsicSize.Min)) {
                             Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                             Spacer(Modifier.width(4.dp))
                             Text(
                                 text = message.replyToText,
                                 style = MaterialTheme.typography.bodySmall,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                        }
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                   modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.reaction != null) {
                         Text(message.reaction, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    }
                    if (message.transportType != null) {
                        val transportText = if (message.transportType == "p2p") 
                            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.p2p_short) 
                        else 
                            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.server_short)
                        
                        Text(
                            text = transportText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
