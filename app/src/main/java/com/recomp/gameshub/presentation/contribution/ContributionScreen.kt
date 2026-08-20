package com.recomp.gameshub.presentation.contribution

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.SectionHeader
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.AuthState
import com.recomp.gameshub.domain.model.SubmissionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionRoute(
    onBack: () -> Unit,
    onOpenAdminReview: () -> Unit,
) {
    val viewModel: ContributionViewModel = appViewModel {
        ContributionViewModel(it.contributionRepository, it.authRepository)
    }
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val submissions by viewModel.submissions.collectAsStateWithLifecycle()
    val isLoadingSubmissions by viewModel.isLoadingSubmissions.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val formVisible by viewModel.formVisible.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Contribuir",
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
            val user = (authState as? AuthState.SignedIn)?.user

            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Ajude a comunidade",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Encontrou uma recompilação que ainda não está no catálogo? " +
                                "Envie os dados do jogo. Nossa equipe revisa e publica para todos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (user == null) {
                item {
                    SectionHeader("Entrar ou criar conta", Modifier.padding(top = 8.dp))
                }
                item {
                    AuthCard(
                        isAuthenticating = isAuthenticating,
                        error = authError,
                        onSignIn = viewModel::signIn,
                        onSignUp = viewModel::signUp,
                        onResetPassword = viewModel::resetPassword,
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conectado como", style = MaterialTheme.typography.labelMedium)
                            Text(
                                user.email ?: user.id,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            if (isAdmin) {
                                Text(
                                    "Administrador",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        OutlinedButton(onClick = viewModel::signOut) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Sair")
                        }
                    }
                }

                if (isAdmin) {
                    item {
                        Button(
                            onClick = onOpenAdminReview,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(Icons.Rounded.Gamepad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Revisar contribuições pendentes")
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.toggleForm(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Enviar um jogo")
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }

                successMessage?.let { msg ->
                    item {
                        InfoBanner(msg, isError = false)
                    }
                }
                submitError?.let { msg ->
                    item {
                        InfoBanner(msg, isError = true)
                    }
                }

                if (formVisible) {
                    item {
                        GameSubmissionForm(
                            isSubmitting = isSubmitting,
                            onSubmit = viewModel::submitForm,
                            onCancel = { viewModel.toggleForm(false) },
                        )
                    }
                }

                item {
                    SectionHeader("Minhas contribuições", Modifier.padding(top = 12.dp))
                }
                if (isLoadingSubmissions) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                } else if (submissions.isEmpty()) {
                    item {
                        EmptyStateBox(
                            title = "Nada por aqui ainda",
                            message = "Quando você enviar jogos, eles vão aparecer aqui com o status da revisão.",
                            icon = Icons.Rounded.Gamepad,
                        )
                    }
                } else {
                    submissions.forEach { submission ->
                        item(key = submission.slug) {
                            SubmissionCard(submission)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SubmissionCard(submission: com.recomp.gameshub.domain.model.GameSubmission) {
    val status = when (submission.status) {
        "approved" -> SubmissionStatus.APPROVED
        "rejected" -> SubmissionStatus.REJECTED
        else -> SubmissionStatus.PENDING
    }
    val statusColor = when (status) {
        SubmissionStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        SubmissionStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
        SubmissionStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onStatusColor = when (status) {
        SubmissionStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
        SubmissionStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
        SubmissionStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    submission.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(status.label, style = MaterialTheme.typography.labelMedium, color = onStatusColor)
            }
            textIf("v${submission.version}", submission.version)
            textIf(submission.slug, submission.slug)
            submission.reviewReason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Motivo da rejeição: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.textIf(text: String, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthCard(
    isAuthenticating: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AuthMode.SIGN_IN) }
    var email by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var password by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var validationError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { mode = AuthMode.SIGN_IN; validationError = null }) {
                    Text("Entrar", fontWeight = if (mode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Normal)
                }
                TextButton(onClick = { mode = AuthMode.SIGN_UP; validationError = null }) {
                    Text("Criar conta", fontWeight = if (mode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Normal)
                }
                TextButton(onClick = { mode = AuthMode.RESET; validationError = null }) {
                    Text("Esqueci a senha", fontWeight = if (mode == AuthMode.RESET) FontWeight.Bold else FontWeight.Normal)
                }
            }

            androidx.compose.material3.OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == AuthMode.SIGN_IN || mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirmar senha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            (validationError ?: error)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    when (mode) {
                        AuthMode.SIGN_IN -> onSignIn(email.trim(), password)
                        AuthMode.SIGN_UP ->
                            if (password != confirm) {
                                validationError = "As senhas não coincidem."
                            } else {
                                onSignUp(email.trim(), password)
                            }
                        AuthMode.RESET -> onResetPassword(email.trim())
                    }
                },
                enabled = !isAuthenticating &&
                    email.isNotBlank() &&
                    (mode == AuthMode.RESET || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when (mode) {
                            AuthMode.SIGN_IN -> "Entrar"
                            AuthMode.SIGN_UP -> "Criar conta"
                            AuthMode.RESET -> "Enviar link de redefinição"
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Sua conta só é usada para publicar e acompanhar contribuições. Nada de rastreamento.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

@Composable
private fun GameSubmissionForm(
    isSubmitting: Boolean,
    onSubmit: (
        name: String,
        status: String,
        version: String?,
        description: String,
        originalPlatform: String?,
        author: String?,
        sourceRepo: String?,
        tags: List<String>,
        coverUrl: String?,
        bannerUrl: String?,
        screenshots: List<String>,
    ) -> Unit,
    onCancel: () -> Unit,
) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("stable") }
    var version by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var description by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var platform by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var author by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var sourceRepo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var tagsText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var coverUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var bannerUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var screenshotsText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var aiJson by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var aiError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val aiPrompt = """Você é um agente de pesquisa rigoroso. Acesse e investigue este repositório do GitHub: $sourceRepo
Obtenha automaticamente, usando o conteúdo do repositório e as releases, os dados da recompilação Android: nome do jogo (pode ser o nome da recomp), descrição, plataforma original, autor da recompilação, URL exata do repositório, tags, URL da capa, URL do banner e URLs diretas de screenshots (se existirem). Não invente informações: use null ou [] quando não encontrar. Retorne SOMENTE um JSON válido, sem markdown, exatamente neste formato:
{"name":"","description":"","platform":"","author":"","repository":"$sourceRepo","tags":[],"cover_url":null,"banner_url":null,"screenshots":[]}""".trimIndent()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Enviar jogo", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Preencha com o máximo de detalhes. URLs de imagem devem ser diretas (https).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            FormField(
                value = name,
                onValueChange = { name = it },
                label = "Nome do jogo *",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = description,
                onValueChange = { description = it },
                label = "Descrição",
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = version,
                onValueChange = { version = it },
                label = "Versão (opcional)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = platform,
                onValueChange = { platform = it },
                label = "Plataforma original (ex.: Nintendo 64)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = author,
                onValueChange = { author = it },
                label = "Autor da recompilação (opcional)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = sourceRepo,
                onValueChange = { sourceRepo = it },
                label = "Repositório GitHub (https://github.com/…) *",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Text("O APK, a versão, o autor e o tamanho serão obtidos automaticamente da release mais recente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FormField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = "Tags separadas por vírgula (ex.: n64, zelda)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = "URL da capa 3:4 (https://…)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = bannerUrl,
                onValueChange = { bannerUrl = it },
                label = "URL do banner 16:9 (https://…)",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            FormField(value = screenshotsText, onValueChange = { screenshotsText = it }, label = "URLs de screenshots (opcional, separadas por vírgula)", minLines = 2)

            Spacer(Modifier.height(18.dp))
            Text("Prompt de IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Copie o prompt, cole na sua IA e depois cole o JSON retornado para preencher os campos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(aiPrompt)); android.widget.Toast.makeText(context, "Prompt copiado", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) { Text("Copiar prompt de IA") }
            FormField(value = aiJson, onValueChange = { aiJson = it; aiError = null }, label = "Cole aqui o JSON retornado pela IA", minLines = 4)
            aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                runCatching {
                    val obj = Json.parseToJsonElement(aiJson).jsonObject
                    name = obj.stringValue("name")
                    description = obj.stringValue("description")
                    platform = obj.stringValue("platform")
                    author = obj.stringValue("author")
                    sourceRepo = obj.stringValue("repository").ifBlank { sourceRepo }
                    tagsText = obj.arrayValue("tags").joinToString(", ")
                    coverUrl = obj.nullableValue("cover_url")
                    bannerUrl = obj.nullableValue("banner_url")
                    screenshotsText = obj.arrayValue("screenshots").joinToString(", ")
                }.onFailure { aiError = "JSON inválido: ${it.message ?: "verifique o formato"}" }
            }) { Text("Preencher campos com JSON") }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        onSubmit(
                            name,
                            status,
                            version.ifBlank { null },
                            description,
                            platform.ifBlank { null },
                            author.ifBlank { null },
                            sourceRepo.ifBlank { null },
                            tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            coverUrl.ifBlank { null },
                            bannerUrl.ifBlank { null },
                            screenshotsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        )
                    },
                    enabled = !isSubmitting && name.isNotBlank() && sourceRepo.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Enviar para revisão")
                    }
                }
            }
        }
    }
}

private fun JsonObject.stringValue(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonObject.nullableValue(key: String): String = stringValue(key)
private fun JsonObject.arrayValue(key: String): List<String> = this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
