package com.recomp.gameshub.download

import com.recomp.gameshub.data.remote.await
import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.domain.model.DownloadPhase
import com.recomp.gameshub.domain.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

enum class SegmentResult {
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED,
}

/**
 * Executa os downloads respeitando um limite de concorrência.
 *
 * Cada task PENDING ocupa um slot até terminar (completar, falhar, ser
 * pausada ou cancelada). Uma task PAUSED libera o slot imediatamente —
 * o método [DownloadRepository.resume] devolve a task para PENDING e
 * dispara um novo [drive] pelo callback onChanged.
 *
 * O conjunto de tasks ativas é protegido por lock, pois [drive] pode ser
 * disparado tanto da thread principal quanto de IO (via setPhase).
 */
class DownloadEngine(
    private val repository: DownloadRepository,
    private val client: OkHttpClient,
) {
    @Volatile
    private var scope: CoroutineScope? = null
    private val lock = Any()
    private val active = mutableSetOf<String>()
    private val maxConcurrent = 2

    /** Chamadas em voo por task; permite reagir a pausa/cancel na hora. */
    private val inFlight = ConcurrentHashMap<String, Call>()

    fun start(scope: CoroutineScope) {
        if (this.scope === scope) {
            // O serviço pode já estar vivo quando uma nova task é enfileirada.
            // O callback já está ligado, mas o primeiro drive pode ter rodado
            // antes do StateFlow do repositório entregar a task.
            drive()
            return
        }
        this.scope = scope
        repository.bindOnChanged { drive() }
        drive()
    }

    fun stop() {
        synchronized(lock) {
            scope = null
            active.clear()
        }
        inFlight.values.forEach { runCatching { it.cancel() } }
        inFlight.clear()
        repository.bindOnChanged {}
    }

    private fun drive() {
        val currentScope = scope ?: return
        // Cancela transferências em voo que não correspondem mais a uma task
        // em DOWNLOADING (pausada, cancelada, removida ou já concluída): faz
        // pausa/cancel reagirem na hora, mesmo com a conexão parada esperando
        // read timeout.
        val downloadingIds = repository.currentTasks()
            .filter { it.phase == DownloadPhase.DOWNLOADING }
            .map { it.id }
            .toSet()
        inFlight.keys.filter { it !in downloadingIds }.toList().forEach { id ->
            inFlight.remove(id)?.cancel()
        }
        val toStart: List<DownloadTask> = synchronized(lock) {
            if (scope == null) return
            val slots = (maxConcurrent - active.size).coerceAtLeast(0)
            repository.currentTasks()
                .filter { it.phase == DownloadPhase.PENDING && it.id !in active }
                .take(slots)
                .also { candidates -> active.addAll(candidates.map { it.id }) }
        }
        toStart.forEach { task ->
            currentScope.launch {
                try {
                    runDownload(task.id)
                } finally {
                    synchronized(lock) { active.remove(task.id) }
                    drive()
                }
            }
        }
    }

    private suspend fun runDownload(id: String) {
        val task = repository.task(id) ?: return
        if (task.phase != DownloadPhase.PENDING) return
        repository.setPhase(id, DownloadPhase.DOWNLOADING)
        val outcome = downloadSegment(id)
        if (outcome == SegmentResult.COMPLETED) {
            repository.setPhase(id, DownloadPhase.COMPLETED)
        }
        // PAUSED/CANCELLED/FAILED já tiveram a fase definida pelo repositório
        // ou por downloadSegment; o slot é liberado pelo finally em drive().
    }

    private suspend fun downloadSegment(id: String): SegmentResult = withContext(Dispatchers.IO) {
        val task = repository.task(id) ?: return@withContext SegmentResult.CANCELLED
        val file = File(task.localPath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        var start = task.downloadedBytes.coerceAtLeast(0L)

        val requestBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", "RecompHub/1.0 (Android)")
        if (start > 0L) {
            requestBuilder.header("Range", "bytes=$start-")
        }
        val request = requestBuilder.build()

        val call = client.newCall(request)
        inFlight[id] = call
        try {
            return@withContext executeSegment(id, call, file, start)
        } finally {
            inFlight.remove(id)
        }
    }

    private suspend fun executeSegment(
        id: String,
        call: Call,
        file: File,
        initialStart: Long,
    ): SegmentResult {
        var start = initialStart
        val response = try {
            call.await()
        } catch (e: IOException) {
            if (!isActive) return SegmentResult.CANCELLED
            val current = repository.task(id)
            when {
                current == null -> return SegmentResult.CANCELLED
                current.phase == DownloadPhase.PAUSED -> return SegmentResult.PAUSED
                else -> {
                    repository.setPhase(id, DownloadPhase.FAILED, friendlyNetworkError(e))
                    return SegmentResult.FAILED
                }
            }
        } catch (e: Exception) {
            if (!isActive) return SegmentResult.CANCELLED
            repository.setPhase(id, DownloadPhase.FAILED, e.message ?: "Erro inesperado ao conectar")
            return SegmentResult.FAILED
        }

        response.use { resp ->
            val accepted = resp.isSuccessful || resp.code == 206
            if (!accepted) {
                repository.setPhase(
                    id,
                    DownloadPhase.FAILED,
                    "Servidor respondeu HTTP ${resp.code} (${resp.message})"
                )
                return SegmentResult.FAILED
            }

            val stream = resp.body?.byteStream()
            if (stream == null) {
                repository.setPhase(id, DownloadPhase.FAILED, "Resposta sem corpo de download")
                return SegmentResult.FAILED
            }

            val isResumed = resp.code == 206
            // O tamanho real vem sempre do servidor: o catálogo pode descrever outra
            // versão do jogo, o que fazia o progresso estourar 100% e falhar no fim
            // ao baixar versões antigas.
            val contentRangeTotal = resp.header("Content-Range")?.let { parseContentRange(it) }
            val contentLength = resp.body?.contentLength() ?: -1L
            val serverTotal = contentRangeTotal ?: when {
                !isResumed && contentLength > 0L -> contentLength
                isResumed && contentLength > 0L -> start + contentLength
                else -> -1L
            }
            // total = 0 significa "tamanho desconhecido": a UI mostra progresso
            // indeterminado em vez de um 100% falso.
            val total = if (serverTotal > 0L) serverTotal else taskTotal(id)
            if (serverTotal > 0L && serverTotal != taskTotal(id)) {
                repository.resolveTotal(id, serverTotal)
            }

            val raf = RandomAccessFile(file, "rw")
            if (isResumed && start > 0L) {
                raf.seek(start)
            } else {
                raf.setLength(0L)
                raf.seek(0L)
                start = 0L
            }

            val buffer = ByteArray(64 * 1024)
            var downloaded = start
            var lastBytes = start
            var lastEmit = android.os.SystemClock.elapsedRealtime()
            var speed = 0L

            try {
                while (true) {
                    if (!isActive) {
                        call.cancel()
                        return SegmentResult.CANCELLED
                    }
                    val current = repository.task(id)
                    if (current == null) {
                        return SegmentResult.CANCELLED
                    }
                    if (current.phase == DownloadPhase.PAUSED) {
                        repository.updateProgress(id, downloaded, total, speed, force = true)
                        return SegmentResult.PAUSED
                    }

                    val n = try {
                        stream.read(buffer)
                    } catch (e: IOException) {
                        if (!isActive) return SegmentResult.CANCELLED
                        val state = repository.task(id)
                        when {
                            state == null -> return SegmentResult.CANCELLED
                            state.phase == DownloadPhase.PAUSED -> return SegmentResult.PAUSED
                            else -> {
                                repository.setPhase(id, DownloadPhase.FAILED, "Falha durante o download: ${e.message ?: "rede"}")
                                return SegmentResult.FAILED
                            }
                        }
                    }
                    if (n == -1) break

                    try {
                        raf.write(buffer, 0, n)
                    } catch (e: IOException) {
                        repository.setPhase(
                            id,
                            DownloadPhase.FAILED,
                            "Falha ao gravar o arquivo (armazenamento cheio?): ${e.message ?: "erro de E/S"}"
                        )
                        return SegmentResult.FAILED
                    }
                    downloaded += n

                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastEmit >= 300L) {
                        val elapsed = (now - lastEmit).coerceAtLeast(1L)
                        speed = ((downloaded - lastBytes) * 1000L) / elapsed
                        lastBytes = downloaded
                        lastEmit = now
                        repository.updateProgress(id, downloaded, total, speed, force = false)
                    }
                }
            } finally {
                runCatching { raf.close() }
                runCatching { stream.close() }
            }

            repository.updateProgress(id, downloaded, total, 0L, force = true)

            val finalState = repository.task(id) ?: return SegmentResult.CANCELLED

            if (total > 0L && downloaded != total) {
                repository.setPhase(
                    id,
                    DownloadPhase.FAILED,
                    "Download incompleto (${downloaded}/${total} bytes). O arquivo se corrompeu?"
                )
                return SegmentResult.FAILED
            }

            SegmentResult.COMPLETED
        }
    }

    private fun taskTotal(id: String): Long = repository.task(id)?.totalBytes ?: 0L

    private fun parseContentRange(value: String): Long? {
        // Formato: "bytes 0-499/12345"
        val slash = value.indexOf('/')
        if (slash < 0) return null
        val totalText = value.substring(slash + 1).trim()
        val total = totalText.toLongOrNull() ?: return null
        return if (total > 0L) total else null
    }

    private fun friendlyNetworkError(e: IOException): String {
        val message = e.message?.lowercase()
        return when {
            // SocketTimeoutException cobre connect/read/call timeout: a mensagem
            // deve falar de conexão lenta, não apenas de "conectar".
            e is java.net.SocketTimeoutException ->
                "A conexão demorou demais (timeout). Tente novamente."
            message?.contains("unable to resolve") == true -> "Sem conexão com a internet"
            message?.contains("timeout") == true ->
                "A conexão demorou demais (timeout). Tente novamente."
            else -> "Sem conexão com o servidor"
        }
    }
}
