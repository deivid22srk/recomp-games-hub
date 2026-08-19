package com.recomp.gameshub

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.recomp.gameshub.download.ResumeDownloadsWorker

object ResumeWorkScheduler {
    private const val WORK_NAME = "resume-downloads"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<ResumeDownloadsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}