package com.recomp.gameshub.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomp.gameshub.data.repository.RepoUpdateInfo

@Composable
fun RepoUpdateDialog(
    update: RepoUpdateInfo.Available,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    isUpdating: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdateAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Atualização do catálogo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Há uma nova versão do catálogo disponível.")
                Text(
                    text = buildString {
                        append("Versão atual: ")
                        append(update.installedVersion?.takeIf { it.isNotBlank() } ?: "—")
                        append("\nNova versão: ")
                        append(update.version)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isUpdating) {
                    Text(
                        text = "Baixando e atualizando o catálogo…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !isUpdating) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Atualizar")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isUpdating) { Text("Agora não") }
        },
    )
}
