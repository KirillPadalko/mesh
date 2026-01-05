package com.mesh.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mesh.client.viewmodel.MeshViewModel
import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.Color

@Composable
fun SplashScreen(
    viewModel: MeshViewModel,
    onIdentityFound: () -> Unit,
    onIdentityMissing: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1500) // Fake work / UX delay
        viewModel.checkIdentity()
        if (viewModel.meshId.value != null) {
            onIdentityFound()
        } else {
            onIdentityMissing()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Updated to use the generated splash logo
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.mesh.client.R.drawable.splash_logo),
                contentDescription = "Mesh Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Connecting to Mesh...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: MeshViewModel, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    // 0 = Welcome
    // 1 = Create: Nickname
    // 2 = Create: Seed Display
    // 3 = Create: Warning
    // 4 = Restore: Enter Mnemonic
    // 5 = Restore: Nickname

    // Hold generated seed temporarily for display
    var tempSeed by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        when (step) {
            0 -> WelcomeStep(
                onCreate = { step = 1 },
                onRestore = { step = 4 }
            )
            1 -> NicknameStep(
                onNext = { name ->
                    // Create Flow: Generate & Save
                    val mnemonic = com.mesh.client.identity.BackupManager.generateMnemonic()
                    viewModel.createFromMnemonic(mnemonic)
                    viewModel.updateLocalNickname(name)
                    tempSeed = mnemonic
                    step = 2
                }
            )
            2 -> SeedDisplayStep(
                seed = tempSeed ?: "Error",
                onNext = { step = 3 }
            )
            3 -> WarningStep(
                onFinish = onComplete
            )
            4 -> RestoreStep(
                onRestore = { mnemonicInput ->
                    try {
                        viewModel.restoreFromMnemonic(mnemonicInput)
                        step = 5 // Go to Nickname for Restore flow
                    } catch (e: Exception) {
                        android.util.Log.e("OnboardingScreen", "Failed to restore from mnemonic", e)
                    }
                },
                onBack = { step = 0 }
            )
            5 -> NicknameStep(
                onNext = { name ->
                    // Restore Flow: Just save nickname and finish
                    viewModel.updateLocalNickname(name)
                    onComplete()
                }
            )
        }
    }
}

@Composable
private fun WelcomeStep(onCreate: () -> Unit, onRestore: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mesh", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Mesh creates a personal node for you in the network.\nNo phone number.\nNo account.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text("Create New Account")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRestore) {
            Text("I have a Seed Phrase")
        }
    }
}

@Composable
private fun SeedDisplayStep(seed: String, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Recovery Phrase", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Write down these 12 words in order. This is the ONLY way to recover your account.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Display mnemonic words in a grid
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                val words = seed.split(" ")
                words.chunked(3).forEach { rowWords ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowWords.forEachIndexed { index, word ->
                            val globalIndex = words.indexOf(word) + 1
                            Text(
                                text = "$globalIndex. $word",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onNext) {
            Text("I have saved it")
        }
    }
}

@Composable
private fun WarningStep(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Are you sure?", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "If you lose your recovery phrase, you lose your identity forever.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onFinish) {
            Text("Yes, I understand")
        }
    }
}

@Composable
private fun RestoreStep(onRestore: (String) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Restore Identity", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { 
                text = it
                errorMessage = null
            },
            label = { Text("Enter 12-word recovery phrase") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            supportingText = { 
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Separate words with spaces")
                }
            },
            isError = errorMessage != null
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { 
                val trimmed = text.trim()
                val wordCount = trimmed.split("\\s+".toRegex()).size
                if (wordCount != 12) {
                    errorMessage = "Must be exactly 12 words"
                } else {
                    onRestore(trimmed)
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun NicknameStep(onNext: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Choose a Nickname", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "This is how you will appear to your contacts locally.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = { onNext(nickname.trim().ifEmpty { "User" }) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}
