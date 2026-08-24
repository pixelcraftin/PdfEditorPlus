package com.pixelcraftin.pdfeditorplus.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelcraftin.pdfeditorplus.R
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import com.pixelcraftin.pdfeditorplus.data.model.ToolCategory
import com.pixelcraftin.pdfeditorplus.data.model.ToolItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HistoryDatabase.getInstance(application).historyDao()

    val recentFiles: StateFlow<List<HistoryItem>> = dao.getRecentHistory(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickTools: List<ToolItem> = listOf(
        ToolItem("image_to_pdf", "Image to PDF", "", ToolCategory.CONVERT,
            R.drawable.ic_image_to_pdf, R.drawable.bg_icon_teal, R.color.icon_teal,
            R.id.imageToPdfFragment),
        ToolItem("pdf_to_image", "PDF to Image", "", ToolCategory.CONVERT,
            R.drawable.ic_pdf_to_image, R.drawable.bg_icon_teal, R.color.icon_teal,
            R.id.pdfToImageFragment),
        ToolItem("extract_images", "Extract", "", ToolCategory.CONVERT,
            R.drawable.ic_extract, R.drawable.bg_icon_orange, R.color.icon_orange,
            R.id.extractImagesFragment),
        ToolItem("unlock_pdf", "Unlock PDF", "", ToolCategory.SECURE,
            R.drawable.ic_unlock, R.drawable.bg_icon_purple, R.color.icon_purple,
            R.id.unlockPdfFragment),
        ToolItem("image_compressor", "Image Compressor", "", ToolCategory.OPTIMIZE,
            R.drawable.ic_image_compress, R.drawable.bg_icon_orange, R.color.icon_orange,
            R.id.imageCompressorFragment),
        ToolItem("round_crop", "Image Round Cropping", "", ToolCategory.OPTIMIZE,
            R.drawable.ic_round_crop, R.drawable.bg_icon_orange, R.color.icon_orange,
            R.id.imageRoundCroppingFragment)
    )

    val coreTools: List<ToolItem> = listOf(
        ToolItem("merge_pdf", "Merge PDF", "COMBINE PDFS", ToolCategory.EDIT,
            R.drawable.ic_merge, R.drawable.bg_icon_blue, R.color.icon_blue,
            R.id.mergePdfFragment),
        ToolItem("split_pdf", "Split PDF", "PULL OUT PAGES", ToolCategory.EDIT,
            R.drawable.ic_split, R.drawable.bg_icon_orange, R.color.icon_orange,
            R.id.splitPdfFragment),
        ToolItem("compress_pdf", "Compress PDF", "REDUCE SIZE", ToolCategory.OPTIMIZE,
            R.drawable.ic_compress, R.drawable.bg_icon_amber, R.color.icon_amber,
            R.id.compressPdfFragment),
        ToolItem("protect_pdf", "Protect PDF", "ENCRYPT FILE", ToolCategory.SECURE,
            R.drawable.ic_protect, R.drawable.bg_icon_purple, R.color.icon_purple,
            R.id.protectPdfFragment)
    )
}
