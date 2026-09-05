package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DocumentCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolMode {
        VIEW,
        PEN,
        HIGHLIGHTER,
        ERASER,
        SIGNATURE,
        TEXT
    }

    var toolMode: ToolMode = ToolMode.VIEW
        set(value) {
            field = value
            invalidate()
        }

    var strokeColor: Int = Color.parseColor("#FF2D55")
    var strokeWidthPx: Float = 10f

    var onTextDoubleTapped: ((TextItem) -> Unit)? = null
    var onTextDeleted: ((TextItem) -> Unit)? = null

    private var baseBitmap: Bitmap? = null
    private var pageData: DocumentPage? = null

    // Touch path in progress
    private var currentPath: Path? = null
    private var lastX = 0f
    private var lastY = 0f

    // Signature dragging
    private var isDraggingSignature = false

    // Text item touch state
    private var activeTextItem: TextItem? = null
    private var isDraggingTextItem = false
    private var isResizingTextItem = false
    private var lastTapTime: Long = 0
    private var lastTappedId: String? = null

    // Paints
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val textItemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val selectionBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5C7CFA")
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val handleDeleteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350") // Red delete handle
        style = Paint.Style.FILL
    }
    private val handleResizeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00C9A7") // Teal resize handle
        style = Paint.Style.FILL
    }
    private val handleIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    // Image destination rectangle within this view
    private val imageDestRect = RectF()

    fun bind(bitmap: Bitmap?, page: DocumentPage) {
        this.baseBitmap = bitmap
        this.pageData = page
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageDestRect()
    }

    private fun computeImageDestRect() {
        val bmp = baseBitmap ?: return
        val rot = (pageData?.rotationDegrees ?: 0) % 360
        val isRotated = rot == 90 || rot == 270
        val bmpW = if (isRotated) bmp.height.toFloat() else bmp.width.toFloat()
        val bmpH = if (isRotated) bmp.width.toFloat() else bmp.height.toFloat()

        if (bmpW <= 0 || bmpH <= 0 || width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scale = minOf(viewW / bmpW, viewH / bmpH)
        val destW = bmpW * scale
        val destH = bmpH * scale

        val left = (viewW - destW) / 2f
        val top = (viewH - destH) / 2f
        imageDestRect.set(left, top, left + destW, top + destH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeImageDestRect()

        val page = pageData ?: return
        val bmp = baseBitmap

        if (bmp != null && !bmp.isRecycled) {
            imagePaint.colorFilter = page.filterType.getColorFilter(page.brightnessAdjustment)

            canvas.save()
            val rot = (page.rotationDegrees % 360 + 360) % 360
            val cx = imageDestRect.centerX()
            val cy = imageDestRect.centerY()

            if (rot != 0) {
                canvas.rotate(rot.toFloat(), cx, cy)
            }

            val isRotated = rot == 90 || rot == 270
            val drawRect = if (isRotated) {
                val scale = minOf(width.toFloat() / bmp.height, height.toFloat() / bmp.width)
                val w = bmp.width * scale
                val h = bmp.height * scale
                RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            } else {
                imageDestRect
            }

            canvas.drawBitmap(bmp, null, drawRect, imagePaint)
            canvas.restore()
        }

        // Draw saved drawings/eraser/highlights
        for (dp in page.drawingPaths) {
            configurePaintForDrawing(dp)
            canvas.drawPath(dp.path, drawingPaint)
        }

        // Draw current path in progress
        currentPath?.let { path ->
            val tempDp = when (toolMode) {
                ToolMode.ERASER -> DrawingPath(path, Color.WHITE, strokeWidthPx * 2f, isEraser = true)
                ToolMode.HIGHLIGHTER -> DrawingPath(path, Color.YELLOW, strokeWidthPx * 2.5f, isHighlighter = true)
                else -> DrawingPath(path, strokeColor, strokeWidthPx)
            }
            configurePaintForDrawing(tempDp)
            canvas.drawPath(path, drawingPaint)
        }

        // Draw Watermark
        page.watermarkText?.takeIf { it.isNotBlank() }?.let { text ->
            canvas.save()
            val cx = imageDestRect.centerX()
            val cy = imageDestRect.centerY()
            canvas.rotate(page.watermarkAngle, cx, cy)
            watermarkPaint.textSize = minOf(imageDestRect.width(), imageDestRect.height()) * 0.12f
            watermarkPaint.color = Color.DKGRAY
            watermarkPaint.alpha = (page.watermarkOpacity.coerceIn(0.05f, 1f) * 255).toInt()
            canvas.drawText(text, cx, cy + watermarkPaint.textSize / 3f, watermarkPaint)
            canvas.restore()
        }

        // Draw Signature overlay
        val sigBmp = page.signatureBitmap
        val sigRect = page.signatureNormRect
        if (sigBmp != null && sigRect != null && !sigBmp.isRecycled) {
            val destSig = RectF(
                imageDestRect.left + sigRect.left * imageDestRect.width(),
                imageDestRect.top + sigRect.top * imageDestRect.height(),
                imageDestRect.left + sigRect.right * imageDestRect.width(),
                imageDestRect.top + sigRect.bottom * imageDestRect.height()
            )
            canvas.drawBitmap(sigBmp, null, destSig, imagePaint)
        }

        // Draw Text Items
        for (item in page.textItems) {
            drawInteractiveTextItem(canvas, item)
        }
    }

    private fun configurePaintForDrawing(dp: DrawingPath) {
        drawingPaint.reset()
        drawingPaint.isAntiAlias = true
        drawingPaint.style = Paint.Style.STROKE
        drawingPaint.strokeCap = Paint.Cap.ROUND
        drawingPaint.strokeJoin = Paint.Join.ROUND
        drawingPaint.strokeWidth = dp.strokeWidth

        when {
            dp.isEraser -> {
                drawingPaint.color = Color.WHITE
                drawingPaint.alpha = 255
            }
            dp.isHighlighter -> {
                drawingPaint.color = dp.color
                drawingPaint.alpha = 110
            }
            else -> {
                drawingPaint.color = dp.color
                drawingPaint.alpha = 255
            }
        }
    }

    private fun drawInteractiveTextItem(canvas: Canvas, item: TextItem) {
        val px = imageDestRect.left + item.x * imageDestRect.width()
        val py = imageDestRect.top + item.y * imageDestRect.height()

        val scaleFactor = (imageDestRect.width() / 1000f) * item.scale
        textItemPaint.textSize = item.textSize * scaleFactor
        textItemPaint.color = item.textColor

        val textW = textItemPaint.measureText(item.text)
        val textH = textItemPaint.textSize

        canvas.save()
        canvas.rotate(item.rotation, px, py)

        // Draw text
        canvas.drawText(item.text, px - textW / 2f, py + textH / 3f, textItemPaint)

        // If selected, draw bounding box & corner handles
        if (item.isSelected) {
            val pad = 16f
            val box = RectF(
                px - textW / 2f - pad,
                py - textH / 2f - pad,
                px + textW / 2f + pad,
                py + textH / 2f + pad
            )

            // Bounding box
            canvas.drawRoundRect(box, 10f, 10f, selectionBoxPaint)

            // Top-Right Delete Handle ('X')
            val handleR = 20f
            val delX = box.right
            val delY = box.top
            canvas.drawCircle(delX, delY, handleR, handleDeleteBgPaint)
            val dSize = 7f
            canvas.drawLine(delX - dSize, delY - dSize, delX + dSize, delY + dSize, handleIconPaint)
            canvas.drawLine(delX + dSize, delY - dSize, delX - dSize, delY + dSize, handleIconPaint)

            // Bottom-Right Resize/Rotate Handle
            val resX = box.right
            val resY = box.bottom
            canvas.drawCircle(resX, resY, handleR, handleResizeBgPaint)
            canvas.drawCircle(resX, resY, 8f, handleIconPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val page = pageData ?: return super.onTouchEvent(event)

        when (toolMode) {
            ToolMode.PEN, ToolMode.HIGHLIGHTER, ToolMode.ERASER -> {
                val x = event.x
                val y = event.y

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        currentPath = Path().apply { moveTo(x, y) }
                        lastX = x
                        lastY = y
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(x - lastX)
                        val dy = Math.abs(y - lastY)
                        if (dx >= 3 || dy >= 3) {
                            currentPath?.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                            lastX = x
                            lastY = y
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        currentPath?.let { path ->
                            val dp = when (toolMode) {
                                ToolMode.ERASER -> DrawingPath(path, Color.WHITE, strokeWidthPx * 2f, isEraser = true)
                                ToolMode.HIGHLIGHTER -> DrawingPath(path, strokeColor, strokeWidthPx * 2.5f, isHighlighter = true)
                                else -> DrawingPath(path, strokeColor, strokeWidthPx)
                            }
                            page.drawingPaths.add(dp)
                        }
                        currentPath = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                        invalidate()
                        return true
                    }
                }
            }
            ToolMode.SIGNATURE -> {
                val sigRect = page.signatureNormRect
                if (sigRect != null) {
                    val currentDestSig = RectF(
                        imageDestRect.left + sigRect.left * imageDestRect.width(),
                        imageDestRect.top + sigRect.top * imageDestRect.height(),
                        imageDestRect.left + sigRect.right * imageDestRect.width(),
                        imageDestRect.top + sigRect.bottom * imageDestRect.height()
                    )
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (currentDestSig.contains(event.x, event.y)) {
                                isDraggingSignature = true
                                parent?.requestDisallowInterceptTouchEvent(true)
                                lastX = event.x
                                lastY = event.y
                                return true
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (isDraggingSignature) {
                                val dx = (event.x - lastX) / imageDestRect.width()
                                val dy = (event.y - lastY) / imageDestRect.height()
                                sigRect.offset(dx, dy)
                                lastX = event.x
                                lastY = event.y
                                invalidate()
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isDraggingSignature = false
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                    }
                }
            }
            ToolMode.TEXT, ToolMode.VIEW -> {
                // Interactive selection & drag of TextItems
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val touchX = event.x
                        val touchY = event.y

                        // 1. Check if touched on selected item's handles first
                        val selected = page.textItems.firstOrNull { it.isSelected }
                        if (selected != null) {
                            val px = imageDestRect.left + selected.x * imageDestRect.width()
                            val py = imageDestRect.top + selected.y * imageDestRect.height()
                            val scaleFactor = (imageDestRect.width() / 1000f) * selected.scale
                            textItemPaint.textSize = selected.textSize * scaleFactor
                            val textW = textItemPaint.measureText(selected.text)
                            val textH = textItemPaint.textSize
                            val pad = 16f

                            val delX = px + textW / 2f + pad
                            val delY = py - textH / 2f - pad
                            if (Math.hypot((touchX - delX).toDouble(), (touchY - delY).toDouble()) <= 55) {
                                page.textItems.remove(selected)
                                onTextDeleted?.invoke(selected)
                                invalidate()
                                return true
                            }

                            val resX = px + textW / 2f + pad
                            val resY = py + textH / 2f + pad
                            if (Math.hypot((touchX - resX).toDouble(), (touchY - resY).toDouble()) <= 55) {
                                isResizingTextItem = true
                                activeTextItem = selected
                                lastX = touchX
                                lastY = touchY
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                        }

                        // 2. Check if touched inside any text item
                        var hitItem: TextItem? = null
                        for (item in page.textItems.reversed()) {
                            val px = imageDestRect.left + item.x * imageDestRect.width()
                            val py = imageDestRect.top + item.y * imageDestRect.height()
                            val scaleFactor = (imageDestRect.width() / 1000f) * item.scale
                            textItemPaint.textSize = item.textSize * scaleFactor
                            val textW = textItemPaint.measureText(item.text)
                            val textH = textItemPaint.textSize
                            val pad = 24f

                            val box = RectF(px - textW / 2f - pad, py - textH / 2f - pad, px + textW / 2f + pad, py + textH / 2f + pad)
                            if (box.contains(touchX, touchY)) {
                                hitItem = item
                                break
                            }
                        }

                        if (hitItem != null) {
                            val now = System.currentTimeMillis()
                            if (lastTappedId == hitItem.id && now - lastTapTime < 350) {
                                // Double tap detected -> Open Edit dialog!
                                onTextDoubleTapped?.invoke(hitItem)
                                lastTapTime = 0
                                return true
                            }
                            lastTapTime = now
                            lastTappedId = hitItem.id

                            for (it in page.textItems) it.isSelected = false
                            hitItem.isSelected = true
                            activeTextItem = hitItem
                            isDraggingTextItem = true
                            lastX = touchX
                            lastY = touchY
                            parent?.requestDisallowInterceptTouchEvent(true)
                            invalidate()
                            return true
                        } else {
                            // Tapped outside -> Deselect all
                            for (it in page.textItems) it.isSelected = false
                            activeTextItem = null
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDraggingTextItem && activeTextItem != null) {
                            val dx = (event.x - lastX) / imageDestRect.width()
                            val dy = (event.y - lastY) / imageDestRect.height()
                            activeTextItem!!.x = (activeTextItem!!.x + dx).coerceIn(0.05f, 0.95f)
                            activeTextItem!!.y = (activeTextItem!!.y + dy).coerceIn(0.05f, 0.95f)
                            lastX = event.x
                            lastY = event.y
                            invalidate()
                            return true
                        } else if (isResizingTextItem && activeTextItem != null) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            activeTextItem!!.scale = (activeTextItem!!.scale + (dx + dy) / 300f).coerceIn(0.4f, 4.0f)
                            lastX = event.x
                            lastY = event.y
                            invalidate()
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDraggingTextItem = false
                        isResizingTextItem = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
