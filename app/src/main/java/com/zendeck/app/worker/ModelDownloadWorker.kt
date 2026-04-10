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
 * Enqueues an Android DownloadManager download for the Gemma 3n E2B model and polls
 * until it completes or fails. WorkManager keeps this alive across process death.
 *
 * The model lands in [Context.getExternalFilesDir]("models") which
 * [LlmSummarizationService.findModelPath] already searches.
 *
 * Supported models:
 *   Gemma 3n E2B (~1.5 GB): gemma3n-E2B-it-int4.task  — default, recommended for most devices
 *   Gemma 3n E4B (~2.5 GB): gemma3n-E4B-it-int4.task  — higher quality, needs ~6 GB RAM
 *
 * NOTE: HuggingFace requires accepting the Gemma Terms of Use before direct download works.
 * Kaggle: https://www.kaggle.com/models/google/gemma-3n/frameworks/litert
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
            .setTitle("AI Link Triage – AI Model")
            .setDescription("Downloading Gemma 3n E2B model for AI summaries (≈1.5 GB)…")
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
            // Use explicit typed variables to avoid Kotlin 2.0 type-inference issues
            // that arise when mixing Int and Long inside a listOf() and then destructuring.
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                ?: return Result.failure(workDataOf(KEY_ERROR to "Download record lost"))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return Result.failure(workDataOf(KEY_ERROR to "Download record lost"))
            }
            var status = 0
            var reason = 0
            var downloaded = 0L
            var total = 0L
            cursor.use { c ->
                status   = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                reason   = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                total      = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            }

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
                    if (total > 0L) {
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
         * Gemma 3n E2B on-device model in LiteRT (.task) format (~1.5 GB).
         * Download requires accepting the Gemma Terms of Use on Kaggle:
         *   https://www.kaggle.com/models/google/gemma-3n/frameworks/litert
         * Replace MODEL_URL with the authenticated direct-download link from Kaggle
         * or HuggingFace (https://huggingface.co/google/gemma-3n-E2B-it-litert-preview).
         *
         * E4B variant (best quality, ~2.5 GB):
         *   filename: gemma3n-E4B-it-int4.task
         *   HuggingFace: https://huggingface.co/google/gemma-3n-E4B-it-litert-preview
         */
        const val MODEL_URL = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma3n-E2B-it-int4.task"
        const val MODEL_FILENAME = "gemma3n-E2B-it-int4.task"

        /** Anything smaller than this is a corrupt/incomplete download. */
        const val MIN_VALID_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB sanity floor

        private const val POLL_INTERVAL_MS = 3_000L
    }
}
