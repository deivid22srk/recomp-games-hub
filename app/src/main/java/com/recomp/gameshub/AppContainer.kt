package com.recomp.gameshub

import android.content.Context
import androidx.room.Room
import com.recomp.gameshub.data.local.AppDatabase
import com.recomp.gameshub.data.remote.CatalogApi
import com.recomp.gameshub.data.remote.supabase.SupabaseApi
import com.recomp.gameshub.data.repository.AuthRepository
import com.recomp.gameshub.data.repository.CatalogRepository
import com.recomp.gameshub.data.repository.ContributionRepository
import com.recomp.gameshub.data.repository.DownloadRepository
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

    val supabaseApi = SupabaseApi(networkClient)
    val catalogApi = CatalogApi(networkClient)
    val settingsRepository = SettingsRepository(app)
    val authRepository = AuthRepository(app, supabaseApi)
    val catalogRepository = CatalogRepository(supabaseApi, catalogApi, database.gameDao())
    val contributionRepository = ContributionRepository(supabaseApi, authRepository)

    val downloadsDir: File = File(app.filesDir, "downloads").apply {
        if (!exists()) mkdirs()
    }

    val downloadRepository = DownloadRepository(database.downloadDao(), downloadsDir, appScope)
    val downloadEngine = DownloadEngine(downloadRepository, networkClient)
}