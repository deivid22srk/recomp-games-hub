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
            // Restaura o estado das tarefas (marca como PENDING). Iniciar um
            // foreground service dataSync em background é bloqueado a partir do
            // Android 15; os downloads pendentes retomam sozinhos quando o app
            // for aberto em primeiro plano (MainActivity aciona o serviço).
            app.container.downloadRepository.restoreInterrupted()
        }
    }
}
