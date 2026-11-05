package com.charles.livecaptionn.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Keeps track of which Vosk models exist on disk right now and lets the UI
 * kick off downloads of additional ones. A model is "installed" if either:
 *
 *   - its folder lives inside `assets/models/<name>/` (shipped with the app), OR
 *   - it has been downloaded into `filesDir/vosk/models/<name>/`.
 *
 * The public [models] StateFlow is what the settings screen binds against;
 * call [refresh] after a download so the list reflects the new state.
 */
class VoskModelRegistry(private val context: Context) {

    private val registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutableModels = MutableStateFlow<List<VoskModelInfo>>(emptyList())
    val models: StateFlow<List<VoskModelInfo>> = mutableModels.asStateFlow()

    /** Tracks in-progress downloads (0f..1f). Entries are removed when done. */
    private val progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = progressMap.asStateFlow()

    /** Last download error keyed by model name, for surfacing in the UI. */
    private val errorMap = ConcurrentHashMap<String, String>()

    /**
     * Same errors as a flow so the model sheet can render them. Before this the
     * failure was recorded but never shown, so a failed download looked like a
     * "Get" button that did nothing (Play review, Oct 2026).
     */
    private val errorFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = errorFlow.asStateFlow()

    init {
        // Asset enumeration and recursive model-directory inspection can block
        // cold startup on devices with large app storage. Load the catalog off
        // the main thread; the UI starts disabled until this state arrives.
        registryScope.launch { refresh() }
    }

    fun refresh() {
        val bundledNames = VoskModelCatalog.BUNDLED_MODELS.mapNotNull { b ->
            if (assetModelExists(b.modelName)) b else null
        }
        val downloadedNames = listDownloadedModelNames()

        val known = buildList {
            // Bundled first — mark them as installed + bundled.
            bundledNames.forEach { b ->
                val catalog = VoskModelCatalog.findByModelName(b.modelName)
                    ?: VoskModelCatalog.findByLanguage(b.languageCode)
                add(
                    VoskModelInfo(
                        languageCode = b.languageCode,
                        languageName = catalog?.languageName ?: b.languageCode.uppercase(),
                        modelName = b.modelName,
                        sizeMb = catalog?.sizeMb ?: 0,
                        downloadUrl = null,
                        installed = true,
                        source = VoskSource.BUNDLED,
                        isBundled = true
                    )
                )
            }
            // Downloadable entries. Mark installed when the folder exists under filesDir.
            VoskModelCatalog.DOWNLOADABLE.forEach { entry ->
                if (bundledNames.any { it.modelName == entry.modelName }) return@forEach
                val installed = downloadedNames.contains(entry.modelName)
                add(entry.copy(installed = installed, source = VoskSource.DOWNLOADED))
            }
        }
        mutableModels.value = known
    }

    fun installedLanguageCodes(): List<String> =
        mutableModels.value.filter { it.installed }.map { it.languageCode }.distinct()

    fun installedModels(): List<VoskModelInfo> =
        mutableModels.value.filter { it.installed }

    fun installedModelFor(languageCode: String): VoskModelInfo? =
        mutableModels.value
            .filter { it.installed && it.languageCode.equals(languageCode, ignoreCase = true) }
            .firstOrNull { it.quality == ModelQuality.LARGE }
            ?: mutableModels.value.firstOrNull {
                it.installed && it.languageCode.equals(languageCode, ignoreCase = true)
            }

    /** Resolves the on-disk directory for a language code, or null if not installed.
     *  When both small and large variants are installed for the same language,
     *  the large one wins since the user explicitly opted into it. */
    fun resolveModelDir(languageCode: String): File? {
        val installed = mutableModels.value.filter {
            it.languageCode.equals(languageCode, ignoreCase = true) && it.installed
        }
        val candidate = installed.firstOrNull { it.quality == ModelQuality.LARGE }
            ?: installed.firstOrNull()
            ?: return null

        return when (candidate.source) {
            VoskSource.BUNDLED -> ensureBundledCopied(candidate.modelName)
            VoskSource.DOWNLOADED -> downloadedModelDir(candidate.modelName)
        }
    }

