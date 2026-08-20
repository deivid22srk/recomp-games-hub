package com.recomp.gameshub.presentation.contribution

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.ErrorStateBox
import com.recomp.gameshub.core.designsystem.GameCoverImage
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.ShimmerBox
import com.recomp.gameshub.core.designsystem.SubmissionStatusChip
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.AuthState
import com.recomp.gameshub.domain.model.GameSubmission
import com.recomp.gameshub.domain.model.SubmissionStatus
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val formVisible by viewModel.formVisible.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(ContributionSection.HOME) }
    var editing by remember { mutableStateOf<GameSubmission?>(null) }
    var deleting by remember { mutableStateOf<GameSubmission?>(null) }

    val user = (authState as? AuthState.SignedIn)?.user

    val bannerMessage = successMessage ?: if (section == ContributionSection.FORM) null else submitError
    val isBannerError = submitError != null && section != ContributionSection.FORM

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(5_000)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = when (section) {
                    ContributionSection.HOME -> "Contribuir"
                    ContributionSection.FORM -> "Enviar jogo"
                    ContributionSection.SUBMISSIONS -> "Minhas contribuições"
                },
                onBack = {
                    if (section == ContributionSection.HOME) onBack()
                    else { section = ContributionSection.HOME; viewModel.toggleForm(false) }
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
                ContributionHero(
                    signedIn = user != null,
                    submissionCount = if (user != null) submissions.size else 0,
                )
            }

            if (user == null) {
                item {
                    Text(
                        "Entrar ou criar conta",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
                    SignedInHeader(
                        email = user.email ?: user.id,
                        isAdmin = isAdmin,
                        onSignOut = viewModel::signOut,
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = bannerMessage != null,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        bannerMessage?.let { msg ->
                            InfoBanner(msg, isError = isBannerError)
                        }
                    }
                }

                item {
                    AnimatedContent(
                        targetState = section,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val direction = if (forward) 1 else -1
                            (slideInHorizontally { it * direction } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it * direction } + fadeOut())
                        },
                        label = "contributionSection",
                    ) { current ->
                        when (current) {
                            ContributionSection.HOME -> HomeSection(
                                onStartSubmission = {
                                    editing = null
                                    section = ContributionSection.FORM
                                    viewModel.toggleForm(true)
                                },
                                onOpenSubmissions = { section = ContributionSection.SUBMISSIONS },
                                onOpenAdminReview = onOpenAdminReview,
                                isAdmin = isAdmin,
                            )
                            ContributionSection.FORM -> GameSubmissionForm(
                                initial = editing,
                                isSubmitting = isSubmitting,
                                submitError = submitError,
                                onSubmit = { name, status, version, description, platform, author, repository, tags, cover, banner, screenshots ->
                                    val original = editing
                                    if (original != null) {
                                        viewModel.updateForm(original, name, status, version, description, platform, author, repository, tags, cover, banner, screenshots)
                                    } else {
                                        viewModel.submitForm(name, status, version, description, platform, author, repository, tags, cover, banner, screenshots)
                                    }
                                },
                                onCancel = {
                                    editing = null
                                    viewModel.toggleForm(false)
                                },
                            )
                            ContributionSection.SUBMISSIONS -> SubmissionsSection(
                                isLoading = isLoadingSubmissions,
                                loadError = loadError,
                                submissions = submissions,
                                onEdit = {
                                    editing = it
                                    section = ContributionSection.FORM
                                    viewModel.toggleForm(true)
                                },
                                onDelete = { deleting = it },
                                onCreateSubmission = {
                                    editing = null
                                    section = ContributionSection.FORM
                                    viewModel.toggleForm(true)
                                },
                                onRetry = viewModel::retrySubmissions,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    LaunchedEffect(formVisible) {
        if (!formVisible && section == ContributionSection.FORM) section = ContributionSection.HOME
    }

    LaunchedEffect(user) {
        if (user == null) section = ContributionSection.HOME
    }

    deleting?.let { submission ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir contribuição?") },
            text = { Text("A contribuição «${submission.name}» será removida permanentemente.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(submission.slug)
                        deleting = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

private enum class ContributionSection { HOME, FORM, SUBMISSIONS }

@Composable
private fun ContributionHero(signedIn: Boolean, submissionCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ajude a comunidade",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (signedIn) {
                        "Encontrou uma recompilação fora do catálogo? Envie os dados. " +
                            "Nossa equipe revisa e publica para todos."
                    } else {
                        "Envie recompilações que ainda não estão no catálogo. " +
                            "Nossa equipe revisa e publica para todos."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                )
                if (signedIn) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ) {
                        Text(
                            when (submissionCount) {
                                0 -> "Nenhuma contribuição enviada ainda"
                                1 -> "1 contribuição enviada"
                                else -> "$submissionCount contribuições enviadas"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedInHeader(
    email: String,
    isAdmin: Boolean,
    onSignOut: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = email.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Conectado como",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    email,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Administrador",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onSignOut) {
                Icon(
                    Icons.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Sair")
            }
        }
    }
}

@Composable
private fun HomeSection(
    onStartSubmission: () -> Unit,
    onOpenSubmissions: () -> Unit,
    onOpenAdminReview: () -> Unit,
    isAdmin: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ContributionActionCard(
            icon = Icons.Rounded.Add,
            title = "Enviar um jogo",
            message = "Cadastre uma recompilação usando o repositório e os dados da release.",
            actionLabel = "Começar",
            onClick = onStartSubmission,
        )
        ContributionActionCard(
            icon = Icons.Rounded.ListAlt,
            title = "Minhas contribuições",
            message = "Acompanhe status, edite dados ou exclua uma contribuição.",
            actionLabel = "Ver contribuições",
            onClick = onOpenSubmissions,
        )
        if (isAdmin) {
            ContributionActionCard(
                icon = Icons.Rounded.Gamepad,
                title = "Área administrativa",
                message = "Revise, edite e gerencie todas as contribuições da comunidade.",
                actionLabel = "Abrir administração",
                onClick = onOpenAdminReview,
            )
        }
    }
}

@Composable
private fun ContributionActionCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .clickable(
                    onClickLabel = actionLabel,
                    onClick = onClick,
                    role = Role.Button,
                ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(5.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmissionsSection(
    isLoading: Boolean,
    loadError: String?,
    submissions: List<GameSubmission>,
    onEdit: (GameSubmission) -> Unit,
    onDelete: (GameSubmission) -> Unit,
    onCreateSubmission: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        submissions.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                submissions.forEach { submission ->
                    SubmissionCard(
                        submission = submission,
                        onEdit = { onEdit(submission) },
                        onDelete = { onDelete(submission) },
                    )
                }
            }
        }
        isLoading -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { SubmissionSkeleton() }
            }
        }
        loadError != null -> {
            ErrorStateBox(message = loadError, onRetry = onRetry)
        }
        else -> {
            EmptyStateBox(
                title = "Nada por aqui ainda",
                message = "Quando você enviar jogos, eles aparecerão aqui com o status da revisão.",
                icon = Icons.Rounded.Gamepad,
                actionLabel = "Enviar um jogo",
                onAction = onCreateSubmission,
            )
        }
    }
}

@Composable
private fun SubmissionSkeleton() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.55f).height(16.dp),
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(10.dp))
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.35f).height(12.dp),
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.height(10.dp))
                ShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.75f).height(40.dp),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }
    }
}

