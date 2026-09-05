package com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

object EdgeDetectionUtils {

    /**
     * Finds the 4 quadrilateral corner points of a document in normalized coordinates (0f..1f).
     * Returns an array of 4 PointF: [TopLeft, TopRight, BottomRight, BottomLeft].
     */
    fun detectDocumentCorners(bitmap: Bitmap): Array<PointF> {
        val sampleSize = 120
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
        } catch (_: Exception) {
            return getDefaultCorners()
        }

        val pixels = IntArray(sampleSize * sampleSize)
        scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        scaled.recycle()

        // 1. Compute grayscale and edge gradient magnitude (Sobel-like gradient)
        val gray = FloatArray(sampleSize * sampleSize)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Corner background luminance average
        val bgLum = (gray[0] + gray[sampleSize - 1] + gray[(sampleSize - 1) * sampleSize] + gray[sampleSize * sampleSize - 1]) / 4f
        val threshold = 22f

        var top = 0
        var bottom = sampleSize - 1
        var left = 0
        var right = sampleSize - 1

        // Scan from Top
        topLoop@ for (y in 0 until sampleSize / 3) {
            for (x in 0 until sampleSize) {
                if (Math.abs(gray[y * sampleSize + x] - bgLum) > threshold) {
                    top = y
                    break@topLoop
                }
            }
        }

        // Scan from Bottom
        botLoop@ for (y in sampleSize - 1 downTo (sampleSize * 2) / 3) {
            for (x in 0 until sampleSize) {
                if (Math.abs(gray[y * sampleSize + x] - bgLum) > threshold) {
                    bottom = y
                    break@botLoop
                }
            }
        }

        // Scan from Left
        leftLoop@ for (x in 0 until sampleSize / 3) {
            for (y in 0 until sampleSize) {
                if (Math.abs(gray[y * sampleSize + x] - bgLum) > threshold) {
                    left = x
                    break@leftLoop
                }
            }
        }

        // Scan from Right
        rightLoop@ for (x in sampleSize - 1 downTo (sampleSize * 2) / 3) {
            for (y in 0 until sampleSize) {
                if (Math.abs(gray[y * sampleSize + x] - bgLum) > threshold) {
                    right = x
                    break@rightLoop
                }
            }
        }

        val normLeft = (left.toFloat() / sampleSize).coerceIn(0.03f, 0.35f)
        val normTop = (top.toFloat() / sampleSize).coerceIn(0.03f, 0.35f)
        val normRight = (right.toFloat() / sampleSize).coerceIn(0.65f, 0.97f)
        val normBottom = (bottom.toFloat() / sampleSize).coerceIn(0.65f, 0.97f)

        // If detection is valid, construct 4 quadrilateral corners
        if (normRight - normLeft >= 0.35f && normBottom - normTop >= 0.35f) {
            return arrayOf(
                PointF(normLeft, normTop),       // TopLeft
                PointF(normRight, normTop),      // TopRight
                PointF(normRight, normBottom),   // BottomRight
                PointF(normLeft, normBottom)     // BottomLeft
            )
        }

        return getDefaultCorners()
    }

    /**
     * Default fallback: 90% inset rectangle.
     */
    fun getDefaultCorners(): Array<PointF> {
        return arrayOf(
            PointF(0.05f, 0.05f),
            PointF(0.95f, 0.05f),
            PointF(0.95f, 0.95f),
            PointF(0.05f, 0.95f)
        )
    }

    /**
     * Performs a perspective warp & crop from 4 polygon corners onto a rectangular target bitmap.
     */
    fun cropPerspective(
        source: Bitmap,
        corners: Array<PointF>,
        rotationDegrees: Int = 0
    ): Bitmap {
        val srcBmp = if (rotationDegrees != 0) {
            val rotMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotBmp = Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotMatrix, true)
            rotBmp
        } else {
            source
        }

        val w = srcBmp.width.toFloat()
        val h = srcBmp.height.toFloat()

        val tl = PointF(corners[0].x * w, corners[0].y * h)
        val tr = PointF(corners[1].x * w, corners[1].y * h)
        val br = PointF(corners[2].x * w, corners[2].y * h)
        val bl = PointF(corners[3].x * w, corners[3].y * h)

        // Calculate target dimensions
        val widthTop = Math.hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = Math.hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val targetWidth = maxOf(widthTop, widthBottom).toInt().coerceIn(100, 4000)

        val heightLeft = Math.hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = Math.hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val targetHeight = maxOf(heightLeft, heightRight).toInt().coerceIn(100, 4000)

        val srcPoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBmp, matrix, paint)

        if (srcBmp != source) {
            srcBmp.recycle()
        }

        return outputBitmap
    }
}