    /**
     * Downloads [info] to local storage, unzips it, and refreshes state. Returns
     * true on success. The caller should observe [downloadProgress] for UI feedback.
     */
    suspend fun downloadAndInstall(info: VoskModelInfo): Boolean = withContext(Dispatchers.IO) {
        val url = info.downloadUrl
        if (url.isNullOrBlank()) {
            setError(info.modelName, "No download URL")
            return@withContext false
        }
        clearError(info.modelName)
        reportProgress(info.modelName, 0f)
        try {
            val cacheDir = File(context.cacheDir, "vosk-downloads").apply { mkdirs() }
            val zipFile = File(cacheDir, "${info.modelName}.zip")
            if (!download(info.modelName, url, zipFile)) return@withContext false

            reportProgress(info.modelName, 0.9f)
            val targetRoot = File(context.filesDir, "vosk/models").apply { mkdirs() }
            unzipInto(zipFile, targetRoot, info.modelName)
            zipFile.delete()
            reportProgress(info.modelName, null)
            refresh()
            true
        } catch (t: Throwable) {
            Log.w("VoskModelRegistry", "Failed to install ${info.modelName}", t)
            setError(info.modelName, describeFailure(t))
            reportProgress(info.modelName, null)
            false
        }
    }

    /**
     * alphacephei.com throttles each connection hard (a few KB/s from some networks), so one
     * stream takes many minutes. It supports HTTP Range, so big files are fetched as several
     * parallel parts (throughput scales with the number of connections). Falls back to a single
     * stream when the server does not report a size or range support.
     */
    private suspend fun download(modelName: String, url: String, zipFile: File): Boolean {
        val size = rangeSupportedSize(url)
        if (size == null || size < PARALLEL_MIN_BYTES) return downloadSingle(modelName, url, zipFile)
        return try {
            RandomAccessFile(zipFile, "rw").use { it.setLength(size) }
            val done = AtomicLong(0)
            var lastPercent = -1
            val partSize = (size + PARTS - 1) / PARTS
            coroutineScope {
                (0 until PARTS).map { i ->
                    async(Dispatchers.IO) {
                        val start = i * partSize
                        val end = minOf(size - 1, start + partSize - 1)
                        if (start <= end) fetchPart(url, zipFile, start, end) { n ->
                            val pct = (done.addAndGet(n) * 100 / size).toInt()
                            if (pct != lastPercent) {
                                lastPercent = pct
                                reportProgress(modelName, (pct / 100f * 0.9f).coerceIn(0f, 0.9f))
                            }
                        }
                    }
                }.awaitAll()
            }
            true
        } catch (t: Throwable) {
            Log.w("VoskModelRegistry", "Parallel download of $modelName failed", t)
            setError(modelName, describeFailure(t))
            reportProgress(modelName, null)
            false
        }
    }

    /** Size in bytes if the server accepts byte ranges, else null. */
    private fun rangeSupportedSize(url: String): Long? = try {
        httpClient().newCall(Request.Builder().url(url).head().build()).execute().use { r ->
            val len = r.header("Content-Length")?.toLongOrNull()
            if (r.isSuccessful && r.header("Accept-Ranges").equals("bytes", ignoreCase = true) && len != null && len > 0) len else null
        }
    } catch (_: Throwable) { null }

