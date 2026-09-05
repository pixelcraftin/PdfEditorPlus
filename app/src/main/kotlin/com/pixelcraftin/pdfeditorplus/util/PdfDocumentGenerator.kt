package com.pixelcraftin.pdfeditorplus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.pixelcraftin.pdfeditorplus.ui.documenteditor.DocumentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfDocumentGenerator {

    // Standard A4 dimensions in PostScript points (72 DPI)
    const val A4_WIDTH_PTS = 595
    const val A4_HEIGHT_PTS = 842

    // High-resolution 300 DPI dimensions for standard A4 page (2480 x 3508 px)
    const val MAX_A4_WIDTH_PX = 2480
    const val MAX_A4_HEIGHT_PX = 3508

    /**
     * Generates a high-DPI (300 DPI), size-optimized, OOM-safe PDF from edited DocumentPages.
     * Preserves diagram/text sharpness with anti-aliasing and immediate bitmap recycling.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<DocumentPage>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        var pdfDocument: PdfDocument? = null
        try {
            pdfDocument = PdfDocument()
            val total = pages.size

            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, total)

                // 1. Decode high-resolution bitmap from URI
                val sourceBitmap = decodeSampledBitmapFromUri(context, page, MAX_A4_WIDTH_PX, MAX_A4_HEIGHT_PX)
                    ?: continue

                // 2. Create intermediate page canvas at 300 DPI A4 pixel dimensions
                val pageBitmap = Bitmap.createBitmap(MAX_A4_WIDTH_PX, MAX_A4_HEIGHT_PX, Bitmap.Config.ARGB_8888)
                val pageCanvas = Canvas(pageBitmap)
                pageCanvas.drawColor(Color.WHITE)

                // 3. Render base image with ColorMatrix filter, rotation, drawings, signature, watermark, text
                renderPageToCanvas(pageCanvas, MAX_A4_WIDTH_PX.toFloat(), MAX_A4_HEIGHT_PX.toFloat(), sourceBitmap, page)
                sourceBitmap.recycle()

                // 4. Compress rendered bitmap to JPEG format at 88% quality (crisp text & diagrams without excessive bloating)
                val jpegStream = ByteArrayOutputStream()
                pageBitmap.compress(Bitmap.CompressFormat.JPEG, 88, jpegStream)
                pageBitmap.recycle()

                // 5. Decode compressed JPEG and draw onto native PdfDocument.Page (A4 size)
                val compressedBitmap = BitmapFactory.decodeByteArray(jpegStream.toByteArray(), 0, jpegStream.size())
                jpegStream.close()

                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PTS, A4_HEIGHT_PTS, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }
                val destRect = RectF(0f, 0f, A4_WIDTH_PTS.toFloat(), A4_HEIGHT_PTS.toFloat())
                pdfPage.canvas.drawBitmap(compressedBitmap, null, destRect, paint)

                pdfDocument.finishPage(pdfPage)

                // 6. IMMEDIATELY recycle bitmap per page to prevent OOM
                compressedBitmap.recycle()
            }

            // Write PDF to output file
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pdfDocument?.close()
        }
    }

    /**
     * Efficiently decodes and downsamples a bitmap from a content URI without loading full image into memory.
     */
    fun decodeSampledBitmapFromUri(
        context: Context,
        page: DocumentPage,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(page.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // Decode bitmap with inSampleSize set
            context.contentResolver.openInputStream(page.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Renders edited page components onto target canvas.
     */
    private fun renderPageToCanvas(
        targetCanvas: Canvas,
        targetWidth: Float,
        targetHeight: Float,
        sourceBitmap: Bitmap,
        page: DocumentPage
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        paint.colorFilter = page.filterType.getColorFilter(page.brightnessAdjustment)

        val rot = (page.rotationDegrees % 360 + 360) % 360
        val isRotated = rot == 90 || rot == 270
        val bmpW = if (isRotated) sourceBitmap.height.toFloat() else sourceBitmap.width.toFloat()
        val bmpH = if (isRotated) sourceBitmap.width.toFloat() else sourceBitmap.height.toFloat()

        val scale = minOf(targetWidth / bmpW, targetHeight / bmpH)
        val destW = bmpW * scale
        val destH = bmpH * scale
        val left = (targetWidth - destW) / 2f
        val top = (targetHeight - destH) / 2f
        val pageRect = RectF(left, top, left + destW, top + destH)

        // Draw rotated base bitmap
        targetCanvas.save()
        val cx = pageRect.centerX()
        val cy = pageRect.centerY()
        if (rot != 0) {
            targetCanvas.rotate(rot.toFloat(), cx, cy)
        }
        val drawRect = if (isRotated) {
            val s = minOf(targetWidth / sourceBitmap.height, targetHeight / sourceBitmap.width)
            val w = sourceBitmap.width * s
            val h = sourceBitmap.height * s
            RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        } else {
            pageRect
        }
        targetCanvas.drawBitmap(sourceBitmap, null, drawRect, paint)
        targetCanvas.restore()

        // Draw drawings, signatures, watermarks
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (dp in page.drawingPaths) {
            drawPaint.reset()
            drawPaint.isAntiAlias = true
            drawPaint.style = Paint.Style.STROKE
            drawPaint.strokeCap = Paint.Cap.ROUND
            drawPaint.strokeJoin = Paint.Join.ROUND
            drawPaint.strokeWidth = dp.strokeWidth * (targetWidth / 1080f).coerceAtLeast(1f)
            when {
                dp.isEraser -> {
                    drawPaint.color = Color.WHITE
                    drawPaint.alpha = 255
                }
                dp.isHighlighter -> {
                    drawPaint.color = dp.color
                    drawPaint.alpha = 110
                }
                else -> {
                    drawPaint.color = dp.color
                    drawPaint.alpha = 255
                }
            }
            targetCanvas.drawPath(dp.path, drawPaint)
        }

        // Draw watermark
        page.watermarkText?.takeIf { it.isNotBlank() }?.let { text ->
            targetCanvas.save()
            targetCanvas.rotate(page.watermarkAngle, cx, cy)
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textSize = minOf(pageRect.width(), pageRect.height()) * 0.12f
                color = Color.DKGRAY
                alpha = (page.watermarkOpacity.coerceIn(0.05f, 1f) * 255).toInt()
            }
            targetCanvas.drawText(text, cx, cy + wmPaint.textSize / 3f, wmPaint)
            targetCanvas.restore()
        }

        // Draw signature
        val sBmp = page.signatureBitmap
        val sRect = page.signatureNormRect
        if (sBmp != null && sRect != null && !sBmp.isRecycled) {
            val destSig = RectF(
                pageRect.left + sRect.left * pageRect.width(),
                pageRect.top + sRect.top * pageRect.height(),
                pageRect.left + sRect.right * pageRect.width(),
                pageRect.top + sRect.bottom * pageRect.height()
            )
            targetCanvas.drawBitmap(sBmp, null, destSig, paint)
        }

        // Draw text notes
        val textNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val textBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#E0E0E0")
        }

        for (note in page.textAnnotations) {
            val px = pageRect.left + note.xRatio * pageRect.width()
            val py = pageRect.top + note.yRatio * pageRect.height()
            textNotePaint.color = note.textColor
            val textWidth = textNotePaint.measureText(note.text)
            val textHeight = 40f
            val padding = 16f
            val bgRect = RectF(
                px - textWidth / 2f - padding,
                py - textHeight - padding,
                px + textWidth / 2f + padding,
                py + padding
            )
            textBgPaint.color = note.backgroundColor
            targetCanvas.drawRoundRect(bgRect, 14f, 14f, textBgPaint)
            targetCanvas.drawRoundRect(bgRect, 14f, 14f, textBorderPaint)
            targetCanvas.drawText(note.text, px - textWidth / 2f, py - 4f, textNotePaint)
        }

        // Draw interactive text sticker items
        val textItemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        for (item in page.textItems) {
            val px = pageRect.left + item.x * pageRect.width()
            val py = pageRect.top + item.y * pageRect.height()
            val scaleFactor = (pageRect.width() / 1000f) * item.scale
            textItemPaint.textSize = item.textSize * scaleFactor
            textItemPaint.color = item.textColor

            targetCanvas.save()
            targetCanvas.rotate(item.rotation, px, py)
            val textWidth = textItemPaint.measureText(item.text)
            targetCanvas.drawText(item.text, px - textWidth / 2f, py + textItemPaint.textSize / 3f, textItemPaint)
            targetCanvas.restore()
        }
    }
}
