package com.pixelcraftin.pdfeditorplus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /** Compress an image file to a target quality */
    suspend fun compressImage(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        quality: Int = 75,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): Result<File> = compressImageWithRotation(context, inputUri, outputFile, 0, quality, format)

    /** Compress an image file to a target quality with rotation */
    suspend fun compressImageWithRotation(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        rotationDegrees: Int = 0,
        quality: Int = 75,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("Cannot open input"))
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val rotatedBitmap = if (rotationDegrees % 360 != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val bmp = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                originalBitmap.recycle()
                bmp
            } else {
                originalBitmap
            }

            FileOutputStream(outputFile).use { out ->
                rotatedBitmap.compress(format, quality, out)
            }
            rotatedBitmap.recycle()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Convert image to grayscale */
    suspend fun toGrayscale(inputFile: File, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayscale)
            val paint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap.recycle()
            FileOutputStream(outputFile).use { out ->
                grayscale.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            grayscale.recycle()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Crop image to circle or rounded rectangle preserving aspect ratio (0-90% corner radius) */
    suspend fun cropToRound(
        inputFile: File,
        outputFile: File,
        isCircle: Boolean = true,
        cornerRadiusPercent: Float = 25f,
        format: Bitmap.CompressFormat = getDefaultWebpFormat(),
        quality: Int = 90
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val source = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return@withContext Result.failure(Exception("Unable to decode image file"))
            val w = source.width
            val h = source.height

            val output: Bitmap
            if (isCircle) {
                val size = minOf(w, h)
                output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
                canvas.drawOval(rect, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                val startX = (w - size) / 2
                val startY = (h - size) / 2
                val cropped = Bitmap.createBitmap(source, startX, startY, size, size)
                canvas.drawBitmap(cropped, 0f, 0f, paint)
                cropped.recycle()
            } else {
                // Preserve original aspect ratio (9:16, 16:9, etc.)
                output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                val maxRadius = minOf(w, h) / 2f
                val clampedPercent = cornerRadiusPercent.coerceIn(0f, 90f)
                val radiusPx = maxRadius * (clampedPercent / 100f)
                canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
            source.recycle()

            val finalBitmap = if (format == Bitmap.CompressFormat.JPEG) {
                val rgbBitmap = Bitmap.createBitmap(output.width, output.height, Bitmap.Config.ARGB_8888)
                val rgbCanvas = Canvas(rgbBitmap)
                rgbCanvas.drawColor(Color.WHITE)
                rgbCanvas.drawBitmap(output, 0f, 0f, null)
                output.recycle()
                rgbBitmap
            } else {
                output
            }

            FileOutputStream(outputFile).use { out ->
                finalBitmap.compress(format, quality.coerceIn(1, 100), out)
            }
            finalBitmap.recycle()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns the recommended WEBP lossy format across Android versions */
    fun getDefaultWebpFormat(): Bitmap.CompressFormat {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    /** Load a bitmap from a URI */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}
