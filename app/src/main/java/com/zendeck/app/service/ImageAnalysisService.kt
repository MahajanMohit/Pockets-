package com.zendeck.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ImageAnalysisService {

    private const val MAX_CAPTURES_DIR_BYTES = 50L * 1024 * 1024  // 50 MB guard

    /**
     * Copies the source URI into filesDir/captures/<uuid>.png using lossless PNG.
     * PNG is required for screenshots — JPEG introduces ringing artefacts on text edges.
     * Returns null if the captures directory has exceeded 50 MB.
     */
    suspend fun copyAndCompress(context: Context, sourceUri: Uri): File? = withContext(Dispatchers.IO) {
        val capturesDir = File(context.filesDir, "captures").also { it.mkdirs() }

        val dirSize = capturesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (dirSize > MAX_CAPTURES_DIR_BYTES) return@withContext null

        val outFile = File(capturesDir, "${UUID.randomUUID()}.png")

        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return@withContext null

            FileOutputStream(outFile).use { out ->
                original.compress(Bitmap.CompressFormat.PNG, 0, out)
            }
            original.recycle()
            outFile
        } catch (e: Exception) {
            outFile.delete()
            null
        }
    }

    /**
     * Runs MLKit OCR on the image at [imagePath] and returns the extracted text, or "" on failure.
     */
    suspend fun extractText(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext ""
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = suspendCancellableCoroutine<String> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        bitmap.recycle()
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        bitmap.recycle()
                        cont.resumeWithException(e)
                    }
            }
            recognizer.close()
            result
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Derives a short title from OCR text.
     * Strategy: first sentence between 8 and 60 chars; else first 50 chars trimmed.
     */
    fun heuristicTitle(text: String): String {
        if (text.isBlank()) return "Screenshot"
        val firstSentence = text.trimStart().split(Regex("[.!?\n]")).firstOrNull { it.trim().length in 8..60 }
        return firstSentence?.trim() ?: text.trim().take(50)
    }
}