@Composable
private fun SubmissionCard(
    submission: GameSubmission,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = SubmissionStatus.fromRaw(submission.status)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GameCoverImage(
                    url = submission.coverUrl,
                    contentDescription = submission.name,
                    fallbackKey = submission.slug.hashCode().toLong(),
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        submission.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    val meta = buildList {
                        submission.version?.takeIf { it.isNotBlank() }?.let { add("v$it") }
                        submission.originalPlatform?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SubmissionStatusChip(status)
            submission.reviewReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Motivo da rejeição: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Editar")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Excluir")
                }
            }
        }
    }
}

private enum class AuthMode(val label: String) {
    SIGN_IN("Entrar"),
    SIGN_UP("Criar conta"),
    RESET("Recuperar"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthCard(
    isAuthenticating: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    val modes = AuthMode.entries
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = mode == candidate,
                        onClick = {
                            mode = candidate
                            validationError = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(candidate.label, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                when (mode) {
                    AuthMode.SIGN_IN -> "Entre para enviar e acompanhar contribuições."
                    AuthMode.SIGN_UP -> "Crie sua conta para começar a contribuir."
                    AuthMode.RESET -> "Enviaremos um link de redefinição para o seu e-mail."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == AuthMode.SIGN_IN || mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(10.dp))
                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Senha",
                    visible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    canSubmit = !isAuthenticating && email.isNotBlank() && password.isNotBlank(),
                    onSubmit = {
                        if (mode == AuthMode.SIGN_IN) onSignIn(email.trim(), password)
                    },
                    imeAction = if (mode == AuthMode.SIGN_IN) ImeAction.Done else ImeAction.Next,
                )
            }
            if (mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(10.dp))
                AuthPasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirmar senha",
                    visible = confirmVisible,
                    onToggleVisibility = { confirmVisible = !confirmVisible },
                    canSubmit = !isAuthenticating &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        confirm.isNotBlank(),
                    onSubmit = {
                        if (password != confirm) {
                            validationError = "As senhas não coincidem."
                        } else {
                            onSignUp(email.trim(), password)
                        }
                    },
                    imeAction = ImeAction.Done,
                )
            }

