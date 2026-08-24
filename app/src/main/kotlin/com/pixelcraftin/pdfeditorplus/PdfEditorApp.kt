package com.pixelcraftin.pdfeditorplus

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences

class PdfEditorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val theme = AppPreferences(this).getThemeSync()
        when (theme) {
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
