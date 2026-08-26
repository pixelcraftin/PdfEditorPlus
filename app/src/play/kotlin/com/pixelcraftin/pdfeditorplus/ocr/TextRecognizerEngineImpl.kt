package com.pixelcraftin.pdfeditorplus.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Google Play implementation using Google Play Services ML Kit On-Device Text Recognition.
 */
class TextRecognizerEngineImpl : TextRecognizerEngine {

    override val engineName: String = "Google Play Services ML Kit"
    override val engineDescription: String = "On-device ML Kit OCR with Google Play Services support"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(context: Context, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        if (continuation.isActive) {
                            continuation.resume(Result.success(visionText.text))
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(exception))
                        }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    override suspend fun extractTextFromPdf(context: Context, pdfFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First attempt: fast native text extraction via iText 7
            val nativeTextResult = PdfUtils.extractText(pdfFile)
            val nativeText = nativeTextResult.getOrNull()?.trim() ?: ""

            if (nativeText.isNotEmpty()) {
                return@withContext Result.success(nativeText)
            }

            // Fallback for scanned image PDFs: Render pages to bitmaps & run ML Kit OCR
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val sb = StringBuilder()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = 2.0f
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageOcrResult = recognizeText(context, bitmap)
                bitmap.recycle()

                val pageText = pageOcrResult.getOrNull()?.trim() ?: ""
                if (pageText.isNotEmpty()) {
                    sb.append("--- Page ${i + 1} ---\n")
                    sb.append(pageText)
                    sb.append("\n\n")
                }
            }

            renderer.close()
            pfd.close()

            val finalText = sb.toString().trim()
            Result.success(if (finalText.isNotEmpty()) finalText else "No text found in document.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
