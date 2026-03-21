package com.zendeck.app.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.widget.ZenDeckWidget

class TTLWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repo = LinkRepository.getInstance(appContext)
            repo.archiveExpired()
            // Refresh the home screen widget
            ZenDeckWidget().updateAll(appContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ZenDeckTTLWorker"
    }
}
