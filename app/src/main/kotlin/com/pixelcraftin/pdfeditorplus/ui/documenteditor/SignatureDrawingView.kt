package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignatureDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var drawPath = Path()
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
    }

    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private var lastX = 0f
    private var lastY = 0f
    private var hasContent = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drawPath.reset()
                drawPath.moveTo(x, y)
                lastX = x
                lastY = y
                hasContent = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(x - lastX)
                val dy = Math.abs(y - lastY)
                if (dx >= 2 || dy >= 2) {
                    drawPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawCanvas?.drawPath(drawPath, drawPaint)
                drawPath.reset()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clear() {
        hasContent = false
        drawPath.reset()
        canvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun isEmpty(): Boolean = !hasContent

    fun getSignatureBitmap(): Bitmap? {
        val bmp = canvasBitmap ?: return null
        if (!hasContent) return null

        // Crop transparent borders
        return try {
            val width = bmp.width
            val height = bmp.height
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0

            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val alpha = (pixels[y * width + x] ushr 24) and 0xff
                    if (alpha > 10) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (maxX > minX && maxY > minY) {
                val padding = 16
                val cropLeft = (minX - padding).coerceAtLeast(0)
                val cropTop = (minY - padding).coerceAtLeast(0)
                val cropWidth = (maxX - cropLeft + padding).coerceAtMost(width - cropLeft)
                val cropHeight = (maxY - cropTop + padding).coerceAtMost(height - cropTop)

                Bitmap.createBitmap(bmp, cropLeft, cropTop, cropWidth, cropHeight)
            } else {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            }
        } catch (_: Exception) {
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
}
