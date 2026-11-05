package com.kun.livetranslation.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the release APK in the background (resuming a partial file if there is one), checks it
 * is really this app, and starts the install. Progress is published for the UI and the notification.
 */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(UpdateInstaller.KEY_URL) ?: return@withContext fail("No download URL")
        val tag = inputData.getString(UpdateInstaller.KEY_TAG) ?: "update"
        try {
            setForeground(foreground(0))
            val dir = File(applicationContext.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.filter { !it.name.startsWith(tag) }?.forEach { it.delete() } // older versions
            val apk = File(dir, "$tag.apk")
            if (!apk.exists() || !looksLikeThisApp(apk)) {
                apk.delete()
                download(url, File(dir, "$tag.apk.part"), apk)
            }
            if (!looksLikeThisApp(apk)) {
                apk.delete()
                return@withContext fail("The downloaded file is not a LiveN update")
            }
            setProgress(workDataOf(UpdateInstaller.KEY_PHASE to UpdateInstaller.PHASE_INSTALL))
            UpdateInstaller.install(applicationContext, apk)
            Result.success()
        } catch (t: Throwable) {
            if (isStopped) Result.failure() else fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun fail(message: String): Result = Result.failure(workDataOf(UpdateInstaller.KEY_ERROR to message))

    private fun looksLikeThisApp(apk: File): Boolean {
        val info = applicationContext.packageManager.getPackageArchiveInfo(apk.path, 0)
        return info?.packageName == applicationContext.packageName
    }

    private suspend fun download(url: String, part: File, target: File) {
        val have = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(request).execute().use { resp ->
            val resumed = resp.code == 206
            if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val offset = if (resumed) have else 0L
            val total = offset + body.contentLength()
            var done = offset
            var lastPercent = -1
            FileOutputStream(part, resumed).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw IOException("Cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != lastPercent) {
                            lastPercent = pct
                            setProgress(workDataOf(UpdateInstaller.KEY_PERCENT to pct))
                            if (pct % 3 == 0) setForeground(foreground(pct))
                        }
                    }
                }
            }
            if (total > 0 && part.length() != total) throw IOException("Download incomplete, try again")
        }
        if (!part.renameTo(target)) throw IOException("Could not save the update")
    }

    private fun foreground(percent: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App update download", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading LiveN update")
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "liven_update_download"
        const val NOTIFICATION_ID = 4102
    }
}
