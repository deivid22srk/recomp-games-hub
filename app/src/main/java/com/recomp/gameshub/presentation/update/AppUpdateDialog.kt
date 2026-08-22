package com.recomp.gameshub.presentation.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomp.gameshub.BuildConfig
import com.recomp.gameshub.domain.model.AppUpdateInfo
import com.recomp.gameshub.domain.model.AppVersions

/**
 * Whether [update] is newer than the APK the user is currently running.
 * Compares primarily by version name (semver-ish) so publishing works even
 * if the build's versionCode is forgotten; version code is the fallback.
 */
fun AppUpdateInfo.isNewerThanInstalled(): Boolean =
    AppVersions.isOutdated(BuildConfig.VERSION_NAME, versionName) ||
        versionCode > BuildConfig.VERSION_CODE

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val openDownload = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Atualização disponível") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A versão ${update.versionName} do Recomp Hub está disponível.")
                Text(
                    text = "Você está usando a versão ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                update.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Novidades:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = openDownload) { Text("Baixar atualização") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Agora não") }
        },
    )
}
