package com.kun.livetranslation.speech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** One streaming Zipformer model: where its files come from and what they are called. */
data class SherpaModel(
    val id: String,          // "en" or "zh"; also the folder name
    val label: String,
    val repo: String,        // Hugging Face repo that holds the files
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val sizeMb: Int
) {
    val files get() = listOf(encoder, decoder, joiner, "tokens.txt")
}

/**
 * Downloads and opens the small streaming Zipformer models (English, Chinese) that run through
 * sherpa-onnx. They are far more accurate than the small Vosk models and still stream word by word.
 */
class SherpaModelStore(private val context: Context) {

    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val root get() = File(context.filesDir, "sherpa")

    private val progressFlow = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = progressFlow.asStateFlow()
    private val errorFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = errorFlow.asStateFlow()
    private val installedFlow = MutableStateFlow(models.filter { isInstalled(it) }.map { it.id }.toSet())
    val installed: StateFlow<Set<String>> = installedFlow.asStateFlow()

    fun modelFor(languageCode: String): SherpaModel? {
        val c = languageCode.lowercase()
        return models.firstOrNull { c == it.id || c.startsWith(it.id + "-") || c.startsWith(it.id + "_") }
    }

    private fun dir(m: SherpaModel) = File(root, m.id)
    fun hasModelFor(languageCode: String) = modelFor(languageCode)?.let { isInstalled(it) } == true

    private fun isInstalled(m: SherpaModel) = m.files.all { File(dir(m), it).length() > 0 }

    suspend fun download(m: SherpaModel) = withContext(Dispatchers.IO) {
        if (progressFlow.value.containsKey(m.id)) return@withContext
        progressFlow.update { it + (m.id to 0f) }
        errorFlow.update { it - m.id }
        try {
            val target = dir(m).apply { mkdirs() }
            var done = 0L
            val total = m.sizeMb * 1_048_576L
            for (name in m.files) {
                val part = File(target, "$name.part")
                val req = Request.Builder().url("https://huggingface.co/${m.repo}/resolve/main/$name").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for $name")
                    resp.body!!.byteStream().use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                progressFlow.update { it + (m.id to (done.toFloat() / total).coerceAtMost(0.99f)) }
                            }
                        }
                    }
                }
                if (!part.renameTo(File(target, name))) error("Could not save $name")
            }
            installedFlow.update { it + m.id }
        } catch (t: Throwable) {
            Log.w(TAG, "download ${m.id} failed", t)
            errorFlow.update { it + (m.id to (t.message ?: t::class.java.simpleName)) }
            dir(m).deleteRecursively()
        } finally {
            progressFlow.update { it - m.id }
        }
    }

    fun delete(m: SherpaModel) {
        dir(m).deleteRecursively()
        installedFlow.update { it - m.id }
    }

    /** Opens a recognizer for [languageCode], or null when no installed model covers it. */
    fun openSession(languageCode: String): SttSession? {
        val m = modelFor(languageCode)?.takeIf { isInstalled(it) } ?: return null
        return try {
            val d = dir(m)
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(d, m.encoder).path,
                        decoder = File(d, m.decoder).path,
                        joiner = File(d, m.joiner).path
                    ),
                    tokens = File(d, "tokens.txt").path,
                    numThreads = 2
                ),
                // Shorter silence than the defaults so a video line closes quickly.
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 1.6f, 0.0f),
                    rule2 = EndpointRule(true, 0.6f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 12.0f)
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )
            SherpaSttSession(OnlineRecognizer(null, config))
        } catch (t: Throwable) {
            Log.w(TAG, "could not open ${m.id}", t)
            null
        }
    }

    companion object {
        private const val TAG = "SherpaModels"
        val models = listOf(
            SherpaModel(
                "en", "English", "csukuangfj/sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06",
                "encoder.onnx", "decoder.onnx", "joiner.onnx", 71
            ),
            SherpaModel(
                "zh", "中文", "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
                "encoder-epoch-99-avg-1.int8.onnx", "decoder-epoch-99-avg-1.onnx", "joiner-epoch-99-avg-1.int8.onnx", 31
            )
        )
    }
}
