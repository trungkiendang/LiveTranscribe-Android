package com.kun.livetranslation.speech

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Streams audio to the Gemini Live translation model over one WebSocket. The server sends back a
 * live transcript of what was said and a live translation into [targetLanguage], so captions grow
 * word by word instead of arriving one clip at a time.
 *
 * Experimental: the model is a preview. If the socket drops it reconnects with a short back-off and
 * reports an error through [onError] after repeated failures.
 */
class GeminiLiveSttSession(
    private val apiKey: String,
    private val targetLanguage: String,
    private val onResult: (SpeechResult) -> Unit,
    private val onError: (String?) -> Unit
) : SttSession {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // the socket stays open for the whole session
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    private var failures = 0

    private val lock = Any()
    private val heard = StringBuilder()
    private val translated = StringBuilder()
    private var turnStartMs = 0L

    init { connect() }

    private fun connect() {
        if (closed) return
        val request = Request.Builder().url("$URL?key=${apiKey.trim()}").build()
        socket = client.newWebSocket(request, Listener())
    }

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        val ws = socket
        if (ready && ws != null) {
            val data = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
            ws.send(
                JSONObject().put(
                    "realtimeInput",
                    JSONObject().put("audio", JSONObject().put("data", data).put("mimeType", "audio/pcm;rate=16000"))
                ).toString()
            )
        }
        return SttFeedResult("", accepted = false)
    }

    override fun finish(): String {
        flushTurn()
        return ""
    }

    override fun close() {
        closed = true
        ready = false
        runCatching { socket?.close(1000, null) }
        socket = null
        client.dispatcher.executorService.shutdown()
    }

    /** Emits what has been heard so far; [final] closes the line so the next speech starts a new one. */
    private fun emit(final: Boolean) {
        val said: String
        val said2: String
        synchronized(lock) {
            said = heard.toString().trim()
            said2 = translated.toString().trim()
            if (final) {
                if (said.isNotEmpty()) {
                    Log.d(TAG, "turn done after ${SystemClock.elapsedRealtime() - turnStartMs} ms: '$said' -> '$said2'")
                }
                heard.setLength(0)
                translated.setLength(0)
            }
        }
        if (said.isNotEmpty()) onResult(SpeechResult(said, final, translation = said2))
    }

    private fun flushTurn() = emit(final = true)

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val setup = JSONObject().put(
                "setup",
                JSONObject()
                    .put("model", MODEL)
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                            .put("inputAudioTranscription", JSONObject())
                            .put("outputAudioTranscription", JSONObject())
                            .put("translationConfig", JSONObject().put("targetLanguageCode", targetLanguage).put("echoTargetLanguage", true))
                    )
            )
            webSocket.send(setup.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = handle(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ready = false
            if (closed) return
            failures++
            Log.w(TAG, "live socket failed (${response?.code}): ${t.message}")
            if (failures >= MAX_FAILURES) {
                onError("Live captions disconnected: ${response?.message ?: t.message ?: "network error"}")
                return
            }
            Thread { Thread.sleep(1_000L * failures); connect() }.start()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ready = false
            if (!closed) { flushTurn(); Thread { Thread.sleep(500); connect() }.start() }
        }
    }

    private fun handle(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (msg.has("setupComplete")) {
            ready = true
            failures = 0
            onError(null)
            return
        }
        val content = msg.optJSONObject("serverContent") ?: return
        content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { piece ->
            synchronized(lock) { if (heard.isEmpty()) turnStartMs = SystemClock.elapsedRealtime(); heard.append(piece) }
            emit(final = false)
        }
        content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { piece ->
            val long = synchronized(lock) { translated.append(piece); translated.length >= LONG_TURN_CHARS && piece.trimEnd().lastOrNull() in SENTENCE_END }
            // Continuous speech never ends a turn: close the line at a sentence end so captions do not grow forever.
            emit(final = long)
        }
        if (content.optBoolean("turnComplete") || content.optBoolean("generationComplete")) flushTurn()
    }

    private companion object {
        const val TAG = "GeminiLive"
        const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val MODEL = "models/gemini-3.5-live-translate-preview"
        const val MAX_FAILURES = 3
        const val LONG_TURN_CHARS = 140
        val SENTENCE_END = setOf('.', '!', '?', '。', '！', '？')
    }
}
