package com.recomp.gameshub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.RecompTheme
import com.recomp.gameshub.core.navigation.AppNavHost
import com.recomp.gameshub.data.repository.AppSettings
import com.recomp.gameshub.data.repository.RepoUpdateInfo
import com.recomp.gameshub.data.repository.ThemeMode
import com.recomp.gameshub.presentation.setup.SetupRoute
import com.recomp.gameshub.presentation.update.RepoUpdateDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as RecompApplication
            val settings by app.container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            RecompTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as RecompApplication

    var bootReady by remember { mutableStateOf(false) }
    var needSetup by remember { mutableStateOf(false) }
    var pendingRepoUpdate by remember { mutableStateOf<RepoUpdateInfo.Available?>(null) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // A notificação é solicitada na Etapa 1 do setup; se o app já estava
            // configurado, pedimos aqui para manter o progresso dos downloads.
            runCatching { app.container.downloadRepository.restoreInterrupted() }
        }
        needSetup = !app.container.repoRepository.isConfigured()
        bootReady = true
    }

    // Verificação de atualização do repositório: só mostra o diálogo quando há
    // de fato uma versão mais nova disponível (verificado contra a versão local).
    LaunchedEffect(bootReady, needSetup) {
        if (bootReady && !needSetup) {
            app.container.repoManager.checkForUpdate()
                .onSuccess { update ->
                    if (update is RepoUpdateInfo.Available) {
                        pendingRepoUpdate = update
                    }
                }
        }
    }

    if (!bootReady) return

    if (needSetup) {
        SetupRoute(onComplete = { needSetup = false })
        return
    }

    pendingRepoUpdate?.let { update ->
        RepoUpdateDialog(
            update = update,
            onUpdate = {
                kotlinx.coroutines.MainScope().launch {
                    app.container.repoManager.applyUpdate()
                    pendingRepoUpdate = null
                }
            },
            onDismiss = { pendingRepoUpdate = null },
        )
    }

    AppNavHost()
}
