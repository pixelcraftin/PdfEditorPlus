package com.pixelcraftin.pdfeditorplus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceGray
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    /** Check if a PDF file is password protected */
    fun isPasswordProtected(file: File): Boolean {
        return try {
            val reader = PdfReader(file.absolutePath)
            val isEnc = reader.isEncrypted
            reader.close()
            isEnc
        } catch (e: com.itextpdf.kernel.exceptions.BadPasswordException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Verify if the given password can open the PDF */
    fun verifyPassword(file: File, password: String): Boolean {
        return try {
            val readerProps = ReaderProperties().setPassword(password.toByteArray())
            val reader = PdfReader(file.absolutePath, readerProps)
            val doc = PdfDocument(reader)
            val count = doc.numberOfPages
            doc.close()
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Merge multiple PDF files into one (supporting password protected inputs & default author) */
    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File,
        passwords: Map<String, String> = emptyMap(),
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val writer = PdfWriter(outputFile)
            val destDoc = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                destDoc.documentInfo.author = it
            }
            val pdfMerger = com.itextpdf.kernel.utils.PdfMerger(destDoc)
            for (file in inputFiles) {
                val pwd = passwords[file.absolutePath]
                val reader = if (!pwd.isNullOrEmpty()) {
                    val props = ReaderProperties().setPassword(pwd.toByteArray())
                    PdfReader(file.absolutePath, props)
                } else {
                    PdfReader(file.absolutePath)
                }
                val src = PdfDocument(reader)
                pdfMerger.merge(src, 1, src.numberOfPages)
                src.close()
            }
            pdfMerger.close()
            destDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Split a PDF by page range (1-indexed, inclusive) */
    suspend fun splitPdf(
        inputFile: File,
        pageRange: IntRange,
        outputFile: File,
        password: String? = null,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = if (!password.isNullOrEmpty()) {
                val props = ReaderProperties().setPassword(password.toByteArray())
                PdfReader(inputFile.absolutePath, props)
            } else {
                PdfReader(inputFile)
            }
            val src = PdfDocument(reader)
            val pages = pageRange.filter { it in 1..src.numberOfPages }
            val writer = PdfWriter(outputFile)
            val dest = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                dest.documentInfo.author = it
            }
            src.copyPagesTo(pages, dest)
            dest.close()
            src.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Rotate all pages (or specific pages) of a PDF */
    suspend fun rotatePdf(
        inputFile: File,
        degrees: Int,
        outputFile: File,
        applyTo: ApplyTo = ApplyTo.ALL,
        customPages: List<Int> = emptyList(),
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writer = PdfWriter(outputFile)
            val pdfDoc = PdfDocument(reader, writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val totalPages = pdfDoc.numberOfPages
            for (i in 1..totalPages) {
                val shouldRotate = when (applyTo) {
                    ApplyTo.ALL -> true
                    ApplyTo.EVEN -> i % 2 == 0
                    ApplyTo.ODD -> i % 2 != 0
                    ApplyTo.CUSTOM -> i in customPages
                }
                if (shouldRotate) {
                    val page = pdfDoc.getPage(i)
                    val currentRotation = page.rotation
                    val newRotation = if (degrees == 0) 0 else ((currentRotation + degrees) % 360 + 360) % 360
                    page.rotation = newRotation
                }
            }
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Rearrange pages of a PDF in a new order (1-indexed list of page numbers) */
    suspend fun rearrangePages(
        inputFile: File,
        pageOrder: List<Int>,
        outputFile: File,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val src = PdfDocument(reader)
            val writer = PdfWriter(outputFile)
            val dest = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                dest.documentInfo.author = it
            }
            src.copyPagesTo(pageOrder, dest)
            dest.close()
            src.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Add watermark text with selectable angle, foreground layering, rotation awareness, and true geometric center */
    suspend fun addWatermark(
        inputFile: File,
        watermarkText: String,
        outputFile: File,
        opacity: Float = 0.3f,
        fontSize: Float = 48f,
        angleDegrees: Float = 45f,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writer = PdfWriter(outputFile)
            val pdfDoc = PdfDocument(reader, writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val font = com.itextpdf.kernel.font.PdfFontFactory.createFont()
            for (i in 1..pdfDoc.numberOfPages) {
                val page = pdfDoc.getPage(i)
                val rot = (page.rotation % 360 + 360) % 360
                val box = page.cropBox ?: page.mediaBox

                val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdfDoc)
                canvas.saveState()

                // Align coordinate system with viewer orientation
                when (rot) {
                    0 -> canvas.concatMatrix(1.0, 0.0, 0.0, 1.0, box.left.toDouble(), box.bottom.toDouble())
                    90 -> canvas.concatMatrix(0.0, 1.0, -1.0, 0.0, box.right.toDouble(), box.bottom.toDouble())
                    180 -> canvas.concatMatrix(-1.0, 0.0, 0.0, -1.0, box.right.toDouble(), box.top.toDouble())
                    270 -> canvas.concatMatrix(0.0, -1.0, 1.0, 0.0, box.left.toDouble(), box.top.toDouble())
                }

                val vWidth = if (rot == 90 || rot == 270) box.height else box.width
                val vHeight = if (rot == 90 || rot == 270) box.width else box.height
                val centerX = (vWidth / 2f).toDouble()
                val centerY = (vHeight / 2f).toDouble()

                val pdfExtGState = com.itextpdf.kernel.pdf.extgstate.PdfExtGState().setFillOpacity(opacity.coerceIn(0.05f, 1.0f))
                canvas.setExtGState(pdfExtGState)

                val rad = Math.toRadians(angleDegrees.toDouble())
                val cos = Math.cos(rad)
                val sin = Math.sin(rad)

                val textWidth = font.getWidth(watermarkText, fontSize)
                val textHeight = fontSize * 0.75f // approx cap height

                // Pivot rotation exactly around geometric center of the page
                canvas.concatMatrix(cos, sin, -sin, cos, centerX, centerY)

                canvas.beginText()
                canvas.setFontAndSize(font, fontSize)
                canvas.setFillColor(com.itextpdf.kernel.colors.DeviceRgb(100, 100, 100))
                canvas.setTextMatrix(1f, 0f, 0f, 1f, -textWidth / 2f, -textHeight / 2f)
                canvas.showText(watermarkText)
                canvas.endText()

                canvas.restoreState()
                canvas.release()
            }
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Add page numbers with rotation awareness, foreground layering, and clean pill backdrop */
    suspend fun addPageNumbers(
        inputFile: File,
        outputFile: File,
        startFrom: Int = 1,
        position: NumberPosition = NumberPosition.BOTTOM_CENTER,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writer = PdfWriter(outputFile)
            val pdfDoc = PdfDocument(reader, writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val font = com.itextpdf.kernel.font.PdfFontFactory.createFont()
            val total = pdfDoc.numberOfPages
            val fontSize = 10f

            for (i in 1..total) {
                val page = pdfDoc.getPage(i)
                val rot = (page.rotation % 360 + 360) % 360
                val box = page.cropBox ?: page.mediaBox

                val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdfDoc)
                canvas.saveState()

                // Coordinate system aligned with viewer orientation
                when (rot) {
                    0 -> canvas.concatMatrix(1.0, 0.0, 0.0, 1.0, box.left.toDouble(), box.bottom.toDouble())
                    90 -> canvas.concatMatrix(0.0, 1.0, -1.0, 0.0, box.right.toDouble(), box.bottom.toDouble())
                    180 -> canvas.concatMatrix(-1.0, 0.0, 0.0, -1.0, box.right.toDouble(), box.top.toDouble())
                    270 -> canvas.concatMatrix(0.0, -1.0, 1.0, 0.0, box.left.toDouble(), box.top.toDouble())
                }

                val visualWidth = if (rot == 90 || rot == 270) box.height else box.width
                val visualHeight = if (rot == 90 || rot == 270) box.width else box.height

                val num = i + startFrom - 1
                val text = "$num / $total"
                val textWidth = font.getWidth(text, fontSize)

                val margin = 24f

                val (drawX, drawY) = when (position) {
                    NumberPosition.BOTTOM_LEFT -> Pair(margin, margin)
                    NumberPosition.BOTTOM_CENTER -> Pair((visualWidth - textWidth) / 2f, margin)
                    NumberPosition.BOTTOM_RIGHT -> Pair(visualWidth - margin - textWidth, margin)
                    NumberPosition.TOP_LEFT -> Pair(margin, visualHeight - margin - fontSize)
                    NumberPosition.TOP_CENTER -> Pair((visualWidth - textWidth) / 2f, visualHeight - margin - fontSize)
                    NumberPosition.TOP_RIGHT -> Pair(visualWidth - margin - textWidth, visualHeight - margin - fontSize)
                }

                // Draw clean semi-rounded white pill background behind number to prevent overlap/obscurity
                val pillPaddingX = 8f
                val pillPaddingY = 4f
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.roundRectangle(
                    (drawX - pillPaddingX).toDouble(),
                    (drawY - pillPaddingY).toDouble(),
                    (textWidth + 2 * pillPaddingX).toDouble(),
                    (fontSize + 2 * pillPaddingY).toDouble(),
                    4.0
                )
                canvas.fill()

                // Draw text in crisp dark gray (#333333)
                canvas.beginText()
                canvas.setFontAndSize(font, fontSize)
                canvas.setFillColor(com.itextpdf.kernel.colors.DeviceRgb(51, 51, 51))
                canvas.setTextMatrix(1f, 0f, 0f, 1f, drawX, drawY)
                canvas.showText(text)
                canvas.endText()

                canvas.restoreState()
                canvas.release()
            }
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Protect PDF with a user password */
    suspend fun protectPdf(
        inputFile: File,
        password: String,
        outputFile: File,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writerProps = WriterProperties()
                .setStandardEncryption(
                    password.toByteArray(),
                    password.toByteArray(),
                    EncryptionConstants.ALLOW_PRINTING or EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_256
                )
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val pdfDoc = PdfDocument(reader, writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Check if a PDF file is encrypted / password protected */
    fun isPdfPasswordProtected(inputFile: File): Boolean {
        return try {
            val reader = PdfReader(inputFile.absolutePath)
            val encrypted = reader.isEncrypted
            reader.close()
            encrypted
        } catch (e: com.itextpdf.kernel.exceptions.BadPasswordException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Unlock (remove password and encryption from) a PDF including AES-128/256 and RC4 bank statements */
    suspend fun unlockPdf(
        inputFile: File,
        password: String,
        outputFile: File,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val readerProps = ReaderProperties().setPassword(password.toByteArray(Charsets.UTF_8))
            val reader = PdfReader(inputFile.absolutePath, readerProps)
            reader.setUnethicalReading(true)
            val srcDoc = PdfDocument(reader)

            val writerProps = WriterProperties()
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val destDoc = PdfDocument(writer)

            srcDoc.copyPagesTo(1, srcDoc.numberOfPages, destDoc)

            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                destDoc.documentInfo.author = it
            }

            destDoc.close()
            srcDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Add signature bitmap to a PDF */
    suspend fun addSignature(
        inputFile: File,
        signatureBitmap: Bitmap,
        outputFile: File,
        pageNumber: Int = 1,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writer = PdfWriter(outputFile)
            val pdfDoc = PdfDocument(reader, writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val page = pdfDoc.getPage(pageNumber.coerceIn(1, pdfDoc.numberOfPages))
            val stream = ByteArrayOutputStream()
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imgData = ImageDataFactory.create(stream.toByteArray())
            val image = Image(imgData)
            image.scaleToFit(150f, 80f)
            val doc = Document(pdfDoc)
            val ps = page.pageSize
            image.setFixedPosition(pageNumber, ps.right - 180f, ps.bottom + 40f)
            doc.add(image)
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Compress a PDF file with Low, Medium, or High compression */
    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        compressionLevel: Int = 1, // 0 = Low, 1 = Medium, 2 = High
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val (scaleFactor, jpegQuality) = when (compressionLevel) {
                0 -> Pair(2.0f, 80) // Low compression (High visual quality)
                1 -> Pair(1.5f, 65) // Medium compression (Balanced)
                2 -> Pair(1.1f, 48) // High compression (Maximum size reduction)
                else -> Pair(1.5f, 65)
            }

            val pfd = android.os.ParcelFileDescriptor.open(inputFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            val writerProps = WriterProperties()
                .useSmartMode()
                .setFullCompressionMode(true)
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val pdfDoc = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val doc = Document(pdfDoc)
            doc.setMargins(0f, 0f, 0f, 0f)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val w = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                val h = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                bitmap.recycle()

                val imgData = ImageDataFactory.create(stream.toByteArray())
                val img = Image(imgData)
                val pageSize = PageSize(page.width.toFloat(), page.height.toFloat())
                pdfDoc.addNewPage(pageSize)
                img.setFixedPosition(i + 1, 0f, 0f)
                img.scaleToFit(pageSize.width, pageSize.height)
                doc.add(img)
            }

            doc.close()
            renderer.close()
            pfd.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Edit PDF metadata */
    suspend fun editMetadata(
        inputFile: File,
        title: String?,
        author: String?,
        subject: String?,
        keywords: String?,
        creator: String?,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val writer = PdfWriter(outputFile)
            val pdfDoc = PdfDocument(reader, writer)
            val info = pdfDoc.documentInfo
            title?.let { info.setTitle(it) }
            author?.let { info.setAuthor(it) }
            subject?.let { info.setSubject(it) }
            keywords?.let { info.setKeywords(it) }
            creator?.let { info.setCreator(it) }
            pdfDoc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Convert image files to PDF preserving original image compression & minimal file size */
    suspend fun imagesToPdf(
        imageFiles: List<File>,
        outputFile: File,
        pageSizeOption: String = "A4",
        fitToPage: Boolean = true,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val isOnlyImage = pageSizeOption.contains("Only Image", ignoreCase = true)
            val isLetter = pageSizeOption.equals("Letter", ignoreCase = true)

            val writerProps = WriterProperties().useSmartMode()
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val pdfDoc = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val doc = Document(pdfDoc)
            doc.setMargins(0f, 0f, 0f, 0f)

            for ((index, file) in imageFiles.withIndex()) {
                val imgData = try {
                    ImageDataFactory.create(file.absolutePath)
                } catch (_: Exception) {
                    // Fallback for WebP / unsupported raw formats
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                        bmp.recycle()
                        ImageDataFactory.create(bos.toByteArray())
                    } else {
                        continue
                    }
                }

                val (pageWidth, pageHeight) = when {
                    isOnlyImage -> Pair(imgData.width, imgData.height)
                    isLetter -> Pair(PageSize.LETTER.width, PageSize.LETTER.height)
                    else -> Pair(PageSize.A4.width, PageSize.A4.height) // A4 default (595 x 842 pt)
                }

                val ps = PageSize(pageWidth, pageHeight)
                pdfDoc.addNewPage(ps)
                val pageIndex = index + 1
                val img = Image(imgData)

                if (isOnlyImage) {
                    img.setFixedPosition(pageIndex, 0f, 0f)
                    img.scaleToFit(pageWidth, pageHeight)
                } else {
                    val scale = if (fitToPage) {
                        minOf(pageWidth / imgData.width, pageHeight / imgData.height)
                    } else {
                        minOf(pageWidth / imgData.width, pageHeight / imgData.height, 1.0f)
                    }
                    val scaledW = imgData.width * scale
                    val scaledH = imgData.height * scale
                    val left = (pageWidth - scaledW) / 2f
                    val bottom = (pageHeight - scaledH) / 2f
                    img.setFixedPosition(pageIndex, left, bottom)
                    img.scaleToFit(scaledW, scaledH)
                }

                doc.add(img)
            }

            doc.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Extract plain text from a PDF */
    suspend fun extractText(inputFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reader = PdfReader(inputFile)
            val pdfDoc = PdfDocument(reader)
            val sb = StringBuilder()
            for (i in 1..pdfDoc.numberOfPages) {
                sb.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)))
                sb.append("\n\n")
            }
            pdfDoc.close()
            Result.success(sb.toString().trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Convert PDF to genuine grayscale */
    suspend fun convertToGrayscale(
        inputFile: File,
        outputFile: File,
        defaultAuthor: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pfd = android.os.ParcelFileDescriptor.open(inputFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            val writerProps = WriterProperties()
                .useSmartMode()
                .setFullCompressionMode(true)
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val pdfDoc = PdfDocument(writer)
            defaultAuthor?.takeIf { it.isNotBlank() }?.let {
                pdfDoc.documentInfo.author = it
            }
            val doc = Document(pdfDoc)
            doc.setMargins(0f, 0f, 0f, 0f)

            val colorMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(0f)
            }
            val grayPaint = Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            }

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = 2.0f // High quality rendering
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val colorBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val colorCanvas = Canvas(colorBitmap)
                colorCanvas.drawColor(Color.WHITE)
                page.render(colorBitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val grayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val grayCanvas = Canvas(grayBitmap)
                grayCanvas.drawBitmap(colorBitmap, 0f, 0f, grayPaint)
                colorBitmap.recycle()

                val stream = ByteArrayOutputStream()
                grayBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                grayBitmap.recycle()

                val imgData = ImageDataFactory.create(stream.toByteArray())
                val img = Image(imgData)
                val pageSize = PageSize(page.width.toFloat(), page.height.toFloat())
                pdfDoc.addNewPage(pageSize)
                img.setFixedPosition(i + 1, 0f, 0f)
                img.scaleToFit(pageSize.width, pageSize.height)
                doc.add(img)
            }

            doc.close()
            renderer.close()
            pfd.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get number of pages in a PDF */
    fun getPageCount(file: File): Int {
        return try {
            val reader = PdfReader(file)
            val pdfDoc = PdfDocument(reader)
            val count = pdfDoc.numberOfPages
            pdfDoc.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /** Read PDF metadata */
    fun readMetadata(file: File): Map<String, String?> {
        return try {
            val reader = PdfReader(file)
            val pdfDoc = PdfDocument(reader)
            val info = pdfDoc.documentInfo
            val result = mapOf(
                "title" to info.title,
                "author" to info.author,
                "subject" to info.subject,
                "keywords" to info.keywords,
                "creator" to info.creator
            )
            pdfDoc.close()
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    enum class ApplyTo { ALL, EVEN, ODD, CUSTOM }
    enum class NumberPosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
}
