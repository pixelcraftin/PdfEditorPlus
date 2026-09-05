package com.pixelcraftin.pdfeditorplus.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {

    private const val AUTHORITY = "com.pixelcraftin.pdfeditorplus.fileprovider"
    private const val OUTPUT_DIR = "PdfEditor+"

    /** Returns the shared output directory in app external files */
    fun getOutputDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), OUTPUT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Returns a Downloads-facing output dir (visible to user in file managers) */
    fun getPublicDownloadsDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            OUTPUT_DIR
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Copies an input stream to a temp file in our app cache */
    fun copyToCache(context: Context, input: InputStream, suffix: String): File {
        val temp = File.createTempFile("pdf_tmp_", suffix, context.cacheDir)
        temp.outputStream().use { out -> input.copyTo(out) }
        return temp
    }

    /** Resolves a content URI to a temporary file */
    fun uriToFile(context: Context, uri: Uri, suffix: String = ".pdf"): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copyToCache(context, input, suffix)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Saves a processed file to the public Downloads / PdfEditor+ directory and notifies the system */
    fun saveFileToDownloads(context: Context, file: File, targetFileName: String? = null): Result<String> {
        return try {
            val finalName = if (!targetFileName.isNullOrBlank()) {
                val ext = file.extension.ifEmpty { "pdf" }
                if (targetFileName.endsWith(".$ext", ignoreCase = true)) targetFileName else "$targetFileName.$ext"
            } else {
                file.name
            }
            val mimeType = getMimeType(finalName)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$OUTPUT_DIR")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Result.failure(Exception("Failed to create MediaStore entry"))
                resolver.openOutputStream(uri)?.use { outStream ->
                    file.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                val targetPath = "Downloads/$OUTPUT_DIR/$finalName"
                Result.success(targetPath)
            } else {
                val publicDir = getPublicDownloadsDir()
                val destFile = File(publicDir, finalName)
                file.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                Result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** Prompts user to rename file before saving to Downloads */
    fun promptSaveToDownloads(
        context: Context,
        file: File,
        onSaved: ((String) -> Unit)? = null
    ) {
        val baseName = file.nameWithoutExtension
        val ext = file.extension.ifEmpty { "pdf" }

        val input = android.widget.EditText(context).apply {
            setText(baseName)
            setSelectAllOnFocus(true)
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            background = androidx.core.content.ContextCompat.getDrawable(context, com.pixelcraftin.pdfeditorplus.R.drawable.bg_input_field)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, com.pixelcraftin.pdfeditorplus.R.color.text_primary))
            textSize = 15f
        }

        val container = android.widget.FrameLayout(context).apply {
            val marginH = (22 * context.resources.displayMetrics.density).toInt()
            val marginV = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(marginH, marginV, marginH, marginV)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Save to Downloads 💾")
            .setMessage("Edit file name (extension .$ext will be kept):")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val enteredName = input.text.toString().trim()
                val customName = if (enteredName.isNotBlank()) enteredName else baseName
                saveFileToDownloads(context, file, customName).onSuccess { path ->
                    android.widget.Toast.makeText(context, "Saved to $path", android.widget.Toast.LENGTH_SHORT).show()
                    onSaved?.invoke(path)
                }.onFailure { e ->
                    android.widget.Toast.makeText(context, "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Prompts user to rename batch files (prefix) before saving to Downloads */
    fun promptSaveBatchToDownloads(
        context: Context,
        files: List<File>,
        defaultPrefix: String = "extracted",
        onSaved: ((Int) -> Unit)? = null
    ) {
        if (files.isEmpty()) return

        val input = android.widget.EditText(context).apply {
            setText(defaultPrefix)
            setSelectAllOnFocus(true)
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            background = androidx.core.content.ContextCompat.getDrawable(context, com.pixelcraftin.pdfeditorplus.R.drawable.bg_input_field)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, com.pixelcraftin.pdfeditorplus.R.color.text_primary))
            textSize = 15f
        }

        val container = android.widget.FrameLayout(context).apply {
            val marginH = (22 * context.resources.displayMetrics.density).toInt()
            val marginV = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(marginH, marginV, marginH, marginV)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Save All to Downloads 💾")
            .setMessage("Enter base file name for ${files.size} items:")
            .setView(container)
            .setPositiveButton("Save All") { _, _ ->
                val prefix = input.text.toString().trim().ifBlank { defaultPrefix }
                var savedCount = 0
                files.forEachIndexed { idx, f ->
                    val customName = "${prefix}_${idx + 1}.${f.extension}"
                    saveFileToDownloads(context, f, customName).onSuccess { savedCount++ }
                }
                android.widget.Toast.makeText(context, "Saved $savedCount items to Downloads/PdfEditor+", android.widget.Toast.LENGTH_SHORT).show()
                onSaved?.invoke(savedCount)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Returns a content URI via FileProvider */
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /** Open a file using the system viewer */
    fun openFile(context: Context, file: File) {
        val uri = getUriForFile(context, file)
        val mime = getMimeType(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }

    /** Share a file */
    fun shareFile(context: Context, file: File) {
        val uri = getUriForFile(context, file)
        val mime = getMimeType(file.name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    /** Get file MIME type from extension */
    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
    }

    /** Format file size in bytes to human-readable string */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /** Get display name from a URI */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
            }
        }
        if (name == "unknown") {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    /** Get file size from a URI */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    /** Generate a unique output filename */
    fun generateOutputName(toolName: String, extension: String = "pdf"): String {
        val timestamp = System.currentTimeMillis()
        val sanitized = toolName.replace(" ", "_").lowercase()
        return "${sanitized}_$timestamp.$extension"
    }
}
