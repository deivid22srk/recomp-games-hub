package com.recomp.gameshub.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

object InstallHelper {

    fun canRequestInstalls(context: Context): Boolean {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installPackage(context: Context, file: File) {
        if (!canRequestInstalls(context)) {
            openInstallPermissionSettings(context)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        CoroutineScope(Dispatchers.IO).launch {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(file.length())
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    val input = FileInputStream(file)
                    try {
                        val output = session.openWrite("base.apk", 0, file.length())
                        try {
                            input.copyTo(output)
                            session.fsync(output)
                        } finally {
                            output.close()
                        }
                    } finally {
                        input.close()
                    }
                    val callback = Intent(context, InstallStatusReceiver::class.java).apply {
                        putExtra(InstallStatusReceiver.EXTRA_SESSION_ID, sessionId)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val status = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                    session.commit(status.intentSender)
                }
            } catch (error: Exception) {
                runCatching { installer.abandonSession(sessionId) }
                android.util.Log.e("InstallHelper", "Falha ao preparar instalação", error)
            }
        }
    }
}