            (validationError ?: error)?.let {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    when (mode) {
                        AuthMode.SIGN_IN -> onSignIn(email.trim(), password)
                        AuthMode.SIGN_UP -> {
                            if (password != confirm) {
                                validationError = "As senhas não coincidem."
                            } else {
                                onSignUp(email.trim(), password)
                            }
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

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (canSubmit) onSubmit()
        }),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Ocultar senha" else "Mostrar senha",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val DevStatusOptions = listOf(
    "stable" to "Lançado",
    "beta" to "Beta",
    "alpha" to "Alpha",
    "in_development" to "Em desenvolvimento",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameSubmissionForm(
    initial: GameSubmission? = null,
    isSubmitting: Boolean,
    submitError: String? = null,
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
    var name by remember(initial?.submissionId) { mutableStateOf(initial?.name.orEmpty()) }
    var status by remember(initial?.submissionId) { mutableStateOf(initial?.devStatus ?: "stable") }
    var version by remember(initial?.submissionId) { mutableStateOf(initial?.version.orEmpty()) }
    var description by remember(initial?.submissionId) { mutableStateOf(initial?.description.orEmpty()) }
    var platform by remember(initial?.submissionId) { mutableStateOf(initial?.originalPlatform.orEmpty()) }
    var author by remember(initial?.submissionId) { mutableStateOf(initial?.author.orEmpty()) }
    var sourceRepo by remember(initial?.submissionId) { mutableStateOf(initial?.sourceRepo.orEmpty()) }
    var tagsText by remember(initial?.submissionId) { mutableStateOf(initial?.tags?.joinToString(", ").orEmpty()) }
    var coverUrl by remember(initial?.submissionId) { mutableStateOf(initial?.coverUrl.orEmpty()) }
    var bannerUrl by remember(initial?.submissionId) { mutableStateOf(initial?.bannerUrl.orEmpty()) }
    var screenshotsText by remember(initial?.submissionId) { mutableStateOf(initial?.screenshots?.joinToString(", ").orEmpty()) }
    var nameError by remember(initial?.submissionId) { mutableStateOf<String?>(null) }
    var repoError by remember(initial?.submissionId) { mutableStateOf<String?>(null) }
    var aiJson by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiExpanded by remember { mutableStateOf(false) }
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
            Text(
                if (initial == null) "Enviar jogo" else "Editar contribuição",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Preencha com o máximo de detalhes. URLs de imagem devem ser diretas (https).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            FormSectionTitle("Informações do jogo")
            Spacer(Modifier.height(8.dp))
            FormField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = "Nome do jogo *",
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Estágio de desenvolvimento",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val selectedDevStatus = when (status) {
                "released", "available", "complete" -> "stable"
                else -> status
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevStatusOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedDevStatus == value,
                        onClick = { status = value },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FormField(
                value = version,
                onValueChange = { version = it },
                label = "Versão (opcional)",
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
                onValueChange = { sourceRepo = it; repoError = null },
                label = "Repositório GitHub (https://github.com/…) *",
                singleLine = true,
                keyboardType = KeyboardType.Uri,
                isError = repoError != null,
                supportingText = repoError,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "O APK, a versão, o autor e o tamanho serão obtidos automaticamente da release mais recente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            FormField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = "Tags separadas por vírgula (ex.: n64, zelda)",
                singleLine = true,
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))
            FormSectionTitle("Mídia e imagens")
            Spacer(Modifier.height(8.dp))
            FormField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = "URL da capa 3:4 (https://…)",
                singleLine = true,
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = bannerUrl,
                onValueChange = { bannerUrl = it },
                label = "URL do banner 16:9 (https://…)",
                singleLine = true,
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(10.dp))
            FormField(
                value = screenshotsText,
                onValueChange = { screenshotsText = it },
                label = "URLs de screenshots (opcional, separadas por vírgula)",
                minLines = 2,
                keyboardType = KeyboardType.Uri,
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = { aiExpanded = !aiExpanded }, role = Role.Button)
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (aiExpanded) "Expandido" else "Recolhido"
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Assistente de IA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Preencha automaticamente com os dados do repositório",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (aiExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = aiExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(aiPrompt))
                            android.widget.Toast.makeText(context, "Prompt copiado", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copiar prompt de IA") }
                    Spacer(Modifier.height(10.dp))
                    FormField(
                        value = aiJson,
                        onValueChange = { aiJson = it; aiError = null },
                        label = "Cole aqui o JSON retornado pela IA",
                        minLines = 4,
                        isError = aiError != null,
                        supportingText = aiError,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
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
                                nameError = null
                                repoError = null
                            }.onFailure {
                                aiError = "JSON inválido: ${it.message ?: "verifique o formato"}"
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Preencher campos com JSON") }
                }
            }

            Spacer(Modifier.height(16.dp))
            submitError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        nameError = if (name.isBlank()) "Informe o nome do jogo." else null
                        repoError = if (sourceRepo.isBlank()) "Informe o repositório GitHub." else null
                        if (nameError == null && repoError == null) {
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
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (initial == null) "Enviar para revisão" else "Salvar alterações")
                    }
                }
            }
        }
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
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
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}