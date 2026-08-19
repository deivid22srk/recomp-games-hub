package com.recomp.gameshub.download

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

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

    fun installIntent(context: Context, file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            Uri.fromFile(file)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            data = uri
            type = "application/vnd.android.package-archive"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    fun installPackage(context: Context, file: File) {
        if (!canRequestInstalls(context)) {
            openInstallPermissionSettings(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.startActivity(installIntent(context, file))
        } else {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = Uri.fromFile(file)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}