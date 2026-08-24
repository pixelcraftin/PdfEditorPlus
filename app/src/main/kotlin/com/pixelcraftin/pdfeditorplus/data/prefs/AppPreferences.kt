package com.pixelcraftin.pdfeditorplus.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme")               // "Light", "Dark", "System"
        val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val KEY_DEFAULT_AUTHOR = stringPreferencesKey("default_author")
        val KEY_AUTO_WIPE = booleanPreferencesKey("auto_wipe_history")
        val KEY_HISTORY_LIMIT = intPreferencesKey("history_limit")  // 10,25,50,100,0=unlimited
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME] ?: "System" }

    val autoDownloadFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTO_DOWNLOAD] ?: true }

    val defaultAuthorFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_AUTHOR] ?: "" }

    val autoWipeFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTO_WIPE] ?: false }

    val historyLimitFlow: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_HISTORY_LIMIT] ?: 50 }

    fun getThemeSync(): String {
        return context.getSharedPreferences("app_settings_sync", Context.MODE_PRIVATE)
            .getString("theme", "System") ?: "System"
    }

    suspend fun getTheme(): String {
        return getThemeSync()
    }

    suspend fun setTheme(theme: String) {
        context.getSharedPreferences("app_settings_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("theme", theme)
            .apply()
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setAutoDownload(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = enabled }
    }

    suspend fun setDefaultAuthor(author: String) {
        context.dataStore.edit { it[KEY_DEFAULT_AUTHOR] = author }
    }

    suspend fun setAutoWipe(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_WIPE] = enabled }
    }

    suspend fun getDefaultAuthor(): String {
        var result = ""
        try {
            defaultAuthorFlow.collect {
                result = it
                throw CancellationException()
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun setHistoryLimit(limit: Int) {
        context.dataStore.edit { it[KEY_HISTORY_LIMIT] = limit }
    }
}
