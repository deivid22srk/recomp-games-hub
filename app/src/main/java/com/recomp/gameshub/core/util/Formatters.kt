package com.recomp.gameshub.core.util

import com.recomp.gameshub.domain.model.DownloadTask
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}

fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0L) "—" else "${formatBytes(bytesPerSec)}/s"

fun formatEtaSimple(task: DownloadTask): String? {
    if (task.totalBytes <= 0L || task.speedBytesPerSec <= 0L) return null
    val remainingMs = (task.remainingBytes * 1000L) / task.speedBytesPerSec
    return formatEta(remainingMs)
}

fun formatEta(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0L -> "~$hours h $minutes min"
        minutes > 0L -> "~$minutes min $seconds s"
        else -> "~$seconds s"
    }
}

fun percentage(task: DownloadTask): Int = (task.progress * 100).toInt().coerceIn(0, 100)