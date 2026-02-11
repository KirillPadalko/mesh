package com.mesh.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel
import androidx.compose.ui.graphics.Color

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
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // String resources
    val strProfile = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.profile)
    val strBack = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.back)
    val strYourMeshNode = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.your_mesh_node)
    val strNickname = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.nickname)
    val strSave = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.save)
    val strEdit = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.edit)
    val strTapToCopy = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.tap_to_copy)
    val strIdCopied = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.id_copied)
    val strMeshSignal = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.mesh_signal)
    val strViewNetworkMap = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.view_network_map)
    val strSecurity = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.security)
    val strBackupIdentity = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.backup_identity)
    val strShowPrivateSeedPhrase = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.show_private_seed_phrase)
    val strShow = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.show)
    val strHide = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.hide)
    val strDoNotShareThis = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.do_not_share_this)
    val strRecoveryPhrase = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.recovery_phrase)
    val strSeedHexLegacy = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.seed_hex_legacy)
    val strLoading = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.loading)
    val strError = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.error)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strProfile) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strBack)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val nick = viewModel.localNickname.value
                        val id = viewModel.meshId.value
                        com.mesh.client.utils.ShareUtils.shareInvite(context, id, nick)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Profile")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Identity Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strYourMeshNode, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val localNickname by viewModel.localNickname.collectAsState()
                    var isEditing by remember { mutableStateOf(false) }
                    var editedName by remember { mutableStateOf(localNickname) }

                    if (isEditing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(strNickname) },
                                singleLine = true
                            )
                            IconButton(onClick = {
                                viewModel.updateLocalNickname(editedName.take(20)) // Limit length
                                isEditing = false
                            }) {
                                Icon(Icons.Default.Check, strSave)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = localNickname,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editedName = localNickname
                                isEditing = true
                            }) {
                                Icon(Icons.Default.Edit, strEdit)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayId = meshId ?: strLoading
                    Text(
                        text = displayId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            if (meshId != null) {
                                clipboardManager.setText(AnnotatedString(meshId!!))
                                android.widget.Toast.makeText(context, strIdCopied, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strTapToCopy, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mesh Signal Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(strMeshSignal, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val level = viewModel.getSignalLevel(meshScore)
                    MeshSignalIcon(
                        level = level,
                        modifier = Modifier.size(80.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.level, level),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.score, meshScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text(strBackupIdentity) },
                supportingContent = { Text(strShowPrivateSeedPhrase) },
                trailingContent = {
                    Button(onClick = { showSeed = !showSeed }) {
                        Text(if (showSeed) strHide else strShow)
                    }
                }
            )
            
            if (showSeed) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strDoNotShareThis, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val backup = viewModel.identityManager.exportMnemonic()
                        if (backup != null) {
                            // Show mnemonic in grid
                            Text(strRecoveryPhrase, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
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
                            Text(strSeedHexLegacy, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            val seedHex = try { viewModel.identityManager.exportSeedHex() } catch(e:Exception){strError}
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
