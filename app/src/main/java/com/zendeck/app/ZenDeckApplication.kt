package com.zendeck.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zendeck.app.worker.TTLWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class ZenDeckApplication : Application() {

    val dataStore: DataStore<Preferences> by preferencesDataStore(name = "zendeck_settings")

    /** Long-lived scope for background work that must outlive any Activity (e.g. ShareActivity). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scheduleTTLWorker()
    }

    private fun scheduleTTLWorker() {
        val request = PeriodicWorkRequestBuilder<TTLWorker>(6, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TTLWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
