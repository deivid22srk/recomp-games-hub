package com.recomp.gameshub.domain.model

data class CurrentUser(
    val id: String,
    val email: String?,
    val isAdmin: Boolean = false,
    val username: String? = null,
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

sealed class AuthState {
    data object SignedOut : AuthState()
    data class SignedIn(
        val user: CurrentUser,
        val session: AuthSession,
    ) : AuthState()
}

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)