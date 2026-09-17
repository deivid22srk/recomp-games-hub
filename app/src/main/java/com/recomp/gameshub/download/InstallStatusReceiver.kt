package com.recomp.gameshub.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.recomp.gameshub.RecompApplication
import com.recomp.gameshub.data.repository.InstalledGamesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // A notificação de "Download concluído" dispara este receiver sem
        // EXTRA_STATUS, apenas com o caminho do APK: nesse caso é um pedido
        // de instalação direto (o botão "Instalar" da notificação).
        val hasStatus = intent.hasExtra(PackageInstaller.EXTRA_STATUS)
        if (!hasStatus) {
            val path = intent.getStringExtra(EXTRA_INSTALL_PATH)
            val slug = intent.getStringExtra(EXTRA_SLUG)
            val file = path?.let { File(it) }
            if (file != null && file.exists()) {
                InstallHelper.installPackage(context, file, slug)
            }
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
                recordInstalledMapping(context, intent)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                android.util.Log.e("InstallHelper", "Instalação rejeitada: $message")
            }
        }
    }

    /**
     * Grava o mapeamento slug → pacote somente após a instalação de fato
     * concluir. Antes, o registro era feito antes do commit: se o usuário
     * cancelasse o instalador, o app continuaria exibindo "Instalado".
     */
    private fun recordInstalledMapping(context: Context, intent: Intent) {
        val slug = intent.getStringExtra(EXTRA_SLUG)?.takeIf { it.isNotBlank() } ?: return
        val path = intent.getStringExtra(EXTRA_INSTALL_PATH) ?: return
        val file = File(path)
        if (!file.exists()) return
        val app = context.applicationContext as? RecompApplication ?: return
        val repo = app.container.installedGamesRepository
        val result = goAsync()
        // Escopo do application (SupervisorJob compartilhado) em vez de um
        // CoroutineScope ad-hoc: a gravação acompanha o ciclo de vida do app.
        app.appScope.launch(Dispatchers.IO) {
            try {
                val identity = InstallHelper.readApkIdentity(context, file)
                if (identity != null) {
                    repo.remember(
                        slug = slug,
                        packageName = identity.packageName,
                        versionName = identity.versionName,
                        versionCode = identity.versionCode,
                    )
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_INSTALL_PATH = "extra_install_path"
        const val EXTRA_SLUG = "extra_slug"
    }
}
