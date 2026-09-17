package com.recomp.gameshub.data.repository

import android.os.SystemClock
import com.recomp.gameshub.data.local.DownloadDao
import com.recomp.gameshub.data.local.toEntity
import com.recomp.gameshub.data.local.toTask
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import com.recomp.gameshub.domain.model.GameDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DownloadRepository(
    private val dao: DownloadDao,
    val downloadsDir: File,
    private val appScope: CoroutineScope,
) {
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    private val _lastPersist = ConcurrentHashMap<String, Long>()

    private val restored = AtomicBoolean(false)

    var onChanged: (() -> Unit)? = null
        private set

    fun bindOnChanged(callback: () -> Unit) {
        onChanged = callback
    }

    val tasks: StateFlow<List<DownloadTask>> = _tasks
        .map { map -> map.values.sortedWith(compareBy<DownloadTask> { rank(it.phase) }.thenByDescending { it.addedAt }) }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeTask(id: String): Flow<DownloadTask?> = _tasks.map { it[id] }

    fun task(id: String): DownloadTask? = _tasks.value[id]

    /** Snapshot síncrono usado pelo serviço para evitar a corrida do StateFlow inicial. */
    fun currentTasks(): List<DownloadTask> = _tasks.value.values.toList()

    fun hasActiveWork(): Boolean = _tasks.value.values.any { it.phase.isActive }

    fun hasPendingWork(): Boolean = _tasks.value.values.any { it.phase == DownloadPhase.PENDING }

    suspend fun restoreInterrupted() {
        if (!restored.compareAndSet(false, true)) return
        val entities = dao.observeAll().first()
        val now = System.currentTimeMillis()
        val loaded = entities.map { entity ->
            var phase = entity.phase.toDownloadPhase()
            if (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.PENDING) {
                phase = DownloadPhase.PENDING
            }
            entity.toTask().copy(phase = phase)
        }
        _tasks.value = loaded.associateBy { it.id }
        loaded.forEach { persist(it.id) }
        onChanged?.invoke()
    }

    /** Enfileira um download; retorna null quando o detalhe não possui URL válida. */
    fun enqueue(detail: GameDetail, expectedSizeBytes: Long? = null): DownloadTask? {
        val slug = detail.summary.slug
        val url = detail.downloadUrl ?: return _tasks.value[slug]
        val existing = _tasks.value[slug]
        if (existing != null && existing.isActive && existing.phase != DownloadPhase.PAUSED) return existing
        if (existing != null && existing.phase == DownloadPhase.PAUSED) {
            if (existing.url == url) {
                resume(existing.id, url)
                return _tasks.value[slug]
            }
            // Versão diferente da pausada: descarta o parcial e recomeça do
            // zero com a nova URL (retomar misturaria os arquivos via Range).
            File(existing.localPath).delete()
            _tasks.update { it - slug }
            appScope.launch(Dispatchers.IO) { dao.delete(slug) }
        }
        val fileName = fileNameFor(detail)
        val file = File(downloadsDir, fileName)
        val task = DownloadTask(
            id = slug,
            gameName = detail.summary.name,
            url = url,
            localPath = file.absolutePath,
            fileName = fileName,
            totalBytes = expectedSizeBytes ?: detail.fileSizeBytes,
            downloadedBytes = 0L,
            phase = DownloadPhase.PENDING,
            addedAt = System.currentTimeMillis(),
        )
        _tasks.update { it + (slug to task) }
        persist(task.id)
        onChanged?.invoke()
        return task
    }

    fun pause(id: String) {
        update(id, DownloadPhase.PAUSED)
    }

    fun resume(id: String, url: String? = null) {
        val current = _tasks.value[id] ?: return
        val task = if (url != null && !url.isBlank()) current.copy(url = url) else current
        _tasks.update { map -> map + (id to task.copy(phase = DownloadPhase.PENDING, errorMessage = null)) }
        persist(id)
        onChanged?.invoke()
    }

    fun retry(id: String) {
        val current = _tasks.value[id] ?: return
        val file = File(current.localPath)
        val downloaded = if (file.exists()) file.length() else 0L
        _tasks.update { map ->
            map + (id to current.copy(
                phase = DownloadPhase.PENDING,
                downloadedBytes = downloaded,
                errorMessage = null,
                completedAt = null,
            ))
        }
        persist(id)
        onChanged?.invoke()
    }

    /** Cancela e remove a task da lista e do armazenamento (como [delete]). */
    fun cancel(id: String) {
        _tasks.value[id]?.let { task -> File(task.localPath).delete() }
        _tasks.update { map -> map - id }
        _lastPersist.remove(id)
        appScope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    fun delete(id: String) {
        _tasks.value[id]?.let { task -> File(task.localPath).delete() }
        _tasks.update { map -> map - id }
        appScope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    fun setPhase(id: String, phase: DownloadPhase, error: String? = null) {
        val completedAt = if (phase == DownloadPhase.COMPLETED) System.currentTimeMillis() else null
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            map + (id to current.copy(phase = phase, errorMessage = error, completedAt = completedAt))
        }
        persist(id)
        onChanged?.invoke()
    }

    fun updateProgress(id: String, downloaded: Long, total: Long, speed: Long, force: Boolean) {
        // A UI acompanha o progresso em tempo real (engine emite a cada ~300 ms);
        // a persistência no Room é mais espaçada para evitar escrita excessiva.
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            map + (id to current.copy(downloadedBytes = downloaded, totalBytes = total, speedBytesPerSec = speed))
        }
        val now = SystemClock.elapsedRealtime()
        val last = _lastPersist[id] ?: 0L
        if (!force && now - last < 1_000L) return
        _lastPersist[id] = now
        persist(id)
    }

    fun resolveTotal(id: String, total: Long) {
        if (total <= 0L) return
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            if (current.totalBytes == total) map else map + (id to current.copy(totalBytes = total))
        }
        persist(id)
    }

    private fun update(id: String, phase: DownloadPhase, error: String? = null) {
        val completedAt = if (phase == DownloadPhase.COMPLETED) System.currentTimeMillis() else null
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            map + (id to current.copy(phase = phase, errorMessage = error, completedAt = completedAt))
        }
        persist(id)
        onChanged?.invoke()
    }

    private fun persist(id: String) {
        val task = _tasks.value[id] ?: return
        appScope.launch(Dispatchers.IO) {
            if (task.phase == DownloadPhase.CANCELLED) {
                dao.delete(task.id)
            } else {
                dao.upsert(task.toEntity())
            }
        }
        if (task.phase == DownloadPhase.CANCELLED) {
            _lastPersist.remove(task.id)
        }
    }

    private fun rank(phase: DownloadPhase): Int = when (phase) {
        DownloadPhase.DOWNLOADING -> 0
        DownloadPhase.PENDING -> 1
        DownloadPhase.PAUSED -> 2
        DownloadPhase.COMPLETED -> 3
        DownloadPhase.FAILED -> 4
        DownloadPhase.CANCELLED -> 5
    }

    private fun String.toDownloadPhase(): DownloadPhase =
        try {
            DownloadPhase.valueOf(this)
        } catch (e: IllegalArgumentException) {
            DownloadPhase.PENDING
        }

    private fun fileNameFor(detail: GameDetail): String {
        // O slug é único e seguro para nomes de arquivo (validado no catálogo);
        // usar name+version permitia colisão entre slugs diferentes.
        val base = detail.summary.slug
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .ifEmpty { "game" }
        return "$base-${detail.summary.version ?: "v1.0"}.apk"
    }
}
