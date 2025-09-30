package com.dsatm.audio_redaction.ui

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val SAMPLE_RATE = 16000f

class AudioRedactionManager(private val context: Context) {

    private val model: Model by lazy {
        val modelDir = File(context.filesDir, "vosk-model-en-us-0.22-lgraph")
        if (!modelDir.exists()) {
            copyAssetFolder("vosk-model-en-us-0.22-lgraph", modelDir)
        }
        Model(modelDir.absolutePath)
    }

    private fun ByteArray.toShortArrayLE(): ShortArray {
        val shortBuffer = ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val shorts = ShortArray(shortBuffer.limit())
        shortBuffer.get(shorts)
        return shorts
    }

    private fun skipWavHeader(inputStream: InputStream) {
        val header = ByteArray(44)
        inputStream.read(header)
    }

    private fun parseResult(resultJson: String): Pair<String, List<String>> {
        val json = JSONObject(resultJson)

        val text: String = json.optString("text", "No transcription")

        val wordsList = mutableListOf<String>()
        val wordsArray: JSONArray? = json.optJSONArray("result")
        if (wordsArray != null) {
            for (i in 0 until wordsArray.length()) {
                val wordObj: JSONObject = wordsArray.optJSONObject(i) ?: continue
                val word: String = wordObj.optString("word", "")
                if (word.isNotEmpty()) {
                    val start = wordObj.optDouble("start", -1.0)
                    val end = wordObj.optDouble("end", -1.0)
                    if (start >= 0 && end >= 0) {
                        wordsList.add("$word [${"%.2f".format(start)}-${"%.2f".format(end)}]")
                    } else {
                        wordsList.add(word)
                    }
                }
            }
        }

        return Pair(text, wordsList)
    }

    fun transcribeAudio(uri: Uri, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recognizer = Recognizer(model, SAMPLE_RATE)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    skipWavHeader(inputStream)
                    val buffer = ByteArray(4096)
                    var read = inputStream.read(buffer)
                    while (read > 0) {
                        val pcmShorts = buffer.copyOf(read).toShortArrayLE()
                        recognizer.acceptWaveForm(pcmShorts, pcmShorts.size)
                        read = inputStream.read(buffer)
                    }
                    val (text, _) = parseResult(recognizer.finalResult)
                    withContext(Dispatchers.Main) { onResult(text) }
                } ?: withContext(Dispatchers.Main) {
                    onResult("Error: Unable to open audio file")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun transcribeAudioWithTimestamps(uri: Uri, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recognizer = Recognizer(model, SAMPLE_RATE)
                val wordsWithTimestamps = mutableListOf<String>()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    skipWavHeader(inputStream)
                    val buffer = ByteArray(4096)
                    var read = inputStream.read(buffer)
                    while (read > 0) {
                        val pcmShorts = buffer.copyOf(read).toShortArrayLE()
                        recognizer.acceptWaveForm(pcmShorts, pcmShorts.size)
                        read = inputStream.read(buffer)
                    }

                    val (text, words) = parseResult(recognizer.finalResult)
                    wordsWithTimestamps.addAll(words)

                    withContext(Dispatchers.Main) {
                        if (wordsWithTimestamps.isEmpty()) {
                            onResult(text)
                        } else {
                            onResult(wordsWithTimestamps.joinToString(" "))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun copyAssetFolder(assetFolder: String, outDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetFolder) ?: return
        outDir.mkdirs()
        for (file in files) {
            val fullAssetPath = "$assetFolder/$file"
            val outFile = File(outDir, file)
            if (assetManager.list(fullAssetPath)?.isNotEmpty() == true) {
                copyAssetFolder(fullAssetPath, outFile)
            } else {
                assetManager.open(fullAssetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
