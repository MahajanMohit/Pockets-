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
 * Enqueues an Android DownloadManager download for the Gemma 4 E2B model and polls
 * until it completes or fails. WorkManager keeps this alive across process death.
 *
 * The model lands in [Context.getExternalFilesDir]("models") which
 * [LlmSummarizationService.discoverModels] already searches.
 *
 * Supported models (LiteRT-LM .litertlm format):
 *   Gemma 4 E2B (~2.6 GB): gemma-4-E2B-it.litertlm  — default, recommended for most devices
 *   Gemma 4 E4B (~4.3 GB): gemma-4-E4B-it.litertlm  — higher quality, needs ~8 GB RAM
 *
 * HuggingFace repos:
 *   E2B: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
 *   E4B: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
 *
 * NOTE: HuggingFace requires accepting the Gemma Terms of Use before direct download works.
 * Replace MODEL_URL with the authenticated download link from HuggingFace or Kaggle.
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
            .setDescription("Downloading Gemma 4 E2B model for AI summaries (≈2.6 GB)…")
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
         * Gemma 4 E2B in LiteRT-LM format (~2.6 GB).
         * Requires accepting Gemma Terms of Use at:
         *   https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
         * Replace MODEL_URL with the authenticated download link after accepting the license.
         */
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

        /** Anything smaller than this is a corrupt/incomplete download. */
        const val MIN_VALID_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB sanity floor

        private const val POLL_INTERVAL_MS = 3_000L
    }
}
