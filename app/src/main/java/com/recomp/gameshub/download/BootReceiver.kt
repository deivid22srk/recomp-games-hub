package com.recomp.gameshub.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recomp.gameshub.RecompApplication
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as RecompApplication
        app.appScope.launch {
            app.container.downloadRepository.restoreInterrupted()
            if (app.container.downloadRepository.hasPendingWork()) {
                DownloadService.start(context)
            }
        }
    }
}