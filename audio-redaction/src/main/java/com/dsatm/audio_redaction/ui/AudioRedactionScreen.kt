// File: com.dsatm.audio_redaction.ui/AudioRedactionScreen.kt

package com.dsatm.audio_redaction.ui
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsatm.ner.BertNerOnnxManager
import com.dsatm.ner.PiiEntity // Assumes PiiEntity is defined in com.dsatm.ner (e.g., in DataModels.kt)
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class AudioRedactionUiState {
    object Initial : AudioRedactionUiState()
    object LoadingModel : AudioRedactionUiState()
    object Ready : AudioRedactionUiState()
    object Transcribing : AudioRedactionUiState()
    object AnalyzingPii : AudioRedactionUiState()
    data class Result(
        val transcript: String,
        val entities: List<PiiEntity>
    ) : AudioRedactionUiState()
    data class Error(val message: String) : AudioRedactionUiState()
}

@Composable
fun AudioRedactionScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Managers
    val audioManager = remember { AudioRedactionManager(context) }
    val nerManager = remember { BertNerOnnxManager(context) }

    // State
    var uiState by remember { mutableStateOf<AudioRedactionUiState>(AudioRedactionUiState.LoadingModel) }

    // State needed for the audio picker launcher
    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                if (uiState !is AudioRedactionUiState.Ready && uiState !is AudioRedactionUiState.Result) return@let

                uiState = AudioRedactionUiState.Transcribing

                // Use the AudioRedactionManager to transcribe
                audioManager.transcribeAudioWithTimestamps(it) { rawTranscript ->
                    if (rawTranscript.startsWith("Error") || rawTranscript.startsWith("Vosk model failed")) {
                        uiState = AudioRedactionUiState.Error("Transcription failed: $rawTranscript")
                    } else {
                        // Transcription success, now run PII detection
                        coroutineScope.launch {
                            runPiiDetection(rawTranscript, nerManager) { resultState ->
                                uiState = resultState
                            }
                        }
                    }
                }
            }
        }

    // Model Initialization for NER and waiting for Vosk model
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                nerManager.initialize() // Initialize the ONNX model
                // Wait for Vosk model to be loaded
                while (!audioManager.isModelLoaded()) {
                    kotlinx.coroutines.delay(100)
                }
                uiState = AudioRedactionUiState.Ready
            } catch (e: Exception) {
                uiState = AudioRedactionUiState.Error("Model initialization failed: ${e.message}")
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
        // --- 1. Audio Picker Button ---
        val isLoading = uiState is AudioRedactionUiState.Transcribing || uiState is AudioRedactionUiState.AnalyzingPii
        val isReady = uiState is AudioRedactionUiState.Ready || uiState is AudioRedactionUiState.Result

        Button(
            onClick = {
                if (isReady) {
                    audioPickerLauncher.launch("audio/*")
                }
            },
            enabled = isReady && !isLoading
        ) {
            Text(text = when(uiState) {
                is AudioRedactionUiState.LoadingModel -> "Loading Models..."
                is AudioRedactionUiState.Transcribing -> "Transcribing Audio..."
                is AudioRedactionUiState.AnalyzingPii -> "Analyzing for PII..."
                else -> "Pick Audio File (WAV/Audio)"
            })
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 2. State Display (Loading/Error) ---
        when (uiState) {
            is AudioRedactionUiState.Initial, is AudioRedactionUiState.LoadingModel -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Initializing Transcription and NER Models...")
            }
            is AudioRedactionUiState.Ready -> {
                Text("Models ready. Pick an audio file to begin. 🎧", color = MaterialTheme.colorScheme.primary)
            }
            is AudioRedactionUiState.Transcribing, is AudioRedactionUiState.AnalyzingPii -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(if (uiState is AudioRedactionUiState.Transcribing) "Decoding and Transcribing Audio..." else "Running PII Detection...",
                    style = MaterialTheme.typography.bodyMedium)
            }
            is AudioRedactionUiState.Error -> {
                val errorMsg = (uiState as AudioRedactionUiState.Error).message
                Text("❌ ERROR: $errorMsg", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            is AudioRedactionUiState.Result -> {
                // Handled in section 3
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. Result Display ---
        if (uiState is AudioRedactionUiState.Result) {
            val result = uiState as AudioRedactionUiState.Result

            // A. PII Entities Summary
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Identified PII Entities (${result.entities.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (result.entities.isEmpty()) {
                        Text("✅ No PII found in the transcript.")
                    } else {
                        result.entities.forEach { pii ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // USE pii.label here
                                Text(
                                    text = "${pii.label}:",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(text = pii.text)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // B. Full Transcript with PII Highlights
            Text(
                text = "Full Raw Transcript (PII Highlighted):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Display the transcript with highlighted PII
            Text(
                text = highlightPii(result.transcript, result.entities),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Runs the NER detection on the transcript and updates the UI state.
 */
private suspend fun runPiiDetection(
    transcript: String,
    nerManager: BertNerOnnxManager,
    onStateUpdate: (AudioRedactionUiState) -> Unit
) {
    onStateUpdate(AudioRedactionUiState.AnalyzingPii)
    withContext(Dispatchers.IO) {
        try {
            val entities = nerManager.detectPii(transcript)
            onStateUpdate(AudioRedactionUiState.Result(transcript, entities))
        } catch (e: Exception) {
            onStateUpdate(AudioRedactionUiState.Error("PII Detection failed: ${e.message}"))
        }
    }
}

/**
 * Helper function to create an AnnotatedString that highlights PII in the transcript,
 * using the corrected property names (start, end, label).
 */
@Composable
private fun highlightPii(transcript: String, entities: List<PiiEntity>): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(transcript)

        // Highlight each PII entity
        entities.forEach { entity ->
            // USE entity.start and entity.end here
            val startIndex = entity.start
            val endIndex = entity.end

            // Basic sanity check to ensure the offsets are within the bounds of the string
            if (startIndex >= 0 && endIndex <= transcript.length && startIndex <= endIndex) {
                addStyle(
                    style = SpanStyle(
                        background = Color(0xFFFFFDD0), // Light Yellow background
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    start = startIndex,
                    end = endIndex
                )
                // Annotation tag should use entity.label
                addStringAnnotation(
                    tag = "PII_TYPE",
                    annotation = entity.label,
                    start = startIndex,
                    end = endIndex
                )
            }
        }
    }
}