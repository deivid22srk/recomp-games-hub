package com.recomp.gameshub.data.remote.supabase

import com.recomp.gameshub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SupabaseApi(
    private val client: OkHttpClient,
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ApiException(val code: Int, val detail: String) : Exception(detail)
    class NotConfiguredException : Exception(
        "Supabase não configurado. Defina SUPABASE_URL e SUPABASE_ANON_KEY no build."
    )

    // ---------- HTTP helpers ----------

    private suspend fun raw(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
    ): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) throw NotConfiguredException()
        val req = Request.Builder()
            .url("$baseUrl$path")
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("apikey", anonKey)
            }
            .apply {
                when (method) {
                    "GET" -> get()
                    "POST" -> post(body ?: "{}".toRequestBody(jsonMediaType))
                    "PATCH" -> patch(body ?: "{}".toRequestBody(jsonMediaType))
                    "DELETE" -> delete(body)
                    else -> get()
                }
            }
            .build()
        client.newCall(req).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, text)
            }
            text
        }
    }

    private fun jsonBody(value: String): RequestBody = value.toRequestBody(jsonMediaType)

    private fun postgrestHeaders(token: String? = null, prefer: String = "return=representation"): Map<String, String> =
        buildMap {
            put("Content-Type", "application/json")
            put("Prefer", prefer)
            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
        }

    /** Escapes a PostgREST filter value inside quotes. */
    private fun esc(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    // ---------- Auth (GoTrue) ----------

    private suspend fun authRequest(endpoint: String, body: JsonObject): AuthSessionDto {
        val text = raw(
            "POST",
            "/auth/v1/$endpoint",
            headers = mapOf("Content-Type" to "application/json"),
            body = jsonBody(json.encodeToString(body)),
        )
        return try {
            json.decodeFromString<AuthSessionDto>(text)
        } catch (e: SerializationException) {
            throw IOException("Resposta inesperada de auth ($endpoint): ${text.take(200)}", e)
        }
    }

    suspend fun signUp(email: String, password: String): AuthSessionDto =
        authRequest(
            "signup",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )

    suspend fun signIn(email: String, password: String): AuthSessionDto =
        authRequest(
            "token?grant_type=password",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )

    suspend fun refreshSession(refreshToken: String): AuthSessionDto =
        authRequest(
            "token?grant_type=refresh_token",
            buildJsonObject { put("refresh_token", refreshToken) },
        )

    suspend fun sendPasswordReset(email: String) {
        raw(
            "POST",
            "/auth/v1/recover",
            headers = mapOf("Content-Type" to "application/json"),
            body = jsonBody(json.encodeToString(buildJsonObject { put("email", email) })),
        )
    }

    suspend fun fetchUser(accessToken: String): AuthUserDto {
        val text = raw(
            "GET",
            "/auth/v1/user",
            headers = mapOf("Authorization" to "Bearer $accessToken"),
        )
        return try {
            json.decodeFromString<AuthUserDto>(text)
        } catch (e: SerializationException) {
            throw IOException("Resposta inesperada de auth user: ${text.take(200)}", e)
        }
    }

    suspend fun signOut(accessToken: String) {
        runCatching {
            raw(
                "POST",
                "/auth/v1/logout",
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Content-Type" to "application/json",
                ),
                body = jsonBody("{}"),
            )
        }
    }

    // ---------- Catalog / PostgREST ----------

    /**
     * Approved games with their screenshots embedded (game_id join).
     * Anonymous users see only `review_status = approved`.
     */
    suspend fun fetchApprovedGames(): List<GameRow> =
        selectRows(
            GAMES_TABLE,
            filter = "review_status=eq.$REVIEW_APPROVED",
            select = "id,slug,title,description,original_platform,status,version,author," +
                "source_repo_url,apk_url,file_size_bytes,sha256,tags,cover_url,banner_url," +
                "review_status,created_at,updated_at,game_screenshots(image_url,sort_order)",
            serializer = GameRow.serializer(),
        )

    suspend fun fetchMyGames(accessToken: String): List<GameRow> {
        val userId = fetchUser(accessToken).id
        return selectRows(
            GAMES_TABLE,
            filter = "submitted_by=eq.$userId",
            select = "id,slug,title,description,original_platform,status,version,author," +
                "source_repo_url,apk_url,file_size_bytes,sha256,tags,cover_url,banner_url," +
                "submitted_by,review_status,created_at,updated_at,game_screenshots(image_url,sort_order)",
            token = accessToken,
            serializer = GameRow.serializer(),
        )
    }

    suspend fun fetchPendingGames(accessToken: String): List<GameRow> =
        selectRows(
            GAMES_TABLE,
            filter = "review_status=eq.$REVIEW_PENDING",
            select = "id,slug,title,description,original_platform,status,version,author," +
                "source_repo_url,apk_url,file_size_bytes,sha256,tags,cover_url,banner_url," +
                "submitted_by,review_status,created_at,updated_at",
            token = accessToken,
            serializer = GameRow.serializer(),
        )

    suspend fun fetchProfile(accessToken: String): ProfileRow? {
        val userId = fetchUser(accessToken).id
        return selectRows(
            PROFILES_TABLE,
            filter = "id=eq.$userId",
            select = "id,email,is_admin",
            token = accessToken,
            serializer = ProfileRow.serializer(),
        ).firstOrNull()
    }

    suspend fun insertGame(game: GameRow, accessToken: String, userId: String) {
        val payload = buildJsonObject {
            put("slug", game.slug)
            put("title", game.title)
            put("description", game.description)
            put("status", game.status.ifBlank { "in_development" })
            put("review_status", REVIEW_PENDING)
            put("submitted_by", userId)
            game.originalPlatform?.takeIf { it.isNotBlank() }?.let { put("original_platform", it) }
            game.version?.takeIf { it.isNotBlank() }?.let { put("version", it) }
            game.author?.takeIf { it.isNotBlank() }?.let { put("author", it) }
            game.sourceRepoUrl?.takeIf { it.isNotBlank() }?.let { put("source_repo_url", it) }
            game.apkUrl?.takeIf { it.isNotBlank() }?.let { put("apk_url", it) }
            put("file_size_bytes", game.fileSizeBytes)
            game.sha256?.takeIf { it.isNotBlank() }?.let { put("sha256", it) }
            put("tags", game.tags)
            game.coverUrl?.takeIf { it.isNotBlank() }?.let { put("cover_url", it) }
            game.bannerUrl?.takeIf { it.isNotBlank() }?.let { put("banner_url", it) }
        }
        raw(
            "POST",
            "/rest/v1/$GAMES_TABLE",
            headers = postgrestHeaders(accessToken, prefer = "return=minimal"),
            body = jsonBody(json.encodeToString(payload)),
        )
    }

    suspend fun updateOwnGame(slug: String, game: GameRow, accessToken: String) {
        val payload = buildJsonObject {
            put("title", game.title)
            put("description", game.description)
            put("status", game.status.ifBlank { "in_development" })
            game.originalPlatform?.takeIf { it.isNotBlank() }?.let { put("original_platform", it) }
            game.version?.takeIf { it.isNotBlank() }?.let { put("version", it) }
            game.author?.takeIf { it.isNotBlank() }?.let { put("author", it) }
            game.sourceRepoUrl?.takeIf { it.isNotBlank() }?.let { put("source_repo_url", it) }
            game.apkUrl?.takeIf { it.isNotBlank() }?.let { put("apk_url", it) }
            put("file_size_bytes", game.fileSizeBytes)
            game.sha256?.takeIf { it.isNotBlank() }?.let { put("sha256", it) }
            put("tags", game.tags)
            game.coverUrl?.takeIf { it.isNotBlank() }?.let { put("cover_url", it) }
            game.bannerUrl?.takeIf { it.isNotBlank() }?.let { put("banner_url", it) }
        }
        raw(
            "PATCH",
            "/rest/v1/$GAMES_TABLE?slug=eq.${esc(slug)}",
            headers = postgrestHeaders(accessToken),
            body = jsonBody(json.encodeToString(payload)),
        )
    }

    suspend fun deleteOwnGame(slug: String, accessToken: String) {
        raw(
            "DELETE",
            "/rest/v1/$GAMES_TABLE?slug=eq.${esc(slug)}",
            headers = postgrestHeaders(token = accessToken, prefer = "return=minimal"),
        )
    }

    suspend fun setReviewStatus(
        slug: String,
        reviewStatus: String,
        accessToken: String,
        reason: String? = null,
    ) {
        val payload = json.encodeToString(
            buildJsonObject {
                put("review_status", reviewStatus)
                if (reason != null) put("review_reason", reason)
            }
        )
        raw(
            "PATCH",
            "/rest/v1/$GAMES_TABLE?slug=eq.${esc(slug)}",
            headers = postgrestHeaders(accessToken),
            body = jsonBody(payload),
        )
    }

    private suspend fun <T : kotlinx.serialization.Serializable> selectRows(
        table: String,
        filter: String,
        select: String = "*",
        token: String? = null,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        val headers = postgrestHeaders(token, prefer = "return=representation")
        val text = raw("GET", "/rest/v1/$table?$filter&select=$select", headers = headers)
        return try {
            val arr = json.decodeFromString<JsonArray>(text)
            arr.mapNotNull { row ->
                runCatching { json.decodeFromString(serializer, row.toString()) }.getOrNull()
            }
        } catch (e: SerializationException) {
            throw IOException("JSON inválido em select $table: ${text.take(200)}", e)
        }
    }
}