package com.zendeck.app.worker

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.widget.ZenDeckWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TTLWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repo = LinkRepository.getInstance(appContext)
            // Archive inbox items whose TTL has expired
            repo.archiveExpired()
            // Delete archived items that have also exceeded the same TTL since archiving
            val ttlHours = getTtlHours()
            repo.deleteExpiredArchived(ttlHours)
            // Refresh the home screen widget
            ZenDeckWidget().updateAll(appContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun getTtlHours(): Long = try {
        (appContext as ZenDeckApplication).dataStore.data
            .map { it[longPreferencesKey("ttl_hours")] ?: 72L }
            .first()
    } catch (e: Exception) {
        72L
    }

    companion object {
        const val WORK_NAME = "ZenDeckTTLWorker"
    }
}
