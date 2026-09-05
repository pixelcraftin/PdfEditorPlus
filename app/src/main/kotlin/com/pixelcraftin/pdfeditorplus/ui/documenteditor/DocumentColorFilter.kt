package com.pixelcraftin.pdfeditorplus.ui.documenteditor

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

enum class ColorFilterType(val displayName: String) {
    ORIGINAL("Original"),
    VIVID_LIGHT("Vivid Light"),
    CONTRAST_BW("Contrast B&W"),
    SHARP_BLACK("Sharp Black"),
    OCV_COLOR("OCV Color"),
    BW("B&W"),
    CARBON_BLACK("Carbon Black"),
    OCV_BLACK("OCV Black"),
    GRAY("Gray"),
    COLOR_POP("Color Pop"),
    VIBRANT("Vibrant"),
    SOFT_TONE("Soft Tone"),
    AUTO("Auto");

    fun getColorMatrix(brightnessDelta: Float = 0f): ColorMatrix {
        val matrix = ColorMatrix()
        when (this) {
            ORIGINAL -> {
                matrix.reset()
            }
            VIVID_LIGHT -> {
                // Brightened with boosted exposure and pleasant saturation
                val brightness = 25f
                val contrast = 1.15f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                matrix.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                val satMatrix = ColorMatrix().apply { setSaturation(1.2f) }
                matrix.postConcat(satMatrix)
            }
            CONTRAST_BW -> {
                // Document scanner clean high-contrast black and white
                matrix.setSaturation(0f)
                val contrast = 1.8f
                val brightness = -10f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(contrastMatrix)
            }
            SHARP_BLACK -> {
                // High-contrast binarization with sharpened thresholding
                matrix.setSaturation(0f)
                val contrast = 2.4f
                val brightness = -25f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(contrastMatrix)
            }
            OCV_COLOR -> {
                // Adaptive histogram-like saturation boost with clarity contrast
                val contrast = 1.3f
                val brightness = 15f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                matrix.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                val satMatrix = ColorMatrix().apply { setSaturation(1.45f) }
                matrix.postConcat(satMatrix)
            }
            BW -> {
                // Standard linear luminance monochrome matrix
                matrix.set(floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            CARBON_BLACK -> {
                // Heavy black document scan matrix with aggressive white background drop
                matrix.setSaturation(0f)
                val contrast = 2.8f
                val brightness = -40f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(contrastMatrix)
            }
            OCV_BLACK -> {
                // Adaptive thresholding monochrome filter
                matrix.setSaturation(0f)
                val contrast = 1.6f
                val brightness = 5f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(contrastMatrix)
            }
            GRAY -> {
                // Standard balanced 8-bit grayscale matrix
                matrix.setSaturation(0f)
            }
            COLOR_POP -> {
                // High saturation (+80%) with deep contrast on primary colors
                val contrast = 1.25f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + 10f
                matrix.set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                val satMatrix = ColorMatrix().apply { setSaturation(1.8f) }
                matrix.postConcat(satMatrix)
            }
            VIBRANT -> {
                // Enhanced saturation & crisp dynamic colors
                matrix.setSaturation(1.5f)
                val contrast = 1.2f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + 10f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(contrastMatrix)
            }
            SOFT_TONE -> {
                // Warm, reduced eye strain tone with smooth paper tint
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.05f, 0f, 0f, 0f, 15f,
                    0f, 0.98f, 0f, 0f, 10f,
                    0f, 0f, 0.88f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                val satMatrix = ColorMatrix().apply { setSaturation(0.9f) }
                warmMatrix.postConcat(satMatrix)
                matrix.set(warmMatrix)
            }
            AUTO -> {
                // Auto document enhancement: clear text, balanced white background
                val contrast = 1.35f
                val brightness = 18f
                val scale = contrast
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val autoMatrix = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                val satMatrix = ColorMatrix().apply { setSaturation(1.1f) }
                autoMatrix.postConcat(satMatrix)
                matrix.set(autoMatrix)
            }
        }

        // Apply quick brightness adjustment if non-zero
        if (brightnessDelta != 0f) {
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, brightnessDelta,
                0f, 1f, 0f, 0f, brightnessDelta,
                0f, 0f, 1f, 0f, brightnessDelta,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(brightnessMatrix)
        }

        return matrix
    }

    fun getColorFilter(brightnessDelta: Float = 0f): ColorMatrixColorFilter {
        return ColorMatrixColorFilter(getColorMatrix(brightnessDelta))
    }
}
