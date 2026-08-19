package com.recomp.gameshub.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recomp.gameshub.R
import com.recomp.gameshub.core.util.formatBytes
import com.recomp.gameshub.core.util.formatSpeed
import com.recomp.gameshub.core.util.formatEtaSimple
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import java.io.File

class DownloadNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ACTIVE = "downloads"
        const val CHANNEL_DONE = "download_complete"
        const val ACTION_PAUSE = "com.recomp.gameshub.action.PAUSE"
        const val ACTION_RESUME = "com.recomp.gameshub.action.RESUME"
        const val ACTION_CANCEL = "com.recomp.gameshub.action.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = NotificationChannel(
            CHANNEL_ACTIVE,
            "Downloads em andamento",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progresso dos downloads em andamento"
            setShowBadge(false)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "Downloads concluídos",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Aviso quando um download é concluído"
        }
        manager.createNotificationChannels(listOf(active, done))
    }

    fun activeNotification(task: DownloadTask): Notification {
        val percent = (task.progress * 100).toInt()
        val paused = task.phase == DownloadPhase.PAUSED
        val title = task.gameName

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent, task.totalBytes <= 0L)
            .setContentText(statusText(task))

        if (task.totalBytes > 0L) {
            val downloaded = formatBytes(task.downloadedBytes)
            val total = formatBytes(task.totalBytes)
            builder.setSubText("$downloaded de $total")
        }

        builder.addAction(
            if (paused) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.action_resume),
                    servicePendingIntent(ACTION_RESUME, task.id),
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.action_pause),
                    servicePendingIntent(ACTION_PAUSE, task.id),
                )
            }
        )
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_cancel),
                servicePendingIntent(ACTION_CANCEL, task.id),
            )
        )
        return builder.build()
    }

    private fun statusText(task: DownloadTask): String =
        when (task.phase) {
            DownloadPhase.PAUSED -> "Pausado • ${formatBytes(task.downloadedBytes)}"
            DownloadPhase.DOWNLOADING, DownloadPhase.PENDING -> {
                val speed = formatSpeed(task.speedBytesPerSec)
                val eta = formatEtaSimple(task)
                buildString {
                    append(speed)
                    if (eta != null) append(" • $eta")
                }
            }
            else -> "Preparando…"
        }

    fun completedNotification(task: DownloadTask): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download concluído")
            .setContentText("${task.gameName} pronto para instalar")
            .setContentIntent(installPendingIntent(task))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS or NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.stat_sys_download_done,
                    context.getString(R.string.action_install),
                    installPendingIntent(task),
                )
            )
            .build()

    fun failedNotification(task: DownloadTask): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Falha no download")
            .setContentText(task.errorMessage ?: "Não foi possível concluir o download de ${task.gameName}")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    fun notify(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permissão de notificações negada — ignorado silenciosamente.
        }
    }

    private fun servicePendingIntent(action: String, taskId: String): PendingIntent {
        val intent = Intent(context, DownloadService::class.java)
            .setAction(action)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getService(
            context,
            requestCode(action, taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun installPendingIntent(task: DownloadTask): PendingIntent {
        val intent = InstallHelper.installIntent(context, File(task.localPath))
        return PendingIntent.getActivity(
            context,
            requestCode("install", task.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(action: String, taskId: String): Int {
        var hash = 17
        hash = hash * 31 + action.hashCode()
        hash = hash * 31 + taskId.hashCode()
        hash = hash * 31 + 7
        return hash and 0x7fffffff
    }
}