package com.recomp.gameshub.domain.model

data class InstalledGame(
    val slug: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val updatedAt: Long,
)

sealed interface GameInstallState {
    data object Unknown : GameInstallState

    data class NotInstalled(val packageName: String) : GameInstallState

    data class Installed(
        val packageName: String,
        val installedVersionName: String?,
        val installedVersionCode: Long,
        val latestVersion: String?,
        val isUpToDate: Boolean,
    ) : GameInstallState {
        val displayVersion: String
            get() = installedVersionName?.takeIf { it.isNotBlank() } ?: "v$installedVersionCode"
    }
}

object AppVersions {
    fun compare(installed: String?, target: String?): Int {
        if (installed.isNullOrBlank() || target.isNullOrBlank()) return 0
        val clean = { value: String ->
            value.lowercase().trim().filter { it.isDigit() || it == '.' }
        }
        val a = numericSegments(clean(installed))
        val b = numericSegments(clean(target))
        if (a != null && b != null) {
            val len = maxOf(a.size, b.size)
            val paddedA = a + List(len - a.size) { 0L }
            val paddedB = b + List(len - b.size) { 0L }
            for (i in 0 until len) {
                if (paddedA[i] != paddedB[i]) return paddedA[i].compareTo(paddedB[i])
            }
            return 0
        }
        val aNorm = installed.lowercase().trim().filter { it.isLetterOrDigit() }
        val bNorm = target.lowercase().trim().filter { it.isLetterOrDigit() }
        if (aNorm == bNorm) return 0
        if (installed.any { it.isDigit() } && target.any { it.isDigit() }) return -1
        return 0
    }

    fun isOutdated(installed: String?, target: String?): Boolean =
        !installed.isNullOrBlank() && !target.isNullOrBlank() && compare(installed, target) < 0

    fun isSameVersion(a: String?, b: String?): Boolean =
        compare(a, b) == 0

    /**
     * Derives a comparable numeric code from a version name like "1.2.3"
     * (major*1_000_000 + minor*1_000 + patch), used to order app releases.
     */
    fun versionCodeFromName(versionName: String): Int {
        val parts = versionName.trim().split('.')
            .map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }
            .take(3)
        val padded = parts + List(3 - parts.size) { 0L }
        return (padded[0] * 1_000_000L + padded[1] * 1_000L + padded[2])
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
    }

    private fun numericSegments(value: String): List<Long>? {
        val parsed = value.split('.').mapNotNull { it.toLongOrNull() }
        return parsed.takeIf { it.isNotEmpty() }
    }
}