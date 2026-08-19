package com.recomp.gameshub.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.recomp.gameshub.R
import com.recomp.gameshub.RecompApplication

class ResumeDownloadsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RecompApplication
        DownloadNotifier(applicationContext).ensureChannels()
        try {
            setForeground(
                ForegroundInfo(
                    notificationId,
                    NotificationCompat.Builder(applicationContext, DownloadNotifier.CHANNEL_ACTIVE)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(applicationContext.getString(R.string.app_name))
                        .setContentText("Preparando downloads…")
                        .setOngoing(true)
                        .build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            )
        } catch (e: Exception) {
            // Sem permissão de notificação/autoridade para startForeground:
            // segue sem o serviço em primeiro plano.
        }

        app.container.downloadRepository.restoreInterrupted()
        if (app.container.downloadRepository.hasPendingWork()) {
            DownloadService.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val notificationId = 1000
    }
}