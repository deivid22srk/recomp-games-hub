package com.recomp.gameshub.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.GameSubmission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReviewRoute(
    onBack: () -> Unit,
) {
    val viewModel: AdminReviewViewModel = appViewModel {
        AdminReviewViewModel(it.contributionRepository, it.authRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var rejectSlug by remember { mutableStateOf<String?>(null) }
    var editSubmission by remember { mutableStateOf<GameSubmission?>(null) }
    var deleteSubmission by remember { mutableStateOf<GameSubmission?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Revisão de contribuições",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.isAdmin) {
                item {
                    EmptyStateBox(
                        title = "Acesso restrito",
                        message = "Apenas administradores podem revisar contribuições.",
                        icon = Icons.Rounded.Close,
                    )
                }
                return@LazyColumn
            }

            uiState.error?.let {
                item { InfoBanner(it, isError = true) }
            }
            uiState.successMessage?.let {
                item { InfoBanner(it, isError = false) }
            }

            if (uiState.isLoading) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            } else if (uiState.pending.isEmpty()) {
                item {
                    EmptyStateBox(
                        title = "Nada pendente",
                        message = "Não há contribuições aguardando revisão.",
                        icon = Icons.Rounded.Check,
                    )
                }
            } else {
                uiState.pending.forEach { submission ->
                    item(key = submission.slug) {
                        AdminSubmissionCard(
                            submission = submission,
                            onApprove = { viewModel.approve(submission.slug) },
                            onReject = { rejectSlug = submission.slug },
                            onEdit = { editSubmission = submission },
                            onDelete = { deleteSubmission = submission },
                            showReviewActions = true,
                        )
                    }
                }
            }

            if (uiState.isAdmin && uiState.all.isNotEmpty()) {
                item { Text("Todas as contribuições", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
                uiState.all.forEach { submission ->
                    item(key = "all-${submission.submissionId}") {
                        AdminSubmissionCard(
                            submission = submission,
                            onApprove = { viewModel.approve(submission.slug) },
                            onReject = { rejectSlug = submission.slug },
                            onEdit = { editSubmission = submission },
                            onDelete = { deleteSubmission = submission },
                            showReviewActions = submission.status == "pending",
                        )
                    }
                }
            }
        }
    }

    rejectSlug?.let { slug ->
        RejectDialog(
            submissionName = uiState.pending.firstOrNull { it.slug == slug }?.name ?: slug,
            onConfirm = { reason ->
                viewModel.reject(slug, reason)
                rejectSlug = null
            },
            onDismiss = { rejectSlug = null },
        )
    }
    editSubmission?.let { submission ->
        AdminEditDialog(
            submission = submission,
            onSave = { viewModel.update(it); editSubmission = null },
            onDismiss = { editSubmission = null },
        )
    }
    deleteSubmission?.let { submission ->
        AlertDialog(
            onDismissRequest = { deleteSubmission = null },
            title = { Text("Excluir contribuição?") },
            text = { Text("A contribuição «${submission.name}» será removida permanentemente.") },
            confirmButton = { Button(onClick = { viewModel.delete(submission.slug); deleteSubmission = null }) { Text("Excluir") } },
            dismissButton = { OutlinedButton(onClick = { deleteSubmission = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AdminSubmissionCard(
    submission: GameSubmission,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showReviewActions: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(submission.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            textIf("slug: ${submission.slug}", submission.slug.isNotBlank())
            textIf("Descrição: ${submission.description}", submission.description.isNotBlank())
            textIf("Plataforma: ${submission.originalPlatform}", !submission.originalPlatform.isNullOrBlank())
            textIf("Autor: ${submission.author}", !submission.author.isNullOrBlank())
            textIf("Versão: ${submission.version}", !submission.version.isNullOrBlank())
            textIf("Repositório: ${submission.sourceRepo}", !submission.sourceRepo.isNullOrBlank())
            submission.apkUrl?.let {
                Text(
                    "APK: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            submission.coverUrl?.let {
                Text(
                    "Capa: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (submission.tags.isNotEmpty()) {
                textIf("Tags: ${submission.tags.joinToString(", ")}", submission.tags.isNotEmpty())
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp)); Text("Editar")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp)); Text("Excluir")
                }
            }
            if (showReviewActions) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp)); Text("Rejeitar")
                    }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp)); Text("Aprovar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.textIf(text: String, show: Boolean) {
    if (show) {
        Spacer(Modifier.height(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminEditDialog(
    submission: GameSubmission,
    onSave: (GameSubmission) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(submission.name) }
    var description by remember { mutableStateOf(submission.description) }
    var platform by remember { mutableStateOf(submission.originalPlatform.orEmpty()) }
    var author by remember { mutableStateOf(submission.author.orEmpty()) }
    var version by remember { mutableStateOf(submission.version.orEmpty()) }
    var repository by remember { mutableStateOf(submission.sourceRepo.orEmpty()) }
    var apkUrl by remember { mutableStateOf(submission.apkUrl.orEmpty()) }
    var size by remember { mutableStateOf(submission.fileSizeBytes.toString()) }
    var tags by remember { mutableStateOf(submission.tags.joinToString(", ")) }
    var cover by remember { mutableStateOf(submission.coverUrl.orEmpty()) }
    var banner by remember { mutableStateOf(submission.bannerUrl.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar contribuição") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(platform, { platform = it }, label = { Text("Plataforma") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(version, { version = it }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(repository, { repository = it }, label = { Text("Repositório") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(apkUrl, { apkUrl = it }, label = { Text("URL do APK") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(size, { size = it }, label = { Text("Tamanho em bytes") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags separadas por vírgula") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(cover, { cover = it }, label = { Text("URL da capa") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(banner, { banner = it }, label = { Text("URL do banner") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(submission.copy(
                    slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                    name = name.trim(), description = description.trim(),
                    originalPlatform = platform.trim().ifBlank { null }, author = author.trim().ifBlank { null },
                    version = version.trim().ifBlank { null }, sourceRepo = repository.trim().ifBlank { null },
                    apkUrl = apkUrl.trim().ifBlank { null }, fileSizeBytes = size.toLongOrNull() ?: 0L,
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                    coverUrl = cover.trim().ifBlank { null }, bannerUrl = banner.trim().ifBlank { null },
                ))
            }, enabled = name.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RejectDialog(
    submissionName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rejeitar «$submissionName»") },
        text = {
            Column {
                Text(
                    "Informe um motivo. Ele será mostrado ao autor da contribuição.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank(),
            ) { Text("Rejeitar") }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Cancelar") }
        },
    )
}
