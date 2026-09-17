package com.recomp.gameshub.presentation.repo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    var pendingReplaceAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val hasActiveSource = repoConfig != null
    fun confirmReplaceSource(hasActive: Boolean, action: () -> Unit) {
        if (hasActive) pendingReplaceAction = action else action()
    }

    // Navega de volta ao HOME quando uma operação de troca de fonte termina
    // com sucesso, para não deixar o usuário preso na sub-seção.
    val sourceChangingSections = remember(section) {
        section == RepoSection.CONTINUE || section == RepoSection.CREATE_LOCAL
    }
    LaunchedEffect(state) {
        if (state is RepoOpState.Success) {
            if (sourceChangingSections) {
                kotlinx.coroutines.delay(1_200)
                viewModel.dismissState()
                viewModel.navigateTo(RepoSection.HOME)
            } else {
                kotlinx.coroutines.delay(4_000)
                viewModel.dismissState()
            }
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
                .padding(innerPadding)
                // Teclado não cobre mais os campos inferiores do formulário.
                .imePadding(),
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
                            onUseDefault = { confirmReplaceSource(hasActiveSource, viewModel::continueWithDefault) },
                            onUseUrl = {
                                confirmReplaceSource(hasActiveSource) {
                                    viewModel.continueWithUrl(viewModel.continueUrl.value)
                                }
                            },
                            onOpenSetup = onOpenSetup,
                        )
                        RepoSection.CREATE_LOCAL -> CreateLocalSection(
                            loading = state is RepoOpState.Loading,
                            onCreate = { confirmReplaceSource(hasActiveSource, viewModel::createLocalRepo) },
                        )
                        RepoSection.MANAGE_GAMES -> ManageGamesSection(
                            games = summaries,
                            editingSlug = viewModel.editingSlug.collectAsStateWithLifecycle().value,
                            editingDraft = viewModel.editingDraft.collectAsStateWithLifecycle().value,
                            addingForm = viewModel.addingForm.collectAsStateWithLifecycle().value,
                            loading = state is RepoOpState.Loading,
                            onEdit = viewModel::startEdit,
                            onSaveEdit = viewModel::saveEdit,
                            onCancelEdit = viewModel::cancelEdit,
                            onAdd = viewModel::addGame,
                            onStartAdd = viewModel::startAdd,
                            onCancelAdd = viewModel::cancelAdd,
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

    BackHandler {
        val editing = viewModel.editingSlug.value != null
        when {
            editing -> viewModel.cancelEdit()
            section != RepoSection.HOME -> viewModel.navigateTo(RepoSection.HOME)
            else -> onBack()
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
    pendingReplaceAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingReplaceAction = null },
            title = { Text("Substituir fonte de dados?") },
            text = {
                Text("A fonte de dados atual será substituída. " +
                    "Os dados do catálogo serão baixados novamente da nova fonte.")
            },
            confirmButton = {
                Button(onClick = {
                    action()
                    pendingReplaceAction = null
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplaceAction = null }) { Text("Cancelar") }
            },
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
    val hasSource = repoConfig != null
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (gameCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralize("jogo no catálogo", "jogos no catálogo", gameCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        CategoryCard(
            icon = Icons.Rounded.CloudDownload,
            title = "Continuar de um repositório",
            message = "Escolha entre o catálogo oficial ou informe a URL de um repositório compatível.",
            onClick = onContinue,
        )
        CategoryCard(
            icon = Icons.Rounded.Folder,
            title = "Criar repositório local",
            message = "Cria uma estrutura de repositório na pasta de dados do app para gerenciar seus próprios dados.",
            onClick = onCreateLocal,
        )
        CategoryCard(
            icon = Icons.Rounded.Storage,
            title = "Gerenciar jogos",
            message = "Adicione, edite ou exclua jogos do repositório local.",
            onClick = onManageGames,
            enabled = hasSource,
        )
        CategoryCard(
            icon = Icons.Rounded.Upload,
            title = "Publicar no GitHub",
            message = if (hasSource) {
                "Envie o repositório local para o seu repositório informando a URL e o token de acesso."
            } else {
                "Configure uma fonte de dados primeiro para liberar a publicação."
            },
            onClick = onPublish,
            enabled = hasSource,
        )
    }
}

private fun pluralize(singular: String, plural: String, count: Int): String =
    if (count == 1) "$count $singular" else "$count $plural"

@Composable
private fun CategoryCard(
    icon: ImageVector,
    title: String,
    message: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.large
    val container = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .let { m ->
                    if (enabled) {
                        m.semantics { role = Role.Button }
                            .clickable(onClick = onClick)
                    } else {
                        m
                    }
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = content,
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
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Baixando…" else "Usar o catálogo oficial do Recomp Hub")
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
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Baixando…" else "Baixar e ativar este repositório")
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
    addingForm: Boolean,
    loading: Boolean,
    onEdit: (String) -> Unit,
    onSaveEdit: (String, com.recomp.gameshub.data.repository.LocalGameDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onAdd: (com.recomp.gameshub.data.repository.LocalGameDraft) -> Unit,
    onStartAdd: () -> Unit,
    onCancelAdd: () -> Unit,
    onDeleteRequest: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    if (editingSlug != null && editingDraft != null) {
        // key() destrói o estado do formulário ao trocar de jogo: sem isso,
        // rememberSaveable restaurava os dados do jogo anterior no formulário.
        androidx.compose.runtime.key(editingSlug) {
            GameForm(
                title = "Editar jogo",
                initial = editingDraft,
                loading = loading,
                onSave = { draft -> onSaveEdit(editingSlug, draft) },
                onCancel = onCancelEdit,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onStartAdd,
            enabled = !loading && !addingForm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Adicionar jogo")
        }

        if (addingForm) {
            androidx.compose.runtime.key("add-game") {
                GameForm(
                    title = "Adicionar jogo",
                    initial = LocalGameDraft(slug = "", name = ""),
                    loading = loading,
                    onSave = { draft -> onAdd(draft) },
                    onCancel = onCancelAdd,
                )
            }
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
    var slug by rememberSaveable { mutableStateOf(initial.slug) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var platform by rememberSaveable { mutableStateOf(initial.originalPlatform.orEmpty()) }
    var version by rememberSaveable { mutableStateOf(initial.version.orEmpty()) }
    var author by rememberSaveable { mutableStateOf(initial.author.orEmpty()) }
    var sourceRepo by rememberSaveable { mutableStateOf(initial.sourceRepo.orEmpty()) }
    var downloadUrl by rememberSaveable { mutableStateOf(initial.downloadUrl.orEmpty()) }
    var size by rememberSaveable { mutableStateOf(initial.fileSizeBytes.toString()) }
    var tags by rememberSaveable { mutableStateOf(initial.tags.joinToString(", ")) }
    var coverUrl by rememberSaveable { mutableStateOf(initial.coverUrl.orEmpty()) }
    var bannerUrl by rememberSaveable { mutableStateOf(initial.bannerUrl.orEmpty()) }
    var screenshots by rememberSaveable { mutableStateOf(initial.screenshots.joinToString("\n")) }

    // IA fill
    var aiJson by rememberSaveable { mutableStateOf("") }
    var aiError by rememberSaveable { mutableStateOf<String?>(null) }
    var aiNotice by rememberSaveable { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun copyPrompt() {
        val prompt = buildAiPrompt(sourceRepo.trim())
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Prompt IA", prompt))
        aiNotice = "Prompt copiado! Cole em uma IA e depois cole o JSON da resposta abaixo."
        aiError = null
    }

    fun applyAiJson() {
        aiError = null
        aiNotice = null
        parseAiFill(aiJson).fold(
            onSuccess = { fill ->
                name = fill.name ?: name
                description = fill.description ?: description
                platform = fill.platform ?: platform
                author = fill.author ?: author
                sourceRepo = fill.repository ?: sourceRepo
                tags = fill.tags?.joinToString(", ") ?: tags
                coverUrl = fill.coverUrl ?: coverUrl
                bannerUrl = fill.bannerUrl ?: bannerUrl
                screenshots = fill.screenshots?.joinToString("\n") ?: screenshots
                slug = slug.ifBlank { (fill.name ?: name).lowercase().trim()
                    .replace(Regex("[^a-z0-9]+"), "-").trim('-') }
                aiJson = ""
                aiNotice = "Campos preenchidos pela IA. Revise antes de salvar."
            },
            onFailure = { error -> aiError = error.message },
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // ── IA fill ──
            Text(
                text = "Preencher com IA",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = sourceRepo,
                onValueChange = { sourceRepo = it; aiError = null; aiNotice = null },
                label = { Text("URL do repositório da recompilação (RECOMP) *") },
                placeholder = { Text("https://github.com/usuario/recomp") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { copyPrompt() },
                enabled = sourceRepo.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (sourceRepo.isBlank()) "Informe a URL do repositório para liberar" else "Copiar prompt")
            }
            OutlinedTextField(
                value = aiJson,
                onValueChange = { aiJson = it; aiError = null; aiNotice = null },
                label = { Text("Cole aqui o JSON da resposta da IA") },
                minLines = 3,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { applyAiJson() },
                enabled = aiJson.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preencher campos com IA")
            }
            aiError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            aiNotice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()

            // ── Campos ──
            OutlinedTextField(name, { name = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(platform, { platform = it }, label = { Text("Plataforma original") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(version, { version = it }, label = { Text("Versão") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // O campo "Repositório do projeto" duplicado foi removido: ele
            // editava o mesmo estado do campo da seção IA acima, confundindo
            // o usuário ao digitar num e ver o outro mudar.
            OutlinedTextField(downloadUrl, { downloadUrl = it }, label = { Text("URL do APK") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(size, { size = it }, label = { Text("Tamanho (bytes)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (vírgulas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(coverUrl, { coverUrl = it }, label = { Text("URL da capa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bannerUrl, { bannerUrl = it }, label = { Text("URL do banner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(screenshots, { screenshots = it }, label = { Text("URLs de screenshots (uma por linha)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

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
                                coverUrl = coverUrl.trim().ifBlank { null },
                                bannerUrl = bannerUrl.trim().ifBlank { null },
                                screenshots = screenshots.split("\n").map { it.trim() }.filter { it.isNotBlank() },
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
