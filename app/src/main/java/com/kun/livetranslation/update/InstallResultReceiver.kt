package com.kun.livetranslation.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat

/** Receives the PackageInstaller result: shows the system install prompt, or explains a failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Starting an activity from the background can be blocked, so also offer a notification.
                runCatching { context.startActivity(confirm) }
                notify(
                    context, "LiveN update ready", "Tap to install",
                    PendingIntent.getActivity(context, 0, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the new version restarts the app
            else -> notify(context, "LiveN update failed", explain(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)), null)
        }
    }

    private fun explain(status: Int, system: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "The update is signed with a different key than the installed app. Uninstall LiveN once and install the new APK."
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free storage to install the update."
        else -> system ?: "The install failed."
    }

    private fun notify(context: Context, title: String, text: String, tap: PendingIntent?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App update install", NotificationManager.IMPORTANCE_HIGH))
        }
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .apply { tap?.let { setContentIntent(it) } }
                .build()
        )
    }

    private companion object {
        const val CHANNEL = "liven_update_install"
        const val NOTIFICATION_ID = 4103
    }
}
