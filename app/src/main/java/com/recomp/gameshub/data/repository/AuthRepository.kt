package com.recomp.gameshub.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recomp.gameshub.data.remote.supabase.AuthSessionDto
import com.recomp.gameshub.data.remote.supabase.SupabaseApi
import com.recomp.gameshub.domain.model.AuthException
import com.recomp.gameshub.domain.model.AuthSession
import com.recomp.gameshub.domain.model.AuthState
import com.recomp.gameshub.domain.model.CurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth")

class AuthRepository(
    private val context: Context,
    private val api: SupabaseApi,
) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val expiresAtKey = longPreferencesKey("expires_at")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val isAdminKey = stringPreferencesKey("is_admin")
    private val usernameKey = stringPreferencesKey("username")

    private val _state = MutableStateFlow<AuthState>(restore())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _isAdmin = MutableStateFlow((_state.value as? AuthState.SignedIn)?.user?.isAdmin ?: false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _username = MutableStateFlow((_state.value as? AuthState.SignedIn)?.user?.username)
    val username: StateFlow<String?> = _username.asStateFlow()

    val session: AuthSession?
        get() = (_state.value as? AuthState.SignedIn)?.session

    private fun restore(): AuthState {
        val prefs = kotlinx.coroutines.runBlocking { context.authDataStore.data.first() }
        val access = prefs[accessTokenKey] ?: return AuthState.SignedOut
        if (access.isBlank()) return AuthState.SignedOut
        val userId = prefs[userIdKey] ?: return AuthState.SignedOut
        val user = CurrentUser(
            id = userId,
            email = prefs[userEmailKey],
            isAdmin = prefs[isAdminKey]?.toBooleanStrictOrNull() ?: false,
            username = prefs[usernameKey],
        )
        return AuthState.SignedIn(
            user = user,
            session = AuthSession(
                accessToken = access,
                refreshToken = prefs[refreshTokenKey] ?: "",
                expiresAt = prefs[expiresAtKey] ?: 0L,
            ),
        )
    }

    suspend fun signIn(email: String, password: String): Result<CurrentUser> =
        authFlow {
            val session = api.signIn(email, password)
            val profile = runCatching { api.fetchProfile(session.accessToken) }.getOrNull()
            val user = session.toCurrentUser(profile?.isAdmin ?: false)
            persist(user, session)
            user
        }

    suspend fun signUp(email: String, password: String): Result<CurrentUser> =
        authFlow {
            val session = api.signUp(email, password)
            val user = session.toCurrentUser(isAdmin = false)
            persist(user, session)
            user
        }

    suspend fun signOut() {
        (session as? AuthSession)?.let { api.signOut(it.accessToken) }
        clear()
        _state.value = AuthState.SignedOut
        _isAdmin.value = false
        _username.value = null
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { api.sendPasswordReset(email) }
            .recoverCatching { e -> throw e.friendly() }

    suspend fun refreshIfNeeded() {
        val current = session ?: return
        if (System.currentTimeMillis() < current.expiresAt) return
        if (current.refreshToken.isBlank()) {
            forceSignOut()
            return
        }
        runCatching {
            val refreshed = api.refreshSession(current.refreshToken)
            val profile = runCatching { api.fetchProfile(refreshed.accessToken) }.getOrNull()
            val user = refreshed.toCurrentUser(profile?.isAdmin ?: false)
            persist(user, refreshed)
        }.onFailure {
            forceSignOut()
        }
    }

    suspend fun refreshAdminProfile() {
        val current = session ?: return
        runCatching {
            val profile = api.fetchProfile(current.accessToken) ?: return
            val signedIn = _state.value as? AuthState.SignedIn ?: return
            val user = signedIn.user.copy(isAdmin = profile.isAdmin)
            persist(user, signedIn.session)
        }
    }

    private suspend fun persist(user: CurrentUser, session: AuthSessionDto) {
        context.authDataStore.edit { prefs ->
            prefs[accessTokenKey] = session.accessToken
            prefs[refreshTokenKey] = session.refreshToken
            prefs[expiresAtKey] = System.currentTimeMillis() + (session.expiresIn * 1000L)
            prefs[userIdKey] = user.id
            if (user.email != null) prefs[userEmailKey] = user.email
            prefs[isAdminKey] = user.isAdmin.toString()
            if (user.username != null) prefs[usernameKey] = user.username
        }
        _state.value = AuthState.SignedIn(
            user = user,
            session = AuthSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAt = System.currentTimeMillis() + (session.expiresIn * 1000L),
            ),
        )
        _isAdmin.value = user.isAdmin
        _username.value = null
    }

    private suspend fun clear() = context.authDataStore.edit { it.clear() }

    private suspend fun forceSignOut() {
        clear()
        _state.value = AuthState.SignedOut
        _isAdmin.value = false
        _username.value = null
    }

    private inline fun <T> authFlow(crossinline block: suspend () -> T): Result<T> =
        runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { block() } }
            .recoverCatching { e -> throw e.friendly() }

    private fun Throwable.friendly(): Throwable = when (this) {
        is AuthException -> this
        is SupabaseApi.NotConfiguredException -> AuthException(
            "O app ainda não está configurado com o backend. " +
                "Defina SUPABASE_URL e SUPABASE_ANON_KEY no build.",
            this,
        )
        is SupabaseApi.ApiException -> {
            val detail = friendlyAuthError(code, detailText())
            AuthException(detail, this)
        }
        else -> AuthException(this.message ?: "Erro de autenticação.", this)
    }

    private fun SupabaseApi.ApiException.detailText(): String = detail

    private fun friendlyAuthError(code: Int, raw: String): String {
        val lower = raw.lowercase()
        return when {
            code == 400 && ("invalid login credentials" in lower || "invalid.credentials" in lower) ->
                "E-mail ou senha incorretos."
            code == 400 && "user already registered" in lower ->
                "Este e-mail já está cadastrado."
            code == 422 && "email not confirmed" in lower ->
                "E-mail ainda não confirmado. Verifique sua caixa de entrada."
            code == 429 -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."
            code == 403 -> "Ação não permitida pela política de segurança."
            code == 401 -> "Sessão expirada. Entre novamente."
            else -> raw.trim().ifBlank { "Erro de autenticação (HTTP $code)." }
        }
    }

    private fun AuthSessionDto.toCurrentUser(isAdmin: Boolean) =
        CurrentUser(
            id = user.id,
            email = user.email,
            isAdmin = isAdmin,
            username = null,
        )
}