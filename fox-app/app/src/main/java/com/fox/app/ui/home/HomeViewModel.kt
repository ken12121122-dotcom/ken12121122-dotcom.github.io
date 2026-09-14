package com.fox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.app.data.db.NodeDao
import com.fox.app.data.db.NodeEntity
import com.fox.app.data.sync.SyncRepository
import com.fox.app.data.sync.SyncSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    nodeDao: NodeDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val recentNodes: StateFlow<List<NodeEntity>> = nodeDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingNodes: StateFlow<List<NodeEntity>> = nodeDao.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = nodeDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncSummary = MutableStateFlow<SyncSummary?>(null)
    val lastSyncSummary: StateFlow<SyncSummary?> = _lastSyncSummary.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            try {
                _lastSyncSummary.value = syncRepository.syncOnce()
            } catch (e: Exception) {
                _syncError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
