package com.pixelcraftin.pdfeditorplus.data.model

import androidx.annotation.DrawableRes

data class OpenSourceLibrary(
    val name: String,
    val description: String,
    val license: String,
    val url: String,
    @DrawableRes val iconRes: Int,
    val iconBgRes: Int,
    val iconTintRes: Int
)
