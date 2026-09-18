package com.fox.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.app.data.db.NodeDao
import com.fox.app.data.db.NodeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Stage-1 search per KB-APP-002: local FTS over title/tags/content only.
 * Keyword+semantic+graph search is a later stage, not P0.
 */
class SearchViewModel(private val nodeDao: NodeDao) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<NodeEntity>>(emptyList())
    val results: StateFlow<List<NodeEntity>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _results.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val ftsQuery = newQuery.trim().split(Regex("\\s+")).joinToString(" ") { "$it*" }
                _results.value = nodeDao.search(ftsQuery)
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
}
