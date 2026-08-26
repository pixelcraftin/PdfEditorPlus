package com.pixelcraftin.pdfeditorplus.ocr

import android.content.Context
import android.graphics.Bitmap
import com.pixelcraftin.pdfeditorplus.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * F-Droid / FOSS implementation using 100% open-source on-device iText 7 text extraction.
 * Completely free of proprietary Google Play Services / GMS dependencies.
 */
class TextRecognizerEngineImpl : TextRecognizerEngine {

    override val engineName: String = "Open-Source PDF Text Extractor"
    override val engineDescription: String = "100% Free & Open-Source on-device PDF text extractor (iText 7)"

    override suspend fun recognizeText(context: Context, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        // Pure FOSS variant does not include proprietary GMS ML Kit binaries
        Result.success("Bitmap OCR is available on the Play edition or requires an external FOSS OCR module.")
    }

    override suspend fun extractTextFromPdf(context: Context, pdfFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Extract digital text natively via iText 7
            val result = PdfUtils.extractText(pdfFile)
            val text = result.getOrNull()?.trim() ?: ""

            if (text.isNotEmpty()) {
                Result.success(text)
            } else {
                Result.success("No digital text layer found in this document. Scanned image PDF OCR requires a text-based document or the Google Play build with ML Kit.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
