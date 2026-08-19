package com.recomp.gameshub

import android.app.Application
import com.recomp.gameshub.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecompApplication : Application() {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        appScope.launch {
            container.downloadRepository.restoreInterrupted()
        }
        ResumeWorkScheduler.schedule(this)
    }
}