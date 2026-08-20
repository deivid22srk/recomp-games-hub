package com.recomp.gameshub.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.recomp.gameshub.R
import com.recomp.gameshub.RecompApplication
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    private var startedForeground = false

    private val notifier by lazy { DownloadNotifier(applicationContext) }
    private val engine by lazy { (application as RecompApplication).container.downloadEngine }

    private val repository by lazy { (application as RecompApplication).container.downloadRepository }

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        if (!startedForeground) {
            try {
                ServiceCompat.startForeground(
                    this,
                    PLACEHOLDER_NOTIFICATION_ID,
                    placeholderNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                startedForeground = true
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "startForeground falhou no onCreate", e)
                startedForeground = false
            }
        }
        engine.start(scope)
        collectorJob = scope.launch {
            repository.tasks.collect { tasks ->
                val active = tasks.filter { it.phase.isActive }
                val nonActive = tasks.filter { it.phase != DownloadPhase.PENDING && it.phase != DownloadPhase.DOWNLOADING && it.phase != DownloadPhase.PAUSED }
                refreshNotifications(active, nonActive)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(DownloadNotifier.EXTRA_TASK_ID)
        when (action) {
            DownloadNotifier.ACTION_PAUSE -> taskId?.let { repository.pause(it) }
            DownloadNotifier.ACTION_RESUME -> taskId?.let { repository.resume(it) }
            DownloadNotifier.ACTION_CANCEL -> taskId?.let { repository.cancel(it) }
            else -> Unit
        }
        notifier.ensureChannels()
        if (!startedForeground) {
            try {
                ServiceCompat.startForeground(
                    this,
                    PLACEHOLDER_NOTIFICATION_ID,
                    placeholderNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                startedForeground = true
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "startForeground falhou no onStartCommand", e)
                startedForeground = false
            }
        }
        engine.start(scope)
        scope.launch { repository.tasks.first().let { refreshNotifications(it.filter { t -> t.phase.isActive }, emptyList()) } }
        return START_STICKY
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, DownloadNotifier.CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Preparando downloads…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private suspend fun refreshNotifications(active: List<com.recomp.gameshub.domain.model.DownloadTask>, others: List<com.recomp.gameshub.domain.model.DownloadTask>) {
        if (active.isEmpty()) {
            // StateFlow starts with an empty value while its upstream is being
            // connected. Do not tear down the foreground service if the real
            // repository snapshot already contains a pending task.
            if (repository.hasActiveWork()) {
                engine.start(scope)
                return
            }
            if (startedForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                startedForeground = false
            }
            if (others.isNotEmpty()) {
                others.forEach { task -> notifyNonActive(task) }
            }
            stopSelf()
            return
        }

        var first = true
        for (task in active) {
            val notification = notifier.activeNotification(task)
            val notifId = notificationId(task.id)
            if (first) {
                try {
                    ServiceCompat.startForeground(
                        this,
                        notifId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                    startedForeground = true
                } catch (e: Exception) {
                    startedForeground = false
                    notifier.notify(notifId, notification)
                }
                first = false
            } else {
                notifier.notify(notifId, notification)
            }
        }
    }

    private fun notifyNonActive(task: com.recomp.gameshub.domain.model.DownloadTask) {
        val notifId = notificationId(task.id) + 1_000
        val notification = when (task.phase) {
            DownloadPhase.COMPLETED -> notifier.completedNotification(task)
            DownloadPhase.FAILED -> notifier.failedNotification(task)
            else -> null
        }
        if (notification != null) {
            notifier.notify(notifId, notification)
        }
    }

    private fun notificationId(taskId: String): Int = taskId.hashCode() and 0x7fffffff

    override fun onDestroy() {
        engine.stop()
        collectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val PLACEHOLDER_NOTIFICATION_ID = 0x9001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java),
            )
        }
    }
}
