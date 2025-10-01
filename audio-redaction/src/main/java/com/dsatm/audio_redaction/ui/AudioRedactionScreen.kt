// File: AudioRedactionScreen.kt
package com.dsatm.audio_redaction.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AudioRedactionScreen() {
    val context = LocalContext.current
    val manager = remember { AudioRedactionManager(context) } // ✅ Only context required

    var transcription by remember { mutableStateOf("No audio selected or transcription started.") }
    var loading by remember { mutableStateOf(false) }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                loading = true
                transcription = "Transcribing..."
                manager.transcribeAudioWithTimestamps(it) { result ->
                    transcription = result
                    loading = false
                }
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (!loading) {
                    audioPickerLauncher.launch("audio/*")
                }
            },
            enabled = !loading
        ) {
            Text(text = if (loading) "Processing..." else "Pick Audio File (WAV/Audio)")
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        Text(
            text = "Transcription Result:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = transcription,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
