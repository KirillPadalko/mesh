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
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo
            Image(
                 painter = painterResource(id = com.mesh.client.R.drawable.call_logo),
                 contentDescription = "Logo",
                 modifier = Modifier.size(120.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.mesh),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                androidx.compose.ui.res.stringResource(com.mesh.client.R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f))
            )
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
        Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.mesh), style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.onboarding_description),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.create_new_account))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRestore) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.i_have_seed_phrase))
        }
    }
}

@Composable
private fun SeedDisplayStep(seed: String, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.your_recovery_phrase), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.recovery_phrase_warning),
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
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.i_have_saved_it))
        }
    }
}

@Composable
private fun WarningStep(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.are_you_sure), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.lose_phrase_warning),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onFinish) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.yes_i_understand))
        }
    }
}

@Composable
private fun RestoreStep(onRestore: (String) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val strMustBe12Words = androidx.compose.ui.res.stringResource(com.mesh.client.R.string.must_be_12_words)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.restore_identity), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { 
                text = it
                errorMessage = null
            },
            label = { Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.enter_recovery_phrase)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            supportingText = { 
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.separate_words_with_spaces))
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
                    errorMessage = strMustBe12Words
                } else {
                    onRestore(trimmed)
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.restore))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.back))
        }
    }
}

@Composable
private fun NicknameStep(onNext: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.choose_nickname), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            androidx.compose.ui.res.stringResource(com.mesh.client.R.string.nickname_description),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.nickname)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = { onNext(nickname.trim().ifEmpty { "User" }) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(androidx.compose.ui.res.stringResource(com.mesh.client.R.string.next))
        }
    }
}
