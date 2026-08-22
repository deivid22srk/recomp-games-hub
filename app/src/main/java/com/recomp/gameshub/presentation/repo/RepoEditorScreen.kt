package com.recomp.gameshub.presentation.repo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.SectionHeader
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.data.repository.LocalGameDraft
import com.recomp.gameshub.domain.model.RepoSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoEditorRoute(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val viewModel: RepoEditorViewModel = appViewModel {
        RepoEditorViewModel(it.repoRepository, it.localRepoRepository, it.repoManager, it.catalogRepository)
    }
    val section by viewModel.section.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repoConfig by viewModel.repoConfig.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state is RepoOpState.Success) {
            kotlinx.coroutines.delay(4_000)
            viewModel.dismissState()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = section.title(),
                onBack = {
                    if (section == RepoSection.HOME) onBack() else viewModel.navigateTo(RepoSection.HOME)
                },
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
            item {
                OpBanner(state = state, onDismiss = viewModel::dismissState)
            }

            item {
                AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it * dir } + fadeOut())
                    },
                    label = "repoSection",
                ) { current ->
                    when (current) {
                        RepoSection.HOME -> RepoHome(
                            repoConfig = repoConfig,
                            gameCount = summaries.size,
                            onContinue = { viewModel.navigateTo(RepoSection.CONTINUE) },
                            onCreateLocal = { viewModel.navigateTo(RepoSection.CREATE_LOCAL) },
                            onManageGames = { viewModel.navigateTo(RepoSection.MANAGE_GAMES) },
                            onPublish = { viewModel.navigateTo(RepoSection.PUBLISH) },
                        )
                        RepoSection.CONTINUE -> ContinueRepoSection(
                            url = viewModel.continueUrl.collectAsStateWithLifecycle().value,
                            onUrlChanged = viewModel::onContinueUrlChanged,
                            loading = state is RepoOpState.Loading,
                            onUseDefault = viewModel::continueWithDefault,
                            onUseUrl = { viewModel.continueWithUrl(viewModel.continueUrl.value) },
                            onOpenSetup = onOpenSetup,
                        )
                        RepoSection.CREATE_LOCAL -> CreateLocalSection(
                            loading = state is RepoOpState.Loading,
                            onCreate = viewModel::createLocalRepo,
                        )
                        RepoSection.MANAGE_GAMES -> ManageGamesSection(
                            games = summaries,
                            editingSlug = viewModel.editingSlug.collectAsStateWithLifecycle().value,
                            editingDraft = viewModel.editingDraft.collectAsStateWithLifecycle().value,
                            loading = state is RepoOpState.Loading,
                            onEdit = viewModel::startEdit,
                            onSaveEdit = viewModel::saveEdit,
                            onCancelEdit = viewModel::cancelEdit,
                            onAdd = viewModel::addGame,
                            onDeleteRequest = { confirmDelete = it },
                            onDelete = viewModel::deleteGame,
                        )
                        RepoSection.PUBLISH -> PublishSection(
                            url = viewModel.publishUrl.collectAsStateWithLifecycle().value,
                            token = viewModel.publishToken.collectAsStateWithLifecycle().value,
                            loading = state is RepoOpState.Loading,
                            onUrlChanged = viewModel::onPublishUrlChanged,
                            onTokenChanged = viewModel::onPublishTokenChanged,
                            onPublish = viewModel::publishToGithub,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirmDelete?.let { slug ->
        val name = summaries.firstOrNull { it.slug == slug }?.name ?: slug
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir jogo?") },
            text = { Text("«$name» será removido do repositório local e do catálogo.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGame(slug, name)
                        confirmDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

private fun RepoSection.title(): String = when (this) {
    RepoSection.HOME -> "Criar fonte de dados"
    RepoSection.CONTINUE -> "Continuar de um repositório"
    RepoSection.CREATE_LOCAL -> "Criar repositório local"
    RepoSection.MANAGE_GAMES -> "Gerenciar jogos"
    RepoSection.PUBLISH -> "Publicar no GitHub"
}

@Composable
private fun OpBanner(state: RepoOpState, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = state is RepoOpState.Success || state is RepoOpState.Error,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        when (state) {
            is RepoOpState.Success -> InfoBanner(
                message = state.message,
                isError = false,
                leadingIcon = Icons.Rounded.CloudUpload,
                onDismiss = onDismiss,
            )
            is RepoOpState.Error -> InfoBanner(
                message = state.message,
                isError = true,
                onDismiss = onDismiss,
            )
            else -> Unit
        }
    }
}

@Composable
private fun RepoHome(
    repoConfig: com.recomp.gameshub.domain.model.RepoConfig?,
    gameCount: Int,
    onContinue: () -> Unit,
    onCreateLocal: () -> Unit,
    onManageGames: () -> Unit,
    onPublish: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Fonte de dados ativa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = repoConfig?.displayName ?: "Nenhuma configurada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                if (gameCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$gameCount jogo(s) no catálogo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }

        CategoryCard(
            icon = Icons.Rounded.CloudDownload,
            title = "Continuar de um repositório",
            message = "Use o repositório oficial do app, informe a URL de um repositório compatível ou importe localmente.",
            onClick = onContinue,
        )
        CategoryCard(
            icon = Icons.Rounded.Folder,
            title = "Criar repositório local",
            message = "Cria uma estrutura de repositório na pasta do app para gerenciar seus próprios dados.",
            onClick = onCreateLocal,
        )
        CategoryCard(
            icon = Icons.Rounded.Storage,
            title = "Gerenciar jogos",
            message = "Adicione, edite ou exclua jogos — incluindo os que vieram do repositório ativo.",
            onClick = onManageGames,
        )
        CategoryCard(
            icon = Icons.Rounded.Upload,
            title = "Publicar no GitHub",
            message = "Envie o repositório local para o seu repositório informando a URL e o token de acesso.",
            onClick = onPublish,
        )
    }
}

@Composable
private fun CategoryCard(
    icon: ImageVector,
    title: String,
    message: String,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContinueRepoSection(
    url: String,
    onUrlChanged: (String) -> Unit,
    loading: Boolean,
    onUseDefault: () -> Unit,
    onUseUrl: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Escolha de onde continuar. Os dados do repositório serão baixados e você poderá editá-los depois.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onUseDefault,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Usar repositório oficial do app")
        }
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text("URL de repositório de terceiros") },
            placeholder = { Text("https://github.com/usuario/repo") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onUseUrl,
            enabled = url.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Baixar repositório")
        }
        OutlinedButton(
            onClick = onOpenSetup,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importar de pasta local (setup)")
        }
    }
}

@Composable
private fun CreateLocalSection(
    loading: Boolean,
    onCreate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Cria uma estrutura de repositório (repo.json + index.json + games/) na pasta do app " +
                "e a define como fonte de dados ativa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onCreate,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Criando…" else "Criar repositório local")
        }
    }
}

