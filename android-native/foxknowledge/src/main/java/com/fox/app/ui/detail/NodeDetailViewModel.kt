package com.fox.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.app.data.db.AppUsageLogDao
import com.fox.app.data.db.AppUsageLogEntity
import com.fox.app.data.db.ContentDao
import com.fox.app.data.db.ContentEntity
import com.fox.app.data.db.EdgeDao
import com.fox.app.data.db.EdgeEntity
import com.fox.app.data.db.NodeDao
import com.fox.app.data.db.NodeEntity
import com.fox.app.data.db.UsageAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NodeDetailViewModel(
    private val nodeId: String,
    private val nodeDao: NodeDao,
    edgeDao: EdgeDao,
    contentDao: ContentDao,
    private val usageLogDao: AppUsageLogDao,
) : ViewModel() {

    private val _node = MutableStateFlow<NodeEntity?>(null)
    val node: StateFlow<NodeEntity?> = _node.asStateFlow()

    val contents: StateFlow<List<ContentEntity>> = contentDao.observeForNode(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoingEdges: StateFlow<List<EdgeEntity>> = edgeDao.observeOutgoing(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomingEdges: StateFlow<List<EdgeEntity>> = edgeDao.observeIncoming(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _node.value = nodeDao.get(nodeId)
            usageLogDao.insert(
                AppUsageLogEntity(
                    nodeId = nodeId,
                    action = UsageAction.OPENED,
                    query = null,
                    agentId = null,
                    sessionId = null,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onEdgeFollowed(targetNodeId: String) {
        viewModelScope.launch {
            usageLogDao.insert(
                AppUsageLogEntity(
                    nodeId = targetNodeId,
                    action = UsageAction.LINKED,
                    query = "from:$nodeId",
                    agentId = null,
                    sessionId = null,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }
}
