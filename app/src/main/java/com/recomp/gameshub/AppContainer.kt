package com.recomp.gameshub

import android.content.Context
import androidx.room.Room
import com.recomp.gameshub.data.local.AppDatabase
import com.recomp.gameshub.data.remote.GithubReleaseApi
import com.recomp.gameshub.data.remote.RepoApi
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.data.repository.DownloadRepository
import com.recomp.gameshub.data.repository.InstalledGamesRepository
import com.recomp.gameshub.data.repository.RepoManager
import com.recomp.gameshub.data.repository.RepoRepository
import com.recomp.gameshub.data.repository.SettingsRepository
import com.recomp.gameshub.download.DownloadEngine
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(
    app: Context,
    val appScope: CoroutineScope,
) {
    val appContext: Context = app.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        app,
        AppDatabase::class.java,
        "recomp.db",
    ).fallbackToDestructiveMigration().build()

    val networkClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val repoApi = RepoApi(networkClient)
    val repoRepository = RepoRepository(app)
    val settingsRepository = SettingsRepository(app)
    val githubReleaseApi = GithubReleaseApi(networkClient)

    val downloadsDir: File = File(app.filesDir, "downloads").apply {
        if (!exists()) mkdirs()
    }

    val repoManager = RepoManager(repoRepository, repoApi, database.gameDao())
    val catalogRepository = CatalogRepository(database.gameDao(), repoManager)
    val downloadRepository = DownloadRepository(database.downloadDao(), downloadsDir, appScope)
    val downloadEngine = DownloadEngine(downloadRepository, networkClient)
    val installedGamesRepository = InstalledGamesRepository(database.installedGamesDao())
}
