package com.dsatm.ner
//
//import android.content.Context
//import android.util.Log
//import org.tensorflow.lite.Interpreter
//import org.tensorflow.lite.support.common.FileUtil
//import java.io.FileInputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.MappedByteBuffer
//import java.nio.channels.FileChannel
//import java.util.Locale
//import java.util.regex.Pattern
//import kotlin.math.exp
//
//class NERModel(private val context: Context) {
//
//    // Model parameters
//    private val model: Interpreter
//    private val vocab: Map<String, Int>
//    private val labels = listOf(
//        "B-AGE", "B-BUILDINGNUM", "B-CITY", "B-CREDITCARDNUMBER", "B-DATE",
//        "B-DRIVERLICENSENUM", "B-EMAIL", "B-GENDER", "B-GIVENNAME", "B-IDCARDNUM",
//        "B-PASSPORTNUM", "B-SEX", "B-SOCIALNUM", "B-STREET", "B-SURNAME",
//        "B-TAXNUM", "B-TELEPHONENUM", "B-TIME", "B-TITLE", "B-ZIPCODE",
//        "I-BUILDINGNUM", "I-CITY", "I-CREDITCARDNUMBER", "I-DATE",
//        "I-DRIVERLICENSENUM", "I-EMAIL", "I-GENDER", "I-GIVENNAME",
//        "I-IDCARDNUM", "I-PASSPORTNUM", "I-SEX", "I-SOCIALNUM",
//        "I-STREET", "I-SURNAME", "I-TAXNUM", "I-TELEPHONENUM",
//        "I-TIME", "I-TITLE", "I-ZIPCODE", "O"
//    )
//    private val maxSeqLength = 512
//
//    // Special tokens
//    private val clsToken = "[CLS]"
//    private val sepToken = "[SEP]"
//    private val unkToken = "[UNK]"
//    private val padToken = "[PAD]"
//
//    init {
//        try {
//            val modelBuffer = loadModelFile(context, "ner.tflite")
//            val options = Interpreter.Options()
//            model = Interpreter(modelBuffer, options)
//            vocab = loadVocab(context, "vocab.txt")
//
//            Log.d("NERModel", "Model and tokenizer loaded successfully.")
//        } catch (e: Exception) {
//            Log.e("NERModel", "Error initializing NERModel: ${e.message}", e)
//            throw e
//        }
//    }
//
//    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
//        val fileDescriptor = context.assets.openFd(modelPath)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    private fun loadVocab(context: Context, vocabPath: String): Map<String, Int> {
//        val vocabMap = mutableMapOf<String, Int>()
//        FileUtil.loadLabels(context, vocabPath).forEachIndexed { index, token ->
//            vocabMap[token] = index
//        }
//        return vocabMap
//    }
//
//    fun predict(text: String): List<Pair<String, String>> {
//        Log.d("NERModel", "Original Text: $text")
//
//        val tokens = bertTokenize(text)
//        Log.d("NERModel", "Tokenized Text: $tokens")
//
//        val bertTokens = mutableListOf(clsToken).apply {
//            addAll(tokens)
//            add(sepToken)
//        }
//
//        val paddedTokens = bertTokens.take(maxSeqLength)
//        Log.d("NERModel", "BERT Tokens (before padding): $bertTokens")
//        Log.d("NERModel", "Padded/Truncated Tokens: $paddedTokens")
//
//        val inputIds = LongArray(maxSeqLength)
//        val attentionMask = LongArray(maxSeqLength)
//        val tokenTypeIds = LongArray(maxSeqLength)
//
//        paddedTokens.forEachIndexed { index, token ->
//            if (index < maxSeqLength) {
//                inputIds[index] = vocab[token]?.toLong() ?: vocab[unkToken]?.toLong() ?: 0L
//                attentionMask[index] = 1L
//                tokenTypeIds[index] = 0L
//            }
//        }
//
//        for (i in paddedTokens.size until maxSeqLength) {
//            inputIds[i] = vocab[padToken]?.toLong() ?: 0L
//            attentionMask[i] = 0L
//            tokenTypeIds[i] = 0L
//        }
//
//        Log.d("NERModel", "Input IDs: ${inputIds.take(paddedTokens.size)}")
//        Log.d("NERModel", "Attention Mask: ${attentionMask.take(paddedTokens.size)}")
//        Log.d("NERModel", "Token Type IDs: ${tokenTypeIds.take(paddedTokens.size)}")
//
//        val inputIdsBuffer = longArrayToByteBuffer(inputIds)
//        val attentionMaskBuffer = longArrayToByteBuffer(attentionMask)
//        val tokenTypeIdsBuffer = longArrayToByteBuffer(tokenTypeIds)
//
//        val inputs = arrayOf(inputIdsBuffer, attentionMaskBuffer, tokenTypeIdsBuffer)
//
//        val outputTensorShape = model.getOutputTensor(0).shape()
//        val numLabels = labels.size
//
//        Log.d("NERModel", "Output Tensor Shape: ${outputTensorShape.contentToString()}")
//
//        val outputScores = Array(1) { Array(maxSeqLength) { FloatArray(numLabels) } }
//        val outputs = mutableMapOf<Int, Any>()
//        outputs[0] = outputScores
//
//        Log.d("NERModel", "Running TFLite inference...")
//        model.runForMultipleInputsOutputs(inputs, outputs)
//
//        Log.d("NERModel", "Inference complete.")
//        val rawScoresLog = StringBuilder("Raw output scores for first 5 tokens:\n")
//        for (i in 0 until 5.coerceAtMost(bertTokens.size)) {
//            rawScoresLog.append("Token: ${bertTokens[i]}\n")
//            for (j in 0 until numLabels) {
//                rawScoresLog.append("\tLabel '${labels[j]}': ${outputScores[0][i][j]}\n")
//            }
//        }
//        Log.d("NERModel", rawScoresLog.toString())
//
//        val results = postProcessOutput(bertTokens, outputScores)
//        Log.d("NERModel", "Final detected entities: $results")
//
//        return results
//    }
//
//    private fun bertTokenize(text: String): List<String> {
//        val tokens = mutableListOf<String>()
//        val words = text.lowercase(Locale.ROOT).split(Regex("\\s+"))
//        val punctuationRegex = Pattern.compile("([^\\p{L}\\p{N}]+)|(\\p{L}+)|(\\p{N}+)")
//
//        for (word in words) {
//            val matcher = punctuationRegex.matcher(word)
//            while (matcher.find()) {
//                val matchedText = matcher.group()
//                if (matchedText.isNotEmpty()) {
//                    tokens.addAll(splitWordpiece(matchedText))
//                }
//            }
//        }
//        return tokens
//    }
//
//    private fun splitWordpiece(word: String): List<String> {
//        val subwords = mutableListOf<String>()
//        var start = 0
//        while (start < word.length) {
//            var end = word.length
//            var subword = ""
//            var isWordFound = false
//
//            while (start < end) {
//                val candidate = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
//                if (vocab.containsKey(candidate)) {
//                    subword = candidate
//                    isWordFound = true
//                    break
//                }
//                end--
//            }
//
//            if (isWordFound) {
//                subwords.add(subword)
//                start = end
//            } else {
//                subwords.add(unkToken)
//                break
//            }
//        }
//        return subwords
//    }
//
//    private fun postProcessOutput(
//        tokens: List<String>,
//        outputScores: Array<Array<FloatArray>>
//    ): List<Pair<String, String>> {
//        val results = mutableListOf<Pair<String, String>>()
//        val numLabels = labels.size
//
//        Log.d("NERModel", "Softmax probabilities for first 5 tokens:")
//
//        for (i in 0 until tokens.size) {
//            val tokenScores = outputScores[0][i]
//            val softmaxScores = softmax(tokenScores)
//
//            Log.d("NERModel", "Token: ${tokens[i]}")
//            val topProbabilities = softmaxScores.mapIndexed { index, score -> Pair(labels[index], score) }
//                .sortedByDescending { it.second }
//                .take(3)
//
//            for ((label, score) in topProbabilities) {
//                Log.d("NERModel", "\tLabel '$label': $score")
//            }
//
//            var maxScore = -1.0f
//            var maxIndex = -1
//
//            for (j in 0 until numLabels) {
//                if (softmaxScores[j] > maxScore) {
//                    maxScore = softmaxScores[j]
//                    maxIndex = j
//                }
//            }
//
//            val token = tokens[i]
//            val label = if (maxIndex != -1 && maxIndex < labels.size) {
//                labels[maxIndex]
//            } else {
//                "O"
//            }
//
//            if (label != "O" && token != clsToken && token != sepToken) {
//                results.add(Pair(token, label))
//            }
//        }
//        return results
//    }
//
//    private fun softmax(logits: FloatArray): FloatArray {
//        val maxLogit = logits.maxOrNull() ?: 0f
//        val expValues = logits.map { exp(it - maxLogit) }
//        val sumExp = expValues.sum()
//        return expValues.map { it / sumExp }.toFloatArray()
//    }
//
//    private fun longArrayToByteBuffer(array: LongArray): ByteBuffer {
//        val buffer = ByteBuffer.allocateDirect(array.size * 8)
//        buffer.order(ByteOrder.nativeOrder())
//        val longBuffer = buffer.asLongBuffer()
//        longBuffer.put(array)
//        buffer.rewind()
//        return buffer
//    }
//
//    fun close() {
//        model.close()
//    }
//}