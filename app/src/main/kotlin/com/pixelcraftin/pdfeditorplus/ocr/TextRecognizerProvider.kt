package com.pixelcraftin.pdfeditorplus.ocr

/**
 * Provider providing the flavor-specific TextRecognizerEngine instance.
 */
object TextRecognizerProvider {
    val instance: TextRecognizerEngine by lazy {
        TextRecognizerEngineImpl()
    }
}
