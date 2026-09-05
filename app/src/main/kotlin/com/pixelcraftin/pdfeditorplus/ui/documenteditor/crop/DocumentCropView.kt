package com.pixelcraftin.pdfeditorplus.ui.documenteditor.crop

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DocumentCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    var rotationAngle: Int = 0
        set(value) {
            field = (value % 360 + 360) % 360
            computeImageRect()
            autoDetectCropBounds()
            invalidate()
        }

    // Image destination rectangle in view
    private val imageRect = RectF()

    // 4 normalized quadrilateral corner points (0f..1f relative to imageRect)
    // [0] = TopLeft, [1] = TopRight, [2] = BottomRight, [3] = BottomLeft
    private var corners: Array<PointF> = EdgeDetectionUtils.getDefaultCorners()

    // Touch handle enum
    private enum class TouchHandle {
        NONE,
        CORNER_TL, CORNER_TR, CORNER_BR, CORNER_BL,
        MID_TOP, MID_RIGHT, MID_BOTTOM, MID_LEFT,
        CENTER
    }

    private var activeHandle = TouchHandle.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Paints & Paths
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000") // 60% dimming outside crop polygon
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C7CFA") // Primary color border
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#77FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val cornerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val cornerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C7CFA")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val midHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00C9A7") // Teal midpoints
        style = Paint.Style.FILL
    }

    private val polygonPath = Path()
    private val dimPath = Path()

    fun setImageBitmap(bitmap: Bitmap?) {
        this.sourceBitmap = bitmap
        computeImageRect()
        autoDetectCropBounds()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageRect()
    }

    private fun computeImageRect() {
        val bmp = sourceBitmap ?: return
        val isRotated = rotationAngle == 90 || rotationAngle == 270
        val bmpW = if (isRotated) bmp.height.toFloat() else bmp.width.toFloat()
        val bmpH = if (isRotated) bmp.width.toFloat() else bmp.height.toFloat()

        if (bmpW <= 0 || bmpH <= 0 || width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scale = minOf((viewW - 40f) / bmpW, (viewH - 40f) / bmpH)
        val destW = bmpW * scale
        val destH = bmpH * scale

        val left = (viewW - destW) / 2f
        val top = (viewH - destH) / 2f
        imageRect.set(left, top, left + destW, top + destH)
    }

    /**
     * Executes edge detection to locate document corners automatically.
     */
    fun autoDetectCropBounds() {
        val bmp = sourceBitmap
        if (bmp != null && !bmp.isRecycled) {
            corners = EdgeDetectionUtils.detectDocumentCorners(bmp)
        } else {
            corners = EdgeDetectionUtils.getDefaultCorners()
        }
        invalidate()
    }

    fun resetToFull() {
        corners = arrayOf(
            PointF(0f, 0f),
            PointF(1f, 0f),
            PointF(1f, 1f),
            PointF(0f, 1f)
        )
        invalidate()
    }

    fun getCorners(): Array<PointF> {
        return arrayOf(
            PointF(corners[0].x, corners[0].y),
            PointF(corners[1].x, corners[1].y),
            PointF(corners[2].x, corners[2].y),
            PointF(corners[3].x, corners[3].y)
        )
    }

    // Convert normalized corner to screen pixel coordinates
    private fun toScreen(p: PointF): PointF {
        return PointF(
            imageRect.left + p.x * imageRect.width(),
            imageRect.top + p.y * imageRect.height()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return

        // 1. Draw Rotated Bitmap
        canvas.save()
        val cx = imageRect.centerX()
        val cy = imageRect.centerY()
        if (rotationAngle != 0) {
            canvas.rotate(rotationAngle.toFloat(), cx, cy)
        }
        val isRotated = rotationAngle == 90 || rotationAngle == 270
        val drawRect = if (isRotated) {
            val scale = minOf((width - 40f) / bmp.height, (height - 40f) / bmp.width)
            val w = bmp.width * scale
            val h = bmp.height * scale
            RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        } else {
            imageRect
        }
        canvas.drawBitmap(bmp, null, drawRect, bitmapPaint)
        canvas.restore()

        // 2. Compute screen points for 4 corners
        val sTL = toScreen(corners[0])
        val sTR = toScreen(corners[1])
        val sBR = toScreen(corners[2])
        val sBL = toScreen(corners[3])

        // 3. Construct Polygon Path
        polygonPath.reset()
        polygonPath.moveTo(sTL.x, sTL.y)
        polygonPath.lineTo(sTR.x, sTR.y)
        polygonPath.lineTo(sBR.x, sBR.y)
        polygonPath.lineTo(sBL.x, sBL.y)
        polygonPath.close()

        // 4. Draw Dimming Mask outside the polygon
        dimPath.reset()
        dimPath.addRect(imageRect, Path.Direction.CW)
        dimPath.addPath(polygonPath)
        dimPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(dimPath, dimPaint)

        // 5. Draw Polygon Border
        canvas.drawPath(polygonPath, borderPaint)

        // 6. Draw 3x3 Grid inside the quadrilateral polygon
        for (i in 1..2) {
            val f = i / 3f
            // Vertical grid lines
            val topX = sTL.x + f * (sTR.x - sTL.x)
            val topY = sTL.y + f * (sTR.y - sTL.y)
            val botX = sBL.x + f * (sBR.x - sBL.x)
            val botY = sBL.y + f * (sBR.y - sBL.y)
            canvas.drawLine(topX, topY, botX, botY, gridPaint)

            // Horizontal grid lines
            val leftX = sTL.x + f * (sBL.x - sTL.x)
            val leftY = sTL.y + f * (sBL.y - sTL.y)
            val rightX = sTR.x + f * (sBR.x - sTR.x)
            val rightY = sTR.y + f * (sBR.y - sTR.y)
            canvas.drawLine(leftX, leftY, rightX, rightY, gridPaint)
        }

        // 7. Draw 4 Corner Handles
        val cornerRadius = 22f
        drawHandle(canvas, sTL.x, sTL.y, cornerRadius, cornerHandlePaint, cornerBorderPaint)
        drawHandle(canvas, sTR.x, sTR.y, cornerRadius, cornerHandlePaint, cornerBorderPaint)
        drawHandle(canvas, sBR.x, sBR.y, cornerRadius, cornerHandlePaint, cornerBorderPaint)
        drawHandle(canvas, sBL.x, sBL.y, cornerRadius, cornerHandlePaint, cornerBorderPaint)

        // 8. Draw 4 Midpoint Edge Handles
        val midRadius = 14f
        drawHandle(canvas, (sTL.x + sTR.x) / 2f, (sTL.y + sTR.y) / 2f, midRadius, midHandlePaint, cornerBorderPaint)
        drawHandle(canvas, (sTR.x + sBR.x) / 2f, (sTR.y + sBR.y) / 2f, midRadius, midHandlePaint, cornerBorderPaint)
        drawHandle(canvas, (sBL.x + sBR.x) / 2f, (sBL.y + sBR.y) / 2f, midRadius, midHandlePaint, cornerBorderPaint)
        drawHandle(canvas, (sTL.x + sBL.x) / 2f, (sTL.y + sBL.y) / 2f, midRadius, midHandlePaint, cornerBorderPaint)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, fillPaint: Paint, borderPaint: Paint) {
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val touchRadius = 60f

        val sTL = toScreen(corners[0])
        val sTR = toScreen(corners[1])
        val sBR = toScreen(corners[2])
        val sBL = toScreen(corners[3])

        val sMidT = PointF((sTL.x + sTR.x) / 2f, (sTL.y + sTR.y) / 2f)
        val sMidR = PointF((sTR.x + sBR.x) / 2f, (sTR.y + sBR.y) / 2f)
        val sMidB = PointF((sBL.x + sBR.x) / 2f, (sBL.y + sBR.y) / 2f)
        val sMidL = PointF((sTL.x + sBL.x) / 2f, (sTL.y + sBL.y) / 2f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = when {
                    Math.hypot((x - sTL.x).toDouble(), (y - sTL.y).toDouble()) <= touchRadius -> TouchHandle.CORNER_TL
                    Math.hypot((x - sTR.x).toDouble(), (y - sTR.y).toDouble()) <= touchRadius -> TouchHandle.CORNER_TR
                    Math.hypot((x - sBR.x).toDouble(), (y - sBR.y).toDouble()) <= touchRadius -> TouchHandle.CORNER_BR
                    Math.hypot((x - sBL.x).toDouble(), (y - sBL.y).toDouble()) <= touchRadius -> TouchHandle.CORNER_BL
                    Math.hypot((x - sMidT.x).toDouble(), (y - sMidT.y).toDouble()) <= touchRadius -> TouchHandle.MID_TOP
                    Math.hypot((x - sMidR.x).toDouble(), (y - sMidR.y).toDouble()) <= touchRadius -> TouchHandle.MID_RIGHT
                    Math.hypot((x - sMidB.x).toDouble(), (y - sMidB.y).toDouble()) <= touchRadius -> TouchHandle.MID_BOTTOM
                    Math.hypot((x - sMidL.x).toDouble(), (y - sMidL.y).toDouble()) <= touchRadius -> TouchHandle.MID_LEFT
                    isInsidePolygon(x, y, sTL, sTR, sBR, sBL) -> TouchHandle.CENTER
                    else -> TouchHandle.NONE
                }

                if (activeHandle != TouchHandle.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != TouchHandle.NONE) {
                    var dx = (x - lastTouchX) / imageRect.width()
                    var dy = (y - lastTouchY) / imageRect.height()

                    // Magnetic grid snap: snap to orthogonal alignment if close (within ~0.02)
                    val snapThreshold = 0.018f

                    when (activeHandle) {
                        TouchHandle.CORNER_TL -> {
                            val newX = (corners[0].x + dx).coerceIn(0f, corners[1].x - 0.08f)
                            val newY = (corners[0].y + dy).coerceIn(0f, corners[3].y - 0.08f)
                            corners[0].x = if (Math.abs(newX - corners[3].x) < snapThreshold) corners[3].x else newX
                            corners[0].y = if (Math.abs(newY - corners[1].y) < snapThreshold) corners[1].y else newY
                        }
                        TouchHandle.CORNER_TR -> {
                            val newX = (corners[1].x + dx).coerceIn(corners[0].x + 0.08f, 1f)
                            val newY = (corners[1].y + dy).coerceIn(0f, corners[2].y - 0.08f)
                            corners[1].x = if (Math.abs(newX - corners[2].x) < snapThreshold) corners[2].x else newX
                            corners[1].y = if (Math.abs(newY - corners[0].y) < snapThreshold) corners[0].y else newY
                        }
                        TouchHandle.CORNER_BR -> {
                            val newX = (corners[2].x + dx).coerceIn(corners[3].x + 0.08f, 1f)
                            val newY = (corners[2].y + dy).coerceIn(corners[1].y + 0.08f, 1f)
                            corners[2].x = if (Math.abs(newX - corners[1].x) < snapThreshold) corners[1].x else newX
                            corners[2].y = if (Math.abs(newY - corners[3].y) < snapThreshold) corners[3].y else newY
                        }
                        TouchHandle.CORNER_BL -> {
                            val newX = (corners[3].x + dx).coerceIn(0f, corners[2].x - 0.08f)
                            val newY = (corners[3].y + dy).coerceIn(corners[0].y + 0.08f, 1f)
                            corners[3].x = if (Math.abs(newX - corners[0].x) < snapThreshold) corners[0].x else newX
                            corners[3].y = if (Math.abs(newY - corners[2].y) < snapThreshold) corners[2].y else newY
                        }
                        TouchHandle.MID_TOP -> {
                            corners[0].y = (corners[0].y + dy).coerceIn(0f, corners[3].y - 0.08f)
                            corners[1].y = (corners[1].y + dy).coerceIn(0f, corners[2].y - 0.08f)
                        }
                        TouchHandle.MID_BOTTOM -> {
                            corners[3].y = (corners[3].y + dy).coerceIn(corners[0].y + 0.08f, 1f)
                            corners[2].y = (corners[2].y + dy).coerceIn(corners[1].y + 0.08f, 1f)
                        }
                        TouchHandle.MID_LEFT -> {
                            corners[0].x = (corners[0].x + dx).coerceIn(0f, corners[1].x - 0.08f)
                            corners[3].x = (corners[3].x + dx).coerceIn(0f, corners[2].x - 0.08f)
                        }
                        TouchHandle.MID_RIGHT -> {
                            corners[1].x = (corners[1].x + dx).coerceIn(corners[0].x + 0.08f, 1f)
                            corners[2].x = (corners[2].x + dx).coerceIn(corners[3].x + 0.08f, 1f)
                        }
                        TouchHandle.CENTER -> {
                            val minX = minOf(corners[0].x, corners[3].x)
                            val maxX = maxOf(corners[1].x, corners[2].x)
                            val minY = minOf(corners[0].y, corners[1].y)
                            val maxY = maxOf(corners[2].y, corners[3].y)

                            if (minX + dx < 0f) dx = -minX
                            if (maxX + dx > 1f) dx = 1f - maxX
                            if (minY + dy < 0f) dy = -minY
                            if (maxY + dy > 1f) dy = 1f - maxY

                            for (c in corners) {
                                c.x += dx
                                c.y += dy
                            }
                        }
                        TouchHandle.NONE -> {}
                    }

                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = TouchHandle.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isInsidePolygon(px: Float, py: Float, p0: PointF, p1: PointF, p2: PointF, p3: PointF): Boolean {
        var inside = false
        val pts = arrayOf(p0, p1, p2, p3)
        var j = pts.size - 1
        for (i in pts.indices) {
            if ((pts[i].y > py) != (pts[j].y > py) &&
                px < (pts[j].x - pts[i].x) * (py - pts[i].y) / (pts[j].y - pts[i].y) + pts[i].x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
