package com.pixelcraftin.pdfeditorplus.data.model

import androidx.annotation.DrawableRes

data class ToolItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    @DrawableRes val iconRes: Int,
    val iconBgRes: Int,
    val iconTintRes: Int,
    val navActionId: Int,
    val subtitleLabel: String = ""
)

enum class ToolCategory(val displayName: String, @DrawableRes val iconRes: Int) {
    CONVERT("CONVERT", com.pixelcraftin.pdfeditorplus.R.drawable.ic_pdf_to_image),
    EDIT("EDIT", com.pixelcraftin.pdfeditorplus.R.drawable.ic_merge),
    OPTIMIZE("OPTIMIZE", com.pixelcraftin.pdfeditorplus.R.drawable.ic_compress),
    SECURE("SECURE", com.pixelcraftin.pdfeditorplus.R.drawable.ic_protect)
}
