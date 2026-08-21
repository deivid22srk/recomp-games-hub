package com.recomp.gameshub.download

import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.domain.model.DownloadPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

enum class SegmentResult {
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED,
}

class DownloadEngine(
    private val repository: DownloadRepository,
    private val client: OkHttpClient,
) {
    private var scope: CoroutineScope? = null
    private val active = mutableSetOf<String>()
    private val maxConcurrent = 2

    fun start(scope: CoroutineScope) {
        if (this.scope === scope) {
            // The service can already be alive when a new task is enqueued. In that
            // case the callback is already bound, but the first drive may have run
            // before the repository StateFlow delivered the task.
            drive()
            return
        }
        this.scope = scope
        repository.bindOnChanged { drive() }
        drive()
    }

    fun stop() {
        scope = null
        repository.bindOnChanged {}
    }

    private fun drive() {
        val scope = scope ?: return
        val pending = repository.currentTasks().filter { it.isActive && it.id !in active }
        val slots = maxConcurrent - active.size
        pending.take(slots).forEach { task ->
            active += task.id
            scope.launch {
                try {
                    runDownload(task.id)
                } finally {
                    active -= task.id
                    drive()
                }
            }
        }
    }

    private suspend fun runDownload(id: String) {
        var task = repository.task(id) ?: return
        while (true) {
            val current = repository.task(id) ?: return
            task = current
            when (current.phase) {
                DownloadPhase.CANCELLED -> {
                    File(current.localPath).delete()
                    repository.delete(id)
                    return
                }
                DownloadPhase.PAUSED -> {
                    delay(300)
                }
                DownloadPhase.COMPLETED -> return
                DownloadPhase.FAILED -> return
                DownloadPhase.PENDING, DownloadPhase.DOWNLOADING -> {
                    if (current.phase == DownloadPhase.PENDING) {
                        repository.setPhase(id, DownloadPhase.DOWNLOADING)
                    }
                    val outcome = downloadSegment(id)
                    when (outcome) {
                        SegmentResult.COMPLETED -> {
                            repository.setPhase(id, DownloadPhase.COMPLETED)
                            return
                        }
                        SegmentResult.PAUSED -> Unit
                        SegmentResult.CANCELLED -> {
                            File(repository.task(id)?.localPath.orEmpty()).delete()
                            repository.delete(id)
                            return
                        }
                        SegmentResult.FAILED -> {
                            repository.setPhase(id, DownloadPhase.FAILED)
                            return
                        }
                    }
                }
            }
        }
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

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            repository.setPhase(id, DownloadPhase.FAILED, friendlyNetworkError(e))
            return@withContext SegmentResult.FAILED
        } catch (e: Exception) {
            repository.setPhase(id, DownloadPhase.FAILED, e.message ?: "Erro inesperado ao conectar")
            return@withContext SegmentResult.FAILED
        }

        response.use { resp ->
            val accepted = resp.isSuccessful || resp.code == 206
            if (!accepted) {
                repository.setPhase(
                    id,
                    DownloadPhase.FAILED,
                    "Servidor respondeu HTTP ${resp.code} (${resp.message})"
                )
                return@withContext SegmentResult.FAILED
            }

            val stream = resp.body?.byteStream()
            if (stream == null) {
                repository.setPhase(id, DownloadPhase.FAILED, "Resposta sem corpo de download")
                return@withContext SegmentResult.FAILED
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
            val total = if (serverTotal > 0L) serverTotal else task.totalBytes
            if (serverTotal > 0L && serverTotal != task.totalBytes) {
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

            while (true) {
                val current = repository.task(id)
                if (current == null) {
                    raf.close()
                    return@withContext SegmentResult.CANCELLED
                }
                if (current.phase == DownloadPhase.PAUSED ||
                    current.phase == DownloadPhase.CANCELLED
                ) {
                    raf.close()
                    repository.updateProgress(id, downloaded, if (total > 0L) total else downloaded, speed, force = true)
                    return@withContext when (current.phase) {
                        DownloadPhase.PAUSED -> SegmentResult.PAUSED
                        else -> SegmentResult.CANCELLED
                    }
                }

                val n = try {
                    stream.read(buffer)
                } catch (e: IOException) {
                    raf.close()
                    repository.setPhase(id, DownloadPhase.FAILED, "Falha durante o download: ${e.message ?: "rede"}")
                    return@withContext SegmentResult.FAILED
                }
                if (n == -1) break

                raf.write(buffer, 0, n)
                downloaded += n

                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastEmit >= 300L) {
                    val elapsed = (now - lastEmit).coerceAtLeast(1L)
                    speed = ((downloaded - lastBytes) * 1000L) / elapsed
                    lastBytes = downloaded
                    lastEmit = now
                    repository.updateProgress(id, downloaded, if (total > 0L) total else downloaded, speed, force = false)
                }
            }

            raf.close()

            repository.updateProgress(id, downloaded, if (total > 0L) total else downloaded, 0L, force = true)

            val finalState = repository.task(id) ?: return@withContext SegmentResult.CANCELLED
            if (finalState.phase == DownloadPhase.CANCELLED) {
                return@withContext SegmentResult.CANCELLED
            }

            if (total > 0L && downloaded != total) {
                repository.setPhase(
                    id,
                    DownloadPhase.FAILED,
                    "Download incompleto (${downloaded}/${total} bytes). O arquivo se corrompeu?"
                )
                return@withContext SegmentResult.FAILED
            }

            SegmentResult.COMPLETED
        }
    }

    private fun parseContentRange(value: String): Long? {
        // Formato: "bytes 0-499/12345"
        val slash = value.indexOf('/')
        if (slash < 0) return null
        val totalText = value.substring(slash + 1).trim()
        val total = totalText.toLongOrNull() ?: return null
        return if (total > 0L) total else null
    }

    private fun friendlyNetworkError(e: IOException): String =
        e.message?.let { msg ->
            if (msg.contains("timeout")) "Tempo esgotado ao conectar ao servidor"
            else if (msg.contains("Unable to resolve")) "Sem conexão com a internet"
            else "Sem conexão com o servidor"
        } ?: "Sem conexão com o servidor"
}
