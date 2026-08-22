package com.recomp.gameshub.presentation.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomp.gameshub.core.navigation.appViewModel
import com.recomp.gameshub.domain.model.RepoSourceType

@Composable
fun SetupRoute(onComplete: () -> Unit) {
    val viewModel: SetupViewModel = appViewModel {
        SetupViewModel(it.repoRepository, it.repoManager)
    }
    val step by viewModel.step.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val customUrl by viewModel.customUrl.collectAsStateWithLifecycle()
    val localPath by viewModel.localPath.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is SetupUiState.Done) onComplete()
    }

    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var notificationGranted by remember { mutableStateOf(
        if (Build.VERSION.SDK_INT < 33) true else
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    ) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.onLocalPathPicked(uri.toString())
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(48.dp))
                StepIndicator(
                    current = step.ordinal,
                    total = 2,
                    labels = listOf("Permissões", "Repositório"),
                )
                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "setupStep",
                ) { current ->
                    when (current) {
                        SetupStep.PERMISSIONS -> PermissionsStep(
                            notificationGranted = notificationGranted,
                        )
                        SetupStep.REPOSITORY -> RepositoryStep(
                            selectedType = selectedType,
                            customUrl = customUrl,
                            localPath = localPath,
                            onTypeSelected = viewModel::selectType,
                            onUrlChanged = viewModel::onUrlChanged,
                            onPickLocal = { treeLauncher.launch(null) },
                            state = state,
                            onRetry = viewModel::resetError,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    when (step) {
                        SetupStep.PERMISSIONS -> viewModel.nextStep()
                        SetupStep.REPOSITORY -> viewModel.finish()
                    }
                },
                enabled = when (step) {
                    SetupStep.PERMISSIONS -> true
                    SetupStep.REPOSITORY -> viewModel.canFinish() && state !is SetupUiState.Loading
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (step == SetupStep.REPOSITORY) "Concluir" else "Próximo",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                if (state is SetupUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int, labels: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(total) { index ->
            val isActive = index <= current
            val isCurrent = index == current
            Surface(
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
                modifier = Modifier.size(if (isCurrent) 14.dp else 10.dp),
            ) {}
            if (index < total - 1) {
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp),
                ) {}
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = labels.getOrElse(current) { "" },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PermissionsStep(notificationGranted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Para começar, o Recomp Hub precisa de algumas permissões.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PermissionCard(
            icon = Icons.Rounded.Notifications,
            title = "Notificações",
            description = "Exibe o progresso dos downloads e avisa quando um jogo for baixado.",
            granted = notificationGranted,
        )
        PermissionCard(
            icon = Icons.Rounded.InstallMobile,
            title = "Instalar aplicativos",
            description = "Permite instalar e atualizar jogos diretamente pelo app.",
            granted = true,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RepositoryStep(
    selectedType: RepoSourceType,
    customUrl: String,
    localPath: String?,
    onTypeSelected: (RepoSourceType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onPickLocal: () -> Unit,
    state: SetupUiState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Escolha a fonte de dados do catálogo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RepoOptionCard(
            icon = Icons.Rounded.Cloud,
            title = "Repositório oficial",
            description = "Usa o catálogo padrão mantido pela equipe do Recomp Hub.",
            selected = selectedType == RepoSourceType.DEFAULT,
            onClick = { onTypeSelected(RepoSourceType.DEFAULT) },
        )

        RepoOptionCard(
            icon = Icons.Rounded.CloudDownload,
            title = "Repositório de terceiros",
            description = "Informe a URL de um repositório GitHub compatível.",
            selected = selectedType == RepoSourceType.GITHUB_URL,
            onClick = { onTypeSelected(RepoSourceType.GITHUB_URL) },
        )

        if (selectedType == RepoSourceType.GITHUB_URL) {
            OutlinedTextField(
                value = customUrl,
                onValueChange = onUrlChanged,
                label = { Text("URL do repositório") },
                placeholder = { Text("https://github.com/usuario/repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        RepoOptionCard(
            icon = Icons.Rounded.Folder,
            title = "Importar localmente",
            description = "Selecione uma pasta do dispositivo contendo a estrutura do repositório.",
            selected = selectedType == RepoSourceType.LOCAL,
            onClick = { onTypeSelected(RepoSourceType.LOCAL) },
        )

        if (selectedType == RepoSourceType.LOCAL) {
            Button(
                onClick = onPickLocal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (localPath != null) "Alterar pasta" else "Selecionar pasta")
            }
            if (localPath != null) {
                Text(
                    text = "Pasta selecionada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when (state) {
            is SetupUiState.Error -> {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null,
                             tint = MaterialTheme.colorScheme.onErrorContainer,
                             modifier = Modifier.size(20.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) { Text("OK") }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun RepoOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                     color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall,
                     color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                             else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}