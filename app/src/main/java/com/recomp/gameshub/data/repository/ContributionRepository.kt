package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.remote.supabase.GameRow
import com.recomp.gameshub.data.remote.GithubReleaseApi
import com.recomp.gameshub.data.remote.supabase.SupabaseApi
import com.recomp.gameshub.data.remote.supabase.toSubmission
import com.recomp.gameshub.domain.model.AuthException
import com.recomp.gameshub.domain.model.AuthState
import com.recomp.gameshub.domain.model.AdminPromotionResult
import com.recomp.gameshub.domain.model.GameSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContributionRepository(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository,
    private val githubReleaseApi: GithubReleaseApi,
) {
    private fun requireToken(admin: Boolean = false): String {
        val access = authRepository.session?.accessToken
            ?: throw AuthException("Você precisa estar conectado para acessar esta área.")
        if (admin && !authRepository.isAdmin.value) {
            throw AuthException("Apenas administradores podem realizar esta ação.")
        }
        return access
    }

    suspend fun submit(game: GameSubmission): Result<Unit> =
        runCatching {
            val token = requireToken()
            val userId = (authRepository.state.value as? AuthState.SignedIn)?.user?.id
                ?: throw AuthException("Sessão inválida. Entre novamente.")
            api.insertGame(game.toRow(), token, userId, game.screenshots)
        }

    suspend fun resolveLatestRelease(repositoryUrl: String) =
        runCatching { githubReleaseApi.latest(repositoryUrl) }

    suspend fun updateOwn(submission: GameSubmission): Result<Unit> =
        runCatching {
            val token = requireToken()
            api.updateOwnGame(submission.slug, submission.toRow(), token, submission.screenshots)
        }

    suspend fun deleteOwn(slug: String): Result<Unit> =
        runCatching {
            val token = requireToken()
            api.deleteOwnGame(slug, token)
        }

    suspend fun mySubmissions(): Result<List<GameSubmission>> =
        runCatching {
            val token = requireToken()
            api.fetchMyGames(token).map { it.toSubmission() }
        }

    // ---------- Admin review ----------

    suspend fun pendingSubmissions(): Result<List<GameSubmission>> =
        runCatching {
            val token = requireToken(admin = true)
            api.fetchPendingGames(token).map { it.toSubmission() }
        }

    suspend fun allSubmissions(): Result<List<GameSubmission>> =
        runCatching {
            val token = requireToken(admin = true)
            api.fetchAllGames(token).map { it.toSubmission() }
        }

    suspend fun approve(slug: String): Result<Unit> =
        runCatching {
            val token = requireToken(admin = true)
            api.setReviewStatus(slug, STATUS_APPROVED, token)
        }

    suspend fun reject(slug: String, reason: String): Result<Unit> =
        runCatching {
            val token = requireToken(admin = true)
            api.setReviewStatus(slug, STATUS_REJECTED, token, reason.takeIf { it.isNotBlank() })
        }

    suspend fun adminUpdate(slug: String, game: GameSubmission): Result<Unit> =
        runCatching {
            val token = requireToken(admin = true)
            api.updateOwnGame(slug, game.toRow(), token, game.screenshots)
        }

    suspend fun adminDelete(slug: String): Result<Unit> =
        runCatching {
            val token = requireToken(admin = true)
            api.deleteOwnGame(slug, token)
        }

    // ---------- Admin management ----------

    suspend fun promoteAdmin(email: String): Result<AdminPromotionResult> =
        runCatching {
            val token = requireToken()
            val result = api.promoteAdmin(email, token)
            AdminPromotionResult(
                ok = result.ok,
                code = result.code,
                message = result.message ?: "Nenhuma resposta do servidor.",
            )
        }

    suspend fun isPrincipalAdmin(): Result<Boolean> =
        runCatching {
            val token = requireToken()
            api.isPrincipalAdmin(token)
        }

    private fun GameSubmission.toRow(): GameRow =
        GameRow(
            id = submissionId.takeIf { it.isNotBlank() },
            slug = slug,
            title = name,
            description = description,
            status = devStatus,
            version = version,
            originalPlatform = originalPlatform,
            author = author,
            sourceRepoUrl = sourceRepo,
            apkUrl = apkUrl,
            fileSizeBytes = fileSizeBytes,
            tags = tags,
            coverUrl = coverUrl,
            bannerUrl = bannerUrl,
            reviewReason = reviewReason,
            screenshots = emptyList(),
        )

    suspend fun validateSlugAvailability(slug: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existing = api.fetchApprovedGames().any { it.slug == slug } ||
                    fetchPendingGamesSafe().any { it.slug == slug }
                !existing
            }
        }

    private suspend fun fetchPendingGamesSafe() =
        runCatching { api.fetchPendingGames(requireToken()) }.getOrElse { emptyList() }

    companion object {
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_REJECTED = "rejected"
    }
}
