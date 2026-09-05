package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import java.util.UUID

data class DrawingPath(
    val path: Path,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val isHighlighter: Boolean = false
)

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var xRatio: Float = 0.5f,
    var yRatio: Float = 0.5f,
    var textColor: Int = android.graphics.Color.BLACK,
    var backgroundColor: Int = android.graphics.Color.parseColor("#FFF9C4"),
    var textSizeSp: Float = 16f
)

data class TextItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var textSize: Float = 24f,
    var textColor: Int = android.graphics.Color.BLACK,
    var x: Float = 0.5f, // Normalized 0..1 relative to page width
    var y: Float = 0.5f, // Normalized 0..1 relative to page height
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var isSelected: Boolean = false
)

data class DocumentPage(
    val uri: Uri,
    var rotationDegrees: Int = 0,
    var filterType: ColorFilterType = ColorFilterType.ORIGINAL,
    val drawingPaths: MutableList<DrawingPath> = mutableListOf(),
    var signatureBitmap: Bitmap? = null,
    var signatureNormRect: RectF? = null,
    var watermarkText: String? = null,
    var watermarkOpacity: Float = 0.3f,
    var watermarkAngle: Float = -35f,
    val textAnnotations: MutableList<TextAnnotation> = mutableListOf(),
    val textItems: MutableList<TextItem> = mutableListOf(),
    var cropRect: RectF? = null,
    var brightnessAdjustment: Float = 0f
) {
    fun rotateClockwise() {
        rotationDegrees = (rotationDegrees + 90) % 360
    }

    fun rotateCounterClockwise() {
        rotationDegrees = (rotationDegrees - 90 + 360) % 360
    }

    fun adjustBrightness(delta: Float) {
        brightnessAdjustment = (brightnessAdjustment + delta).coerceIn(-100f, 100f)
    }

    fun hasModifications(): Boolean {
        return rotationDegrees != 0 ||
                filterType != ColorFilterType.ORIGINAL ||
                brightnessAdjustment != 0f ||
                drawingPaths.isNotEmpty() ||
                signatureBitmap != null ||
                !watermarkText.isNullOrBlank() ||
                textAnnotations.isNotEmpty() ||
                textItems.isNotEmpty() ||
                cropRect != null
    }

    fun clearDrawings() {
        drawingPaths.clear()
    }
}
