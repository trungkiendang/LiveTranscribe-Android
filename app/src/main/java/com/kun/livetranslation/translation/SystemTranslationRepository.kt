package com.kun.livetranslation.translation

import android.content.Context
import android.icu.util.ULocale
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Translation through the phone's own system translation service (Android's public
 * [TranslationManager], API 31+). On Samsung devices One UI may provide that service;
 * it is the only route third-party apps have to the OEM's on-device translator.
 *
 * Whether a device implements it is up to the manufacturer, so every failure path
 * (no service, pair not supported, timeout, empty result) falls back to [fallback]
 * (ML Kit) and remembers unsupported pairs so we do not retry them on every caption.
 * Auto-detect is not handled here: those calls go straight to [fallback], which can
 * identify the language.
 */
class SystemTranslationRepository(
    context: Context,
    private val fallback: TranslationRepository
) : TranslationRepository {

    private val manager: TranslationManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.applicationContext.getSystemService(TranslationManager::class.java)
        } else null

    private val executor = Executors.newSingleThreadExecutor()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val unsupported = ConcurrentHashMap.newKeySet<String>()

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty() || targetCode.isBlank() || sourceCode.equals(targetCode, ignoreCase = true)) return clean
        if (autoDetect) return fallback.translate(text, sourceCode, targetCode, autoDetect)

        val translated = withTimeoutOrNull(TIMEOUT_MS) {
            translatorFor(sourceCode, targetCode)?.let { run(it, clean) }
        }
        return if (!translated.isNullOrBlank()) translated else fallback.translate(text, sourceCode, targetCode, autoDetect)
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        withTimeoutOrNull(TIMEOUT_MS) { translatorFor(sourceCode, targetCode) }
        fallback.prewarm(sourceCode, targetCode)
    }

    private suspend fun translatorFor(source: String, target: String): Translator? {
        val mgr = manager ?: return null
        val key = "${source.lowercase()}|${target.lowercase()}"
        if (key in unsupported) return null
        translators[key]?.takeIf { !it.isDestroyed }?.let { return it }

        val ctx = TranslationContext.Builder(
            TranslationSpec(ULocale.forLanguageTag(source), TranslationSpec.DATA_FORMAT_TEXT),
            TranslationSpec(ULocale.forLanguageTag(target), TranslationSpec.DATA_FORMAT_TEXT)
        ).build()
        val created = try {
            suspendCancellableCoroutine<Translator?> { cont ->
                mgr.createOnDeviceTranslator(ctx, executor) { if (cont.isActive) cont.resume(it) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "createOnDeviceTranslator failed for $key", t)
            null
        }
        if (created == null) {
            unsupported.add(key)
            Log.i(TAG, "No system translator for $key; using fallback")
            return null
        }
        translators[key] = created
        return created
    }

    private suspend fun run(translator: Translator, text: String): String? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val request = TranslationRequest.Builder()
                .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
                .build()
            try {
                translator.translate(request, signal, executor) { response: TranslationResponse ->
                    val value = response.translationResponseValues.valueAt(0)
                    val ok = response.translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS &&
                        value?.statusCode == TranslationResponseValue.STATUS_SUCCESS
                    if (cont.isActive) cont.resume(if (ok) value?.text?.toString()?.trim() else null)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "system translate failed", t)
                if (cont.isActive) cont.resume(null)
            }
        }

    override fun close() {
        translators.values.forEach { runCatching { it.destroy() } }
        translators.clear()
        executor.shutdown()
        fallback.close()
    }

    companion object {
        private const val TAG = "SystemTranslator"
        private const val TIMEOUT_MS = 4_000L

        /** The option is offered on Samsung phones only (One UI is where an OEM service is expected). */
        fun isOfferedOnThisDevice(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}
