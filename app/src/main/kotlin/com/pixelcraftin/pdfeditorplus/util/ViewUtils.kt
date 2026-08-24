package com.pixelcraftin.pdfeditorplus.util

import android.view.View
import android.view.animation.AnimationUtils
import com.pixelcraftin.pdfeditorplus.R

object ViewUtils {
    fun startPulseGlow(view: View?) {
        view?.let {
            val anim = AnimationUtils.loadAnimation(it.context, R.anim.pulse_glow)
            it.startAnimation(anim)
        }
    }
}
