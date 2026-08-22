package com.recomp.gameshub.presentation.admin

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.designsystem.AppTopBar
import com.recomp.gameshub.core.designsystem.EmptyStateBox
import com.recomp.gameshub.core.designsystem.ErrorStateBox
import com.recomp.gameshub.core.designsystem.InfoBanner
import com.recomp.gameshub.core.designsystem.SubmissionStatusChip
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.GameSubmission
import com.recomp.gameshub.domain.model.SubmissionStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AdminReviewRoute(
    onBack: () -> Unit,
) {
    val viewModel: AdminReviewViewModel = appViewModel {
        AdminReviewViewModel(it.contributionRepository, it.authRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val principalStatus by viewModel.principalStatus.collectAsStateWithLifecycle()
    val promotionState by viewModel.promotionState.collectAsStateWithLifecycle()
    val actionStates by viewModel.actionStates.collectAsStateWithLifecycle()
    val appUpdate by viewModel.appUpdate.collectAsStateWithLifecycle()
    val appReleaseAction by viewModel.appReleaseAction.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(ModerationSection.HOME) }
    var rejectSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var editSubmission by remember { mutableStateOf<GameSubmission?>(null) }
    var deleteSubmission by remember { mutableStateOf<GameSubmission?>(null) }
    var confirmPromoteEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var removeReleaseId by rememberSaveable { mutableStateOf<String?>(null) }

    val isPrincipalAdmin = principalStatus == PrincipalAdminStatus.Granted

    val bannerMessage = uiState.error ?: uiState.successMessage
    val bannerIsError = uiState.error != null
    val errorSupersedesBanner =
        uiState.error != null && !uiState.isLoading && uiState.pending.isEmpty()
    val showBanner = bannerMessage != null && !errorSupersedesBanner

    LaunchedEffect(showBanner, bannerMessage) {
        if (showBanner) {
            delay(5_000)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(promotionState) {
        if (promotionState is AdminPromotionUiState.Result) {
            delay(5_000)
            viewModel.dismissPromotion()
        }
    }

    LaunchedEffect(appReleaseAction) {
        if (appReleaseAction is AppReleaseUiState.Result) {
            delay(6_000)
            viewModel.dismissAppReleaseResult()
        }
    }

    LaunchedEffect(isPrincipalAdmin, section) {
        if (isPrincipalAdmin && section == ModerationSection.APP_UPDATE &&
            appUpdate.current == null && !appUpdate.isLoading && appUpdate.loadError == null
        ) {
            viewModel.refreshAppRelease()
        }
    }

    BackHandler(enabled = section != ModerationSection.HOME) {
        section = ModerationSection.HOME
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = when (section) {
                    ModerationSection.HOME -> "Moderação"
                    ModerationSection.PENDING -> "Pendências"
                    ModerationSection.ALL_CONTRIBUTIONS -> "Todas as contribuições"
                    ModerationSection.PROMOTE_ADMIN -> "Promover ADM"
                    ModerationSection.APP_UPDATE -> "Atualização do app"
                },
                onBack = {
                    if (section == ModerationSection.HOME) onBack() else section = ModerationSection.HOME
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
            if (!uiState.isAdmin) {
                item {
                    RestrictedState(onRefresh = viewModel::refresh)
                }
                return@LazyColumn
            }

            item {
                ModerationHero(
                    isPrincipalAdmin = isPrincipalAdmin,
                    onRefresh = viewModel::refresh,
                )
            }

            item {
                AnimatedVisibility(
                    visible = showBanner,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    bannerMessage?.let { message ->
                        InfoBanner(
                            message = message,
                            isError = bannerIsError,
                            leadingIcon = if (bannerIsError) {
                                Icons.Rounded.ErrorOutline
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            onDismiss = viewModel::dismissMessages,
                        )
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
                    label = "moderationSection",
                ) { current ->
                    when (current) {
                        ModerationSection.HOME -> ModerationHomeSection(
                            pendingCount = uiState.pending.size,
                            totalCount = uiState.all.size,
                            isLoading = uiState.isLoading,
                            isPrincipalAdmin = isPrincipalAdmin,
                            onOpenPending = { section = ModerationSection.PENDING },
                            onOpenAll = { section = ModerationSection.ALL_CONTRIBUTIONS },
                            onOpenPromote = { section = ModerationSection.PROMOTE_ADMIN },
                            onOpenAppUpdate = { section = ModerationSection.APP_UPDATE },
                        )
                        ModerationSection.PENDING -> PendingSubmissionsSection(
                            uiState = uiState,
                            actionStates = actionStates,
                            onApprove = viewModel::approve,
                            onReject = { rejectSlug = it },
                            onEdit = { editSubmission = it },
                            onDelete = { deleteSubmission = it },
                            onRetry = viewModel::refresh,
                        )
                        ModerationSection.ALL_CONTRIBUTIONS -> AllContributionsSection(
                            uiState = uiState,
                            actionStates = actionStates,
                            onApprove = viewModel::approve,
                            onReject = { rejectSlug = it },
                            onEdit = { editSubmission = it },
                            onDelete = { deleteSubmission = it },
                            onRetry = viewModel::refresh,
                            onDismissError = viewModel::dismissAllError,
                        )
                        ModerationSection.PROMOTE_ADMIN -> PromoteAdminSection(
                            principalStatus = principalStatus,
                            promotionState = promotionState,
                            onPromoteClick = { email -> confirmPromoteEmail = email },
                            onDismissPromotion = viewModel::dismissPromotion,
                            onRetryStatus = viewModel::refreshPrincipalStatus,
                        )
                        ModerationSection.APP_UPDATE -> AppUpdateCard(
                            state = appUpdate,
                            actionState = appReleaseAction,
                            onPublishClick = { name, url, notes ->
                                viewModel.publishAppRelease(name, url, notes)
                            },
                            onRemoveClick = { releaseId -> removeReleaseId = releaseId },
                            onDismissResult = viewModel::dismissAppReleaseResult,
                            onRefresh = viewModel::refreshAppRelease,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirmPromoteEmail?.let { email ->
        PromoteConfirmDialog(
            email = email,
            onConfirm = {
                viewModel.promote(email)
                confirmPromoteEmail = null
            },
            onDismiss = { confirmPromoteEmail = null },
        )
    }
    rejectSlug?.let { slug ->
        val submission = uiState.pending.firstOrNull { it.slug == slug }
            ?: uiState.all.firstOrNull { it.slug == slug }
        RejectDialog(
            submissionName = submission?.name ?: slug,
            submissionSlug = slug,
            actionState = actionStates["reject:$slug"],
            onConfirm = { reason ->
                viewModel.reject(slug, submission?.name ?: slug, reason)
            },
            onDismiss = {
                viewModel.clearAction("reject:$slug")
                rejectSlug = null
            },
        )
    }
    editSubmission?.let { submission ->
        AdminEditDialog(
            submission = submission,
            actionState = actionStates["update:${submission.slug}"],
            onSave = { viewModel.update(submission.slug, it) },
            onDismiss = {
                viewModel.clearAction("update:${submission.slug}")
                editSubmission = null
            },
        )
    }
    deleteSubmission?.let { submission ->
        DeleteConfirmDialog(
            submissionName = submission.name,
            actionState = actionStates["delete:${submission.slug}"],
            onConfirm = { viewModel.delete(submission.slug) },
            onDismiss = {
                viewModel.clearAction("delete:${submission.slug}")
                deleteSubmission = null
            },
        )
    }
    removeReleaseId?.let { releaseId ->
        AlertDialog(
            onDismissRequest = { removeReleaseId = null },
            title = { Text("Remover publicação?") },
            text = { Text("O aviso de atualização deixa de ser exibido para os usuários.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeAppRelease(releaseId)
                        removeReleaseId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { removeReleaseId = null }) { Text("Cancelar") }
            },
        )
    }
}

private enum class ModerationSection {
    HOME,
    PENDING,
    ALL_CONTRIBUTIONS,
    PROMOTE_ADMIN,
    APP_UPDATE,
}

@Composable
private fun RestrictedState(onRefresh: () -> Unit) {
    EmptyStateBox(
        title = "Acesso restrito",
        message = "Apenas administradores podem revisar contribuições.",
        icon = Icons.Rounded.AdminPanelSettings,
        modifier = Modifier.padding(top = 32.dp),
        actionLabel = "Revisar permissão",
        onAction = onRefresh,
    )
}

@Composable
private fun ModerationHero(
    isPrincipalAdmin: Boolean,
    onRefresh: () -> Unit,
) {
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
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Central de moderação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Revise e gerencie as contribuições enviadas pela comunidade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f),
                )
                if (isPrincipalAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "ADM principal",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Atualizar",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ModerationHomeSection(
    pendingCount: Int,
    totalCount: Int,
    isLoading: Boolean,
    isPrincipalAdmin: Boolean,
    onOpenPending: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenPromote: () -> Unit,
    onOpenAppUpdate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val pendingMessage = when {
            isLoading -> "Carregando contribuições…"
            pendingCount > 0 -> "$pendingCount contribuição(ões) aguardando revisão."
            else -> "Nenhuma pendência no momento."
        }
        ModerationActionCard(
            icon = Icons.Rounded.Schedule,
            title = "Pendências",
            message = pendingMessage,
            actionLabel = "Revisar pendências",
            onClick = onOpenPending,
        )
        ModerationActionCard(
            icon = Icons.Rounded.ListAlt,
            title = "Todas as contribuições",
            message = if (totalCount > 0) {
                "$totalCount contribuição(ões) no catálogo da comunidade."
            } else {
                "Veja, edite e gerencie todas as contribuições."
            },
            actionLabel = "Ver contribuições",
            onClick = onOpenAll,
        )
        if (isPrincipalAdmin) {
            ModerationActionCard(
                icon = Icons.Rounded.PersonAdd,
                title = "Promover administrador",
                message = "Adicione um usuário como ADM informando o e-mail.",
                actionLabel = "Promover ADM",
                onClick = onOpenPromote,
            )
            ModerationActionCard(
                icon = Icons.Rounded.SystemUpdateAlt,
                title = "Atualização do aplicativo",
                message = "Publique a nova versão do Recomp Hub que os usuários verão ao abrir o app.",
                actionLabel = "Gerenciar atualização",
                onClick = onOpenAppUpdate,
            )
        }
    }
}

@Composable
private fun ModerationActionCard(
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
private fun PendingSubmissionsSection(
    uiState: AdminReviewUiState,
    actionStates: Map<String, AdminActionState>,
    onApprove: (slug: String, name: String) -> Unit,
    onReject: (slug: String) -> Unit,
    onEdit: (GameSubmission) -> Unit,
    onDelete: (GameSubmission) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            uiState.isLoading && uiState.pending.isEmpty() -> {
                LoadingCard()
            }
            uiState.error != null && uiState.pending.isEmpty() -> {
                ErrorStateBox(
                    message = uiState.error.orEmpty(),
                    onRetry = onRetry,
                )
            }
            uiState.pending.isEmpty() -> {
                EmptyStateBox(
                    title = "Nada pendente",
                    message = "Todas as contribuições enviadas já foram revisadas.",
                    icon = Icons.Rounded.CheckCircle,
                )
            }
            else -> {
                uiState.pending.forEach { submission ->
                    AdminSubmissionCard(
                        submission = submission,
                        onApprove = { onApprove(submission.slug, submission.name) },
                        onReject = { onReject(submission.slug) },
                        onEdit = { onEdit(submission) },
                        onDelete = { onDelete(submission) },
                        approveLoading =
                            actionStates["approve:${submission.slug}"]?.inProgress == true,
                        approveError = actionStates["approve:${submission.slug}"]?.error,
                        showReviewActions = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllContributionsSection(
    uiState: AdminReviewUiState,
    actionStates: Map<String, AdminActionState>,
    onApprove: (slug: String, name: String) -> Unit,
    onReject: (slug: String) -> Unit,
    onEdit: (GameSubmission) -> Unit,
    onDelete: (GameSubmission) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.allError?.let { allError ->
            AllErrorBanner(
                message = allError,
                onRetry = onRetry,
                onDismiss = onDismissError,
            )
        }
        when {
            uiState.all.isEmpty() && uiState.allError == null && uiState.isLoading -> {
                LoadingCard()
            }
            uiState.all.isEmpty() && uiState.allError == null -> {
                EmptyStateBox(
                    title = "Nenhuma contribuição",
                    message = "Ainda não há contribuições registradas.",
                    icon = Icons.Rounded.ListAlt,
                )
            }
            else -> {
                uiState.all.forEach { submission ->
                    AdminSubmissionCard(
                        submission = submission,
                        onApprove = { onApprove(submission.slug, submission.name) },
                        onReject = { onReject(submission.slug) },
                        onEdit = { onEdit(submission) },
                        onDelete = { onDelete(submission) },
                        approveLoading =
                            actionStates["approve:${submission.slug}"]?.inProgress == true,
                        approveError = actionStates["approve:${submission.slug}"]?.error,
                        showReviewActions = submission.status == "pending",
                    )
                }
            }
        }
    }
}

@Composable
private fun PromoteAdminSection(
    principalStatus: PrincipalAdminStatus,
    promotionState: AdminPromotionUiState,
    onPromoteClick: (String) -> Unit,
    onDismissPromotion: () -> Unit,
    onRetryStatus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (principalStatus) {
            PrincipalAdminStatus.Granted -> {
                PromoteAdminCard(
                    state = promotionState,
                    onPromoteClick = onPromoteClick,
                    onDismissPromotion = onDismissPromotion,
                )
            }
            PrincipalAdminStatus.Loading, PrincipalAdminStatus.Unknown -> {
                PrincipalStatusPlaceholder()
            }
            PrincipalAdminStatus.Failed -> {
                PrincipalStatusFailed(onRetry = onRetryStatus)
            }
            PrincipalAdminStatus.Denied -> {
                PrincipalStatusFailed(onRetry = onRetryStatus)
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    state: AdminAppUpdateState,
    actionState: AppReleaseUiState,
    onPublishClick: (versionName: String, downloadUrl: String, notes: String?) -> Unit,
    onRemoveClick: (releaseId: String) -> Unit,
    onDismissResult: () -> Unit,
    onRefresh: () -> Unit,
) {
    var versionName by rememberSaveable { mutableStateOf("") }
    var downloadUrl by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showVersionError by rememberSaveable { mutableStateOf(false) }
    var showUrlError by rememberSaveable { mutableStateOf(false) }

    val isPublishing = actionState is AppReleaseUiState.Loading
    val result = actionState as? AppReleaseUiState.Result
    val current = state.current

    LaunchedEffect(result?.ok) {
        if (result?.ok == true) {
            versionName = ""
            downloadUrl = ""
            notes = ""
            showVersionError = false
            showUrlError = false
        }
    }

    val attemptPublish = {
        if (!isPublishing) {
            val name = versionName.trim()
            val url = downloadUrl.trim()
            val versionValid = isValidVersionName(name)
            val urlValid = url.startsWith("http://") || url.startsWith("https://")
            showVersionError = !versionValid
            showUrlError = !urlValid
            if (versionValid && urlValid) {
                onPublishClick(name, url, notes.trim().takeIf { it.isNotBlank() })
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atualização do Recomp Hub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Informe a versão e o link de download. Os usuários recebem o aviso ao abrir o app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "ADM principal",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = state.isLoading || state.loadError != null || current != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    when {
                        state.isLoading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Carregando publicação atual…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.loadError != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = state.loadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                        current != null -> Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Publicada no ar: v${current.versionName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (current.id != null) {
                                        TextButton(onClick = { onRemoveClick(current.id) }) {
                                            Text("Remover", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                current.publishedAt?.let { date ->
                                    Text(
                                        text = "Publicada em ${date.take(10)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                current.notes?.takeIf { it.isNotBlank() }?.let { note ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = versionName,
                onValueChange = {
                    versionName = it
                    showVersionError = false
                },
                label = { Text("Versão") },
                placeholder = { Text("1.0.1") },
                singleLine = true,
                enabled = !isPublishing,
                isError = showVersionError,
                supportingText = {
                    Text(
                        text = if (showVersionError) {
                            "Use números e pontos, ex.: 1.0.1"
                        } else {
                            "Compare apenas números e pontos (até 3 níveis)."
                        },
                        color = if (showVersionError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = downloadUrl,
                onValueChange = {
                    downloadUrl = it
                    showUrlError = false
                },
                label = { Text("Link de download do APK") },
                placeholder = { Text("https://…") },
                singleLine = true,
                enabled = !isPublishing,
                isError = showUrlError,
                supportingText = {
                    Text(
                        text = if (showUrlError) {
                            "Informe um link começando com https://"
                        } else {
                            "O botão «Baixar atualização» abrirá este link."
                        },
                        color = if (showUrlError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { attemptPublish() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Novidades (opcional)") },
                placeholder = { Text("O que mudou nesta versão…") },
                minLines = 2,
                enabled = !isPublishing,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = attemptPublish,
                enabled = versionName.isNotBlank() && downloadUrl.isNotBlank() && !isPublishing,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPublishing) "Publicando…" else "Publicar atualização",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            AnimatedVisibility(
                visible = result != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                result?.let {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        message = it.message,
                        isError = !it.ok,
                        leadingIcon = if (it.ok) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        onDismiss = onDismissResult,
                    )
                }
            }
        }
    }
}

private fun isValidVersionName(value: String): Boolean =
    value.matches(Regex("^\\d+(\\.\\d+){0,2}$"))

@Composable
private fun LoadingCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Carregando contribuições…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Tentar novamente") }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Fechar",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun PrincipalStatusPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Verificando permissão…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrincipalStatusFailed(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Não foi possível verificar sua permissão.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}

@Composable
private fun InlineErrorRow(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PromoteAdminCard(
    state: AdminPromotionUiState,
    onPromoteClick: (String) -> Unit,
    onDismissPromotion: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var showEmailError by rememberSaveable { mutableStateOf(false) }
    val result = state as? AdminPromotionUiState.Result
    val isLoading = state is AdminPromotionUiState.Loading

    val attemptPromote = {
        if (!isLoading) {
            val trimmed = email.trim()
            if (isValidEmail(trimmed)) {
                showEmailError = false
                onPromoteClick(trimmed)
            } else {
                showEmailError = true
            }
        }
    }

    LaunchedEffect(result?.ok) {
        if (result?.ok == true) {
            email = ""
            showEmailError = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Promover ADM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Adicione um usuário como ADM informando o e-mail dele.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "ADM principal",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    showEmailError = false
                },
                label = { Text("E-mail do usuário") },
                placeholder = { Text("usuario@exemplo.com") },
                singleLine = true,
                enabled = !isLoading,
                isError = showEmailError,
                supportingText = {
                    Text(
                        text = if (showEmailError) {
                            "Informe um e-mail válido."
                        } else {
                            "Somente você, ADM principal, pode promover usuários."
                        },
                        color = if (showEmailError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { attemptPromote() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = attemptPromote,
                enabled = email.isNotBlank() && !isLoading,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isLoading) "Promovendo…" else "Promover",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            AnimatedVisibility(
                visible = result != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                result?.let {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        message = it.message,
                        isError = !it.ok,
                        leadingIcon = if (it.ok) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        onDismiss = onDismissPromotion,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromoteConfirmDialog(
    email: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Promover usuário a ADM") },
        text = {
            Text(
                "O usuário com o e-mail «$email» passará a ter permissões de ADM " +
                    "e poderá revisar e moderar contribuições. Deseja continuar?",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Promover") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminSubmissionCard(
    submission: GameSubmission,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showReviewActions: Boolean,
    approveLoading: Boolean = false,
    approveError: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = submission.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (submission.slug.isNotBlank()) {
                        Text(
                            text = submission.slug,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                SubmissionStatusChip(status = reviewStatus(submission.status))
            }

            if (submission.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = submission.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val metas = buildList {
                submission.originalPlatform?.takeIf { it.isNotBlank() }?.let { add(it) }
                submission.author?.takeIf { it.isNotBlank() }?.let { add(it) }
                submission.version?.takeIf { it.isNotBlank() }?.let { add("v$it") }
            }
            if (metas.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    metas.forEach { meta ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            submission.reviewReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Motivo: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar")
                }
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir")
                }
            }

            if (showReviewActions) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !approveLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rejeitar")
                    }
                    Button(
                        onClick = onApprove,
                        enabled = !approveLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        if (approveLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (approveLoading) "Aprovando…" else "Aprovar")
                    }
                }
                AnimatedVisibility(
                    visible = approveError != null,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    approveError?.let { InlineErrorRow(message = it, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

private fun reviewStatus(status: String): SubmissionStatus = SubmissionStatus.fromRaw(status)

@Composable
private fun AdminEditDialog(
    submission: GameSubmission,
    actionState: AdminActionState?,
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
    val inProgress = actionState?.inProgress == true
    val error = actionState?.error
    val sizeInvalid = size.isNotBlank() && size.trim().toLongOrNull() == null

    LaunchedEffect(actionState) {
        val state = actionState ?: return@LaunchedEffect
        if (!state.inProgress && state.error == null) onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Editar contribuição") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 3, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(platform, { platform = it }, label = { Text("Plataforma") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(version, { version = it }, label = { Text("Versão") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(repository, { repository = it }, label = { Text("Repositório") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(apkUrl, { apkUrl = it }, label = { Text("URL do APK") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item {
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = { Text("Tamanho em bytes") },
                        singleLine = true,
                        enabled = !inProgress,
                        isError = sizeInvalid,
                        supportingText = if (sizeInvalid) {
                            { Text("Tamanho inválido.") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags separadas por vírgula") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(cover, { cover = it }, label = { Text("URL da capa") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(banner, { banner = it }, label = { Text("URL do banner") }, singleLine = true, enabled = !inProgress, modifier = Modifier.fillMaxWidth()) }
                if (error != null) {
                    item {
                        InlineErrorRow(message = error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        submission.copy(
                            slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                            name = name.trim(), description = description.trim(),
                            originalPlatform = platform.trim().ifBlank { null }, author = author.trim().ifBlank { null },
                            version = version.trim().ifBlank { null }, sourceRepo = repository.trim().ifBlank { null },
                            apkUrl = apkUrl.trim().ifBlank { null }, fileSizeBytes = size.trim().toLongOrNull() ?: 0L,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                            coverUrl = cover.trim().ifBlank { null }, bannerUrl = banner.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = name.isNotBlank() && !sizeInvalid && !inProgress,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (inProgress) "Salvando…" else "Salvar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancelar") }
        },
    )
}

@Composable
private fun RejectDialog(
    submissionName: String,
    submissionSlug: String,
    actionState: AdminActionState?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable(submissionSlug) { mutableStateOf("") }
    val inProgress = actionState?.inProgress == true
    val error = actionState?.error

    LaunchedEffect(actionState) {
        val state = actionState ?: return@LaunchedEffect
        if (!state.inProgress && state.error == null) onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
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
                    enabled = !inProgress,
                )
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    error?.let {
                        InlineErrorRow(message = it, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank() && !inProgress,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (inProgress) "Rejeitando…" else "Rejeitar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    submissionName: String,
    actionState: AdminActionState?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inProgress = actionState?.inProgress == true
    val error = actionState?.error

    LaunchedEffect(actionState) {
        val state = actionState ?: return@LaunchedEffect
        if (!state.inProgress && state.error == null) onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Excluir contribuição?") },
        text = {
            Column {
                Text("A contribuição «$submissionName» será removida permanentemente.")
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    error?.let {
                        InlineErrorRow(message = it, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !inProgress,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (inProgress) "Excluindo…" else "Excluir")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancelar") } },
    )
}

private fun isValidEmail(value: String): Boolean =
    value.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))