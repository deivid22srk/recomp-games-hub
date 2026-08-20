package com.recomp.gameshub.data.repository

import com.recomp.gameshub.data.local.InstalledGameEntity
import com.recomp.gameshub.data.local.InstalledGamesDao
import com.recomp.gameshub.data.local.toDomain
import com.recomp.gameshub.domain.model.InstalledGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstalledGamesRepository(private val dao: InstalledGamesDao) {

    fun observeInstalled(slug: String): Flow<InstalledGame?> =
        dao.observeBySlug(slug).map { entity -> entity?.toDomain() }

    suspend fun remember(
        slug: String,
        packageName: String,
        versionName: String?,
        versionCode: Long,
    ) {
        dao.upsert(
            InstalledGameEntity(
                slug = slug,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}