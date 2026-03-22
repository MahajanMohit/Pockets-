package com.zendeck.app.worker

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.io.File

/**
 * Enqueues an Android DownloadManager download for the Gemma model and polls
 * until it completes or fails. WorkManager keeps this alive across process death.
 *
 * The model lands in [Context.getExternalFilesDir]("models") which
 * [LlmSummarizationService.findModelPath] already searches.
 *
 * NOTE: Update [MODEL_URL] to the correct Kaggle / HuggingFace direct-download
 * URL once you have confirmed the license-accepted download link for your users.
 * Kaggle URL: https://www.kaggle.com/models/google/gemma/frameworks/tfLite/variations/gemma-2b-it-cpu-int4
 */
class ModelDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: MODEL_URL
        val destDir = appContext.getExternalFilesDir("models") ?: appContext.filesDir
        destDir.mkdirs()
        val destFile = File(destDir, MODEL_FILENAME)

        // Already downloaded — nothing to do
        if (destFile.exists() && destFile.length() > MIN_VALID_SIZE_BYTES) {
            Log.i(TAG, "Model already present: ${destFile.absolutePath}")
            return Result.success()
        }
        destFile.delete() // remove any incomplete previous attempt

        val dm = appContext.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ZenDeck AI Model")
            .setDescription("Downloading Gemma model for AI summaries (≈1.1 GB)…")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setRequiresCharging(false)

        val downloadId = dm.enqueue(request)
        Log.i(TAG, "DownloadManager enqueued id=$downloadId → ${destFile.absolutePath}")

        // Poll until DownloadManager reports success or failure
        while (true) {
            delay(POLL_INTERVAL_MS)
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
            val (status, reason, downloaded, total) = cursor?.use { c ->
                if (!c.moveToFirst()) return@use null
                listOf(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            } ?: return Result.failure(workDataOf(KEY_ERROR to "Download record lost"))

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.i(TAG, "Model downloaded successfully: ${destFile.absolutePath}")
                    return Result.success()
                }
                DownloadManager.STATUS_FAILED -> {
                    Log.e(TAG, "Download failed: reason=$reason")
                    destFile.delete()
                    return Result.failure(workDataOf(KEY_ERROR to "DownloadManager failed (reason=$reason)"))
                }
                DownloadManager.STATUS_RUNNING -> {
                    if (total > 0) {
                        val pct = (downloaded * 100L / total).toInt()
                        Log.d(TAG, "Downloading model… $pct% ($downloaded / $total bytes)")
                    }
                }
                else -> { /* PENDING / PAUSED — keep waiting */ }
            }
        }
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        const val WORK_NAME = "GemmaModelDownload"
        const val KEY_URL   = "model_url"
        const val KEY_ERROR = "error"

        /**
         * The Gemma 2B CPU-int4 model in MediaPipe TFLite format (~1.1 GB).
         * Replace with the Kaggle direct-download URL after the user has accepted
         * the Gemma terms of service on https://www.kaggle.com/models/google/gemma
         */
        const val MODEL_URL = "https://huggingface.co/google/gemma-2b-it-mediapipe/resolve/main/gemma-2b-it-cpu-int4.bin"
        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"

        /** Anything smaller than this is a corrupt/incomplete download. */
        const val MIN_VALID_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB sanity floor

        private const val POLL_INTERVAL_MS = 3_000L
    }
}
