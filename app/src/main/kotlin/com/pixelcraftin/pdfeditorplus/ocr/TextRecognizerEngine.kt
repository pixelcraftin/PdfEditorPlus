package com.pixelcraftin.pdfeditorplus.ocr

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * Common abstraction for OCR and text recognition across product flavors.
 */
interface TextRecognizerEngine {
    /**
     * Recognize text from a bitmap image.
     */
    suspend fun recognizeText(context: Context, bitmap: Bitmap): Result<String>

    /**
     * Extract or OCR text from a PDF file.
     */
    suspend fun extractTextFromPdf(context: Context, pdfFile: File): Result<String>

    /**
     * Human-readable engine name (e.g., "ML Kit Play Services" vs "iText Native Text Extractor").
     */
    val engineName: String

    /**
     * Description of the engine for about / open source dialog.
     */
    val engineDescription: String
}
