package com.recomp.gameshub.presentation.repo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.SectionHeader
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.data.repository.LocalGameDraft
import com.recomp.gameshub.presentation.setup.SetupRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoEditorRoute(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val viewModel: RepoEditorViewModel = appViewModel {
        RepoEditorViewModel(it.repoRepository, it.localRepoRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localGames by viewModel.localGames.collectAsStateWithLifecycle()
    val repoConfig by viewModel.repoConfig.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Criar fonte de dados", onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AnimatedVisibility(
                    visible = state is RepoEditorState.Success,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    (state as? RepoEditorState.Success)?.let { s ->
                        InfoBanner(
                            message = s.message,
                            isError = false,
                            leadingIcon = Icons.Rounded.CloudUpload,
                            onDismiss = viewModel::dismissState,
                        )
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = state is RepoEditorState.Error,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    (state as? RepoEditorState.Error)?.let { e ->
                        InfoBanner(
                            message = e.message,
                            isError = true,
                            onDismiss = viewModel::dismissState,
                        )
                    }
                }
            }

            item {
                SectionHeader("Fonte atual")
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fonte ativa", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = repoConfig?.displayName ?: "Nenhuma (use o setup)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(onClick = onOpenSetup) { Text("Trocar") }
                    }
                }
            }

            item {
                SectionHeader("Criar repositório local", Modifier.padding(top = 12.dp))
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.Folder,
                    title = "Criar repositório local",
                    description = "Cria uma estrutura de repositório na pasta do app e a define como fonte ativa.",
                    loading = state is RepoEditorState.Loading,
                    onClick = viewModel::createLocalRepo,
                )
            }

            item {
                SectionHeader("Meus jogos", Modifier.padding(top = 12.dp))
            }
            if (localGames.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum jogo no repositório local ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            } else {
                localGames.forEach { name ->
                    item(key = name) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleSmall)
                                }
                                TextButton(onClick = { viewModel.removeGame(slugOf(name)) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remover",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remover", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Adicionar jogo próprio")
                }
            }

            item {
                SectionHeader("Publicar no GitHub", Modifier.padding(top = 12.dp))
            }
            item {
                Text(
                    text = "Envie seu repositório local para um repositório do GitHub. " +
                        "Crie o repositório no GitHub, informe a URL e o seu token de acesso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = viewModel::onUrlChanged,
                    label = { Text("URL do repositório") },
                    placeholder = { Text("https://github.com/usuario/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = token,
                    onValueChange = viewModel::onTokenChanged,
                    label = { Text("Token de acesso (GitHub)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = viewModel::publishToGithub,
                    enabled = url.isNotBlank() && token.isNotBlank() &&
                        state !is RepoEditorState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (state is RepoEditorState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Publicando…")
                    } else {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publicar no GitHub")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        AddGameDialog(
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                viewModel.addGame(draft)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun AddGameDialog(
    onDismiss: () -> Unit,
    onSave: (LocalGameDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var sourceRepo by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar jogo") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(platform, { platform = it }, label = { Text("Plataforma original") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(version, { version = it }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(sourceRepo, { sourceRepo = it }, label = { Text("Repositório do projeto") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(downloadUrl, { downloadUrl = it }, label = { Text("URL do APK") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(size, { size = it }, label = { Text("Tamanho (bytes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags (vírgulas)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        LocalGameDraft(
                            slug = name,
                            name = name.trim(),
                            description = description.trim(),
                            originalPlatform = platform.trim().ifBlank { null },
                            version = version.trim().ifBlank { null },
                            author = author.trim().ifBlank { null },
                            sourceRepo = sourceRepo.trim().ifBlank { null },
                            downloadUrl = downloadUrl.trim().ifBlank { null },
                            fileSizeBytes = size.trim().toLongOrNull() ?: 0L,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private fun slugOf(name: String): String =
    name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
