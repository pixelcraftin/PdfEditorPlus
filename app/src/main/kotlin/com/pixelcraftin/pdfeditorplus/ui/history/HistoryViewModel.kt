package com.pixelcraftin.pdfeditorplus.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelcraftin.pdfeditorplus.data.db.HistoryDatabase
import com.pixelcraftin.pdfeditorplus.data.model.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HistoryDatabase.getInstance(application).historyDao()
    private val _searchQuery = MutableStateFlow("")

    val historyItems: StateFlow<List<HistoryItem>> = dao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<HistoryItem>> = combine(historyItems, _searchQuery) { items, query ->
        if (query.isBlank()) items
        else items.filter {
            it.fileName.contains(query, ignoreCase = true) ||
            it.toolName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(query: String) { _searchQuery.value = query }

    fun delete(item: HistoryItem) = viewModelScope.launch { dao.delete(item) }

    fun clearAll() = viewModelScope.launch { dao.deleteAll() }

    suspend fun count(): Int = dao.count()
}
