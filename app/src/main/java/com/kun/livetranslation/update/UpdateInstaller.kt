package com.kun.livetranslation.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** What the UI shows while an in-app update runs. [percent] is only meaningful while downloading. */
data class UpdateProgress(val state: State, val percent: Int = 0, val message: String? = null) {
    enum class State { DOWNLOADING, INSTALLING, FAILED }
}

/**
 * In-app updates: the APK is downloaded by a WorkManager job (a foreground service with a progress
 * notification, so it keeps going when the app is closed), then handed to Android's PackageInstaller.
 * Android always asks the user to confirm the install; there is no silent install for normal apps.
 * Updates only install over the same signing key as the running app.
 */
object UpdateInstaller {
    const val WORK_NAME = "liven_update_download"
    const val KEY_URL = "url"
    const val KEY_TAG = "tag"
    const val KEY_PERCENT = "percent"
    const val KEY_PHASE = "phase"
    const val KEY_ERROR = "error"
    const val PHASE_INSTALL = "install"

    fun start(context: Context, info: UpdateInfo) {
        val url = info.apkDownloadUrl ?: return
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(workDataOf(KEY_URL to url, KEY_TAG to info.tagName))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun progress(context: Context): Flow<UpdateProgress?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val info = infos.firstOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UpdateProgress(UpdateProgress.State.DOWNLOADING, 0)
                WorkInfo.State.RUNNING ->
                    if (info.progress.getString(KEY_PHASE) == PHASE_INSTALL) UpdateProgress(UpdateProgress.State.INSTALLING)
                    else UpdateProgress(UpdateProgress.State.DOWNLOADING, info.progress.getInt(KEY_PERCENT, 0))
                WorkInfo.State.SUCCEEDED -> UpdateProgress(UpdateProgress.State.INSTALLING)
                WorkInfo.State.FAILED -> UpdateProgress(UpdateProgress.State.FAILED, message = info.outputData.getString(KEY_ERROR))
                WorkInfo.State.CANCELLED -> null
            }
        }

    /** "Install unknown apps" must be allowed for LiveN once before it can install updates. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands [apk] to PackageInstaller; the result arrives at [InstallResultReceiver]. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Skips the confirmation only when Android allows it (updates by the installing app).
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("liven.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(result.intentSender)
        }
    }
}
