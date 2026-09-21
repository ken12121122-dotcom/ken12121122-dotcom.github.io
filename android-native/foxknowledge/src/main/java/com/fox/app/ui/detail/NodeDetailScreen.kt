package com.fox.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fox.app.FoxDependencies
import com.fox.app.data.db.EdgeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    nodeId: String,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val deps = FoxDependencies.get(LocalContext.current)
    val viewModel: NodeDetailViewModel = viewModel(
        key = nodeId,
        factory = viewModelFactory {
            initializer {
                NodeDetailViewModel(
                    nodeId = nodeId,
                    nodeDao = deps.database.nodeDao(),
                    edgeDao = deps.database.edgeDao(),
                    contentDao = deps.database.contentDao(),
                    usageLogDao = deps.database.appUsageLogDao(),
                )
            }
        },
    )

    val node by viewModel.node.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoingEdges.collectAsStateWithLifecycle()
    val incoming by viewModel.incomingEdges.collectAsStateWithLifecycle()

    fun followEdge(targetNodeId: String) {
        viewModel.onEdgeFollowed(targetNodeId)
        onNavigateToDetail(targetNodeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(node?.title ?: nodeId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                node?.let { n ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${n.documentType} · ${n.status} · ${n.reviewStatus}", style = MaterialTheme.typography.labelMedium)
                            if (n.tags.isNotBlank()) {
                                Text(n.tags, style = MaterialTheme.typography.labelSmall)
                            }
                            n.owner?.let { Text("owner: $it", style = MaterialTheme.typography.labelSmall) }
                            n.confidence?.let { Text("confidence: $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            items(contents, key = { it.contentId }) { section ->
                Column {
                    Text(section.section, style = MaterialTheme.typography.titleMedium)
                    Text(section.content, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (outgoing.isNotEmpty()) {
                item { Text("關係（由此節點出發）", style = MaterialTheme.typography.titleMedium) }
                items(outgoing, key = { it.edgeId }) { edge ->
                    EdgeRow(edge = edge, label = "${edge.relation} → ${edge.toNode}", onClick = { followEdge(edge.toNode) })
                }
            }

            if (incoming.isNotEmpty()) {
                item { Text("關係（指向此節點）", style = MaterialTheme.typography.titleMedium) }
                items(incoming, key = { it.edgeId }) { edge ->
                    EdgeRow(edge = edge, label = "${edge.fromNode} ${edge.relation} →", onClick = { followEdge(edge.fromNode) })
                }
            }
        }
    }
}

@Composable
private fun EdgeRow(edge: EdgeEntity, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (edge.targetResolved) label else "$label（未解析）") },
        modifier = Modifier.fillMaxWidth(),
    )
}