@Composable
private fun ManageGamesSection(
    games: List<com.recomp.gameshub.domain.model.GameSummary>,
    editingSlug: String?,
    editingDraft: com.recomp.gameshub.data.repository.LocalGameDraft?,
    loading: Boolean,
    onEdit: (String) -> Unit,
    onSaveEdit: (String, com.recomp.gameshub.data.repository.LocalGameDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onAdd: (com.recomp.gameshub.data.repository.LocalGameDraft) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    if (editingSlug != null && editingDraft != null) {
        GameForm(
            title = "Editar jogo",
            initial = editingDraft,
            loading = loading,
            onSave = { draft -> onSaveEdit(editingSlug, draft) },
            onCancel = onCancelEdit,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var adding by remember { mutableStateOf(false) }

        Button(
            onClick = { adding = true },
            enabled = !loading && !adding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adicionar jogo")
        }

        if (adding) {
            GameForm(
                title = "Adicionar jogo",
                initial = LocalGameDraft(slug = "", name = ""),
                loading = loading,
                onSave = { draft ->
                    onAdd(draft)
                    adding = false
                },
                onCancel = { adding = false },
            )
            return@Column
        }

        if (games.isEmpty()) {
            Text(
                text = "Nenhum jogo no catálogo. Use o repositório oficial ou adicione um jogo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            SectionHeader("Catálogo (${games.size})")
            games.forEach { game ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(game.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = game.slug,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onEdit(game.slug) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar",
                                 tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDeleteRequest(game.slug) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Excluir",
                                 tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameForm(
    title: String,
    initial: com.recomp.gameshub.data.repository.LocalGameDraft,
    loading: Boolean,
    onSave: (com.recomp.gameshub.data.repository.LocalGameDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var slug by remember { mutableStateOf(initial.slug) }
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var platform by remember { mutableStateOf(initial.originalPlatform.orEmpty()) }
    var version by remember { mutableStateOf(initial.version.orEmpty()) }
    var author by remember { mutableStateOf(initial.author.orEmpty()) }
    var sourceRepo by remember { mutableStateOf(initial.sourceRepo.orEmpty()) }
    var downloadUrl by remember { mutableStateOf(initial.downloadUrl.orEmpty()) }
    var size by remember { mutableStateOf(initial.fileSizeBytes.toString()) }
    var tags by remember { mutableStateOf(initial.tags.joinToString(", ")) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(platform, { platform = it }, label = { Text("Plataforma original") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(version, { version = it }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sourceRepo, { sourceRepo = it }, label = { Text("Repositório do projeto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(downloadUrl, { downloadUrl = it }, label = { Text("URL do APK") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(size, { size = it }, label = { Text("Tamanho (bytes)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (vírgulas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        onSave(
                            LocalGameDraft(
                                slug = slug,
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
                    enabled = name.isNotBlank() && !loading,
                    modifier = Modifier.weight(1f),
                ) { Text(if (loading) "Salvando…" else "Salvar") }
            }
        }
    }
}

@Composable
private fun PublishSection(
    url: String,
    token: String,
    loading: Boolean,
    onUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onPublish: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Crie um repositório no GitHub, informe a URL e o seu token de acesso. " +
                "Os dados do repositório local (repo.json, index.json e jogos) serão enviados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text("URL do repositório") },
            placeholder = { Text("https://github.com/usuario/repo") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChanged,
            label = { Text("Token de acesso (GitHub)") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onPublish,
            enabled = url.isNotBlank() && token.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Publicando…" else "Publicar no GitHub")
        }
    }
}
