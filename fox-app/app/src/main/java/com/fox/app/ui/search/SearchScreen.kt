package com.fox.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import com.fox.app.FoxApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as FoxApplication
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(app.database.nodeDao()) }
        },
    )

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜尋") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("標題／標籤／內文") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (isSearching) {
                Text("搜尋中…", modifier = Modifier.padding(top = 12.dp))
            } else if (query.isNotBlank() && results.isEmpty()) {
                Text("沒有符合的節點", modifier = Modifier.padding(top = 12.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.nodeId }) { node ->
                    ListItem(
                        headlineContent = { Text(node.title) },
                        supportingContent = { Text("${node.documentType} · ${node.tags}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDetail(node.nodeId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