    /** Downloads bytes [start]..[end] into [file], resuming from the last byte on a dropped connection. */
    private fun fetchPart(url: String, file: File, start: Long, end: Long, onBytes: (Long) -> Unit) {
        var pos = start
        var attempt = 0
        while (pos <= end) {
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=$pos-$end").build()
                httpClient().newCall(req).execute().use { r ->
                    if (r.code != 206) throw java.io.IOException("Server ignored range request (HTTP ${r.code})")
                    val input = r.body?.byteStream() ?: throw java.io.IOException("empty response")
                    RandomAccessFile(file, "rw").use { out ->
                        out.seek(pos)
                        val buf = ByteArray(64 * 1024)
                        while (pos <= end) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                            onBytes(n.toLong())
                        }
                    }
                }
                if (pos <= end) throw java.io.IOException("connection closed early")
            } catch (t: java.io.IOException) {
                if (++attempt >= MAX_PART_RETRIES) throw t
            }
        }
    }

    private fun downloadSingle(modelName: String, url: String, zipFile: File): Boolean {
    httpClient().newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w("VoskModelRegistry", "Download of ${modelName} failed: HTTP ${resp.code} from $url")
                val reason = if (resp.code == 404) {
                    "Download failed (HTTP 404): this model is no longer published"
                } else {
                    "Download failed (HTTP ${resp.code})"
                }
                setError(modelName, reason)
                reportProgress(modelName, null)
                return false
            }
            val body = resp.body ?: run {
                setError(modelName, "Download failed: empty response")
                reportProgress(modelName, null)
                return false
            }
            val total = body.contentLength().coerceAtLeast(1)
            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        reportProgress(modelName, (downloaded.toFloat() / total).coerceIn(0f, 0.9f))
                    }
                }
            }
        }
        return true
    }

    private fun setError(modelName: String, message: String) {
        errorMap[modelName] = message
        errorFlow.value = errorMap.toMap()
    }

    private fun clearError(modelName: String) {
        errorMap.remove(modelName)
        errorFlow.value = errorMap.toMap()
    }

    private fun describeFailure(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Download failed: no internet connection"
        is java.net.SocketTimeoutException -> "Download failed: the connection timed out"
        is java.io.IOException -> "Download failed: ${t.message ?: "network error"}"
        else -> "Download failed: ${t.message ?: t::class.java.simpleName}"
    }

    /** Deletes a downloaded model from disk. Bundled models cannot be deleted. */
    suspend fun uninstall(modelName: String): Boolean = withContext(Dispatchers.IO) {
        val bundled = VoskModelCatalog.BUNDLED_MODELS.any { it.modelName == modelName }
        if (bundled) return@withContext false
        val dir = downloadedModelDir(modelName)
        val ok = if (dir.exists()) dir.deleteRecursively() else true
        if (ok) refresh()
        ok
    }

    fun lastError(modelName: String): String? = errorMap[modelName]

    // ── internals ──────────────────────────────────────────────────────────

    private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun reportProgress(modelName: String, progress: Float?) {
        val current = progressMap.value.toMutableMap()
        if (progress == null) current.remove(modelName) else current[modelName] = progress
        progressMap.value = current
    }

    private fun assetModelExists(modelName: String): Boolean {
        return try {
            !context.assets.list("models/$modelName").isNullOrEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun listDownloadedModelNames(): Set<String> {
        val root = File(context.filesDir, "vosk/models")
        if (!root.exists() || !root.isDirectory) return emptySet()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, ".installed").exists() }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    private fun downloadedModelDir(modelName: String): File {
        val root = File(context.filesDir, "vosk/models").apply { mkdirs() }
        return File(root, modelName)
    }

    private fun ensureBundledCopied(modelName: String): File {
        val targetDir = File(context.filesDir, "vosk/bundled/$modelName")
        val marker = File(targetDir, ".copied")
        if (!marker.exists()) {
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()
            copyAssetDirectory("models/$modelName", targetDir)
            marker.writeText("ok")
        }
        return targetDir
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        for (child in children) {
            val childAsset = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            if (context.assets.list(childAsset).isNullOrEmpty()) {
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDirectory(childAsset, childTarget)
            }
        }
    }

    /**
     * Vosk zips are packaged as `{modelName}/...`. We extract into a temp dir first,
     * then move the inner folder to [targetRoot]/[expectedName] and drop an
     * `.installed` marker so [refresh] can see it.
     */
    private fun unzipInto(zipFile: File, targetRoot: File, expectedName: String) {
        val finalDir = File(targetRoot, expectedName)
        if (finalDir.exists()) finalDir.deleteRecursively()
        val stagingDir = File(targetRoot, ".staging-$expectedName").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val zipIn = java.io.FileInputStream(zipFile)
        val zipLen = zipFile.length().coerceAtLeast(1)
        var lastPct = -1
        ZipInputStream(zipIn.buffered(256 * 1024)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                // Extraction of a big model takes a while; keep the bar moving from 90% to 100%.
                val pct = (zipIn.channel.position() * 100 / zipLen).toInt()
                if (pct != lastPct) { lastPct = pct; reportProgress(expectedName, 0.9f + pct / 1000f) }
                val outFile = File(stagingDir, entry.name)
                // Path traversal guard
                if (!outFile.canonicalPath.startsWith(stagingDir.canonicalPath)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        // Many Vosk zips wrap everything under a single top-level dir. Collapse it.
        val top = stagingDir.listFiles()?.singleOrNull { it.isDirectory }
        val sourceDir = top ?: stagingDir
        finalDir.parentFile?.mkdirs()
        if (!sourceDir.renameTo(finalDir)) {
            sourceDir.copyRecursively(finalDir, overwrite = true)
            sourceDir.deleteRecursively()
        }
        stagingDir.deleteRecursively()
        File(finalDir, ".installed").writeText("ok")
    }

    private companion object {
        const val PARALLEL_MIN_BYTES = 4L * 1024 * 1024
        const val PARTS = 6
        const val MAX_PART_RETRIES = 5
    }
}
