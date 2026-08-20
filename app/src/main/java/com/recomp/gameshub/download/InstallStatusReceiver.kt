package com.recomp.gameshub.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.recomp.gameshub.RecompApplication
import com.recomp.gameshub.data.repository.InstalledGamesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_INSTALL_PATH)?.let { path ->
            val file = File(path)
            val slug = intent.getStringExtra(EXTRA_SLUG)
            if (slug != null && slug.isNotBlank() && file.exists()) {
                val repo = (context.applicationContext as? RecompApplication)?.container?.installedGamesRepository
                if (repo != null) {
                    val result = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        val identity = InstallHelper.readApkIdentity(context, file)
                        if (identity != null) {
                            repo.remember(
                                slug = slug,
                                packageName = identity.packageName,
                                versionName = identity.versionName,
                                versionCode = identity.versionCode,
                            )
                        }
                        result.finish()
                    }
                }
            }
            InstallHelper.installPackage(context, file)
            return
        }
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                android.util.Log.i("InstallHelper", "APK instalado com sucesso")
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                android.util.Log.e("InstallHelper", "Instalação rejeitada: $message")
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_INSTALL_PATH = "extra_install_path"
        const val EXTRA_SLUG = "extra_slug"
    }
}