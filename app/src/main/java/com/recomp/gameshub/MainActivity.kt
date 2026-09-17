package com.recomp.gameshub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.RecompTheme
import com.recomp.gameshub.core.navigation.AppNavHost
import com.recomp.gameshub.data.repository.AppSettings
import com.recomp.gameshub.data.repository.RepoUpdateInfo
import com.recomp.gameshub.data.repository.ThemeMode
import com.recomp.gameshub.download.DownloadService
import com.recomp.gameshub.presentation.setup.SetupRoute
import com.recomp.gameshub.presentation.update.RepoUpdateDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as RecompApplication
            // null enquanto o DataStore carrega: evita piscar o tema claro
            // para usuários com tema escuro configurado.
            val settings by app.container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null as AppSettings?)

            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }

            // Mantém as system bars em sincronia com o tema escolhido no app
            // (não apenas com o modo do sistema), evitando ícones invisíveis.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.Transparent.toArgb())
                    } else {
                        SystemBarStyle.light(
                            Color.Transparent.toArgb(),
                            Color.Transparent.toArgb(),
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.Transparent.toArgb())
                    } else {
                        SystemBarStyle.light(
                            Color.Transparent.toArgb(),
                            Color.Transparent.toArgb(),
                        )
                    },
                )
            }

            val loaded = settings
            if (loaded == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F1512)),
                )
                return@setContent
            }

            RecompTheme(
                darkTheme = darkTheme,
                dynamicColor = loaded.dynamicColor,
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
    var applyingUpdate by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        runCatching { app.container.downloadRepository.restoreInterrupted() }
        needSetup = !app.container.repoRepository.isConfigured()
        bootReady = true
    }

    // Solicita a permissão de notificações caso o usuário tenha pulado o setup
    // ou negado antes — mantém o progresso dos downloads visível.
    LaunchedEffect(bootReady) {
        if (bootReady &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Downloads restantes de sessões anteriores retomam quando o app abre em
    // primeiro plano (iniciar FGS em background é bloqueado no Android 15).
    LaunchedEffect(bootReady, needSetup) {
        if (bootReady && !needSetup && app.container.downloadRepository.hasPendingWork()) {
            runCatching { DownloadService.start(context) }
        }
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
            isUpdating = applyingUpdate,
            onUpdate = {
                if (!applyingUpdate) {
                    applyingUpdate = true
                    app.appScope.launch {
                        val result = runCatching { app.container.repoManager.applyUpdate() }
                        applyingUpdate = false
                        pendingRepoUpdate = null
                        result.onFailure { failure ->
                            updateError = failure.message ?: "Falha ao atualizar o catálogo."
                        }
                    }
                }
            },
            onDismiss = { if (!applyingUpdate) pendingRepoUpdate = null },
        )
    }

    updateError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { updateError = null },
            title = { androidx.compose.material3.Text("Falha na atualização") },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { updateError = null }) {
                    androidx.compose.material3.Text("OK")
                }
            },
        )
    }

    AppNavHost()
}
