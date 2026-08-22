package com.recomp.gameshub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.RecompTheme
import com.recomp.gameshub.core.navigation.AppNavHost
import com.recomp.gameshub.data.repository.AppSettings
import com.recomp.gameshub.data.repository.ThemeMode
import com.recomp.gameshub.domain.model.AppUpdateInfo
import com.recomp.gameshub.presentation.update.AppUpdateDialog
import com.recomp.gameshub.presentation.update.isNewerThanInstalled

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

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = context.applicationContext as RecompApplication
        app.container.authRepository.refreshIfNeeded()
    }

    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val app = context.applicationContext as RecompApplication
        app.container.contributionRepository.latestAppRelease()
            .onSuccess { release ->
                if (release != null && release.isNewerThanInstalled()) {
                    pendingUpdate = release
                }
            }
    }
    pendingUpdate?.let { update ->
        AppUpdateDialog(update = update, onDismiss = { pendingUpdate = null })
    }

    AppNavHost()
}