package com.fox.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fox.app.FoxDependencies
import com.fox.app.data.db.NodeEntity
import com.fox.app.data.drive.buildFoxGoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val deps = FoxDependencies.get(context)
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(deps.database.nodeDao(), deps.syncRepository) }
        },
    )

    val recentNodes by viewModel.recentNodes.collectAsStateWithLifecycle()
    val pendingNodes by viewModel.pendingNodes.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncSummary by viewModel.lastSyncSummary.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()

    val signInClient = remember(context) { buildFoxGoogleSignInClient(context) }
    var signedInAccount by remember {
        mutableStateOf<GoogleSignInAccount?>(GoogleSignIn.getLastSignedInAccount(context))
    }
    var signInError by remember { mutableStateOf<String?>(null) }
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            signedInAccount = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            signInError = null
        } catch (e: ApiException) {
            signInError = "登入失敗（code ${e.statusCode}）"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("FOX 狐狸") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateToSearch, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  搜尋知識庫")
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Google 帳號", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        val account = signedInAccount
                        if (account == null) {
                            Text("尚未登入，無法同步 Drive 知識庫。")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { signInLauncher.launch(signInClient.signInIntent) }) {
                                Text("使用 Google 帳號登入")
                            }
                        } else {
                            Text("已登入：${account.email ?: account.displayName ?: "未知帳號"}")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                signInClient.signOut().addOnCompleteListener { signedInAccount = null }
                            }) {
                                Text("登出")
                            }
                        }
                        signInError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "登入失敗：$err（若 Google Cloud Console 尚未替此 App 設定 OAuth client，這是預期中的失敗）",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("知識庫狀態", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("節點總數：$totalCount")
                        Text("待處理事項：${pendingNodes.size}")
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Button(onClick = viewModel::syncNow, enabled = !isSyncing && signedInAccount != null) {
                                Text(if (isSyncing) "同步中…" else "立即同步 Drive")
                            }
                            if (isSyncing) {
                                Spacer(Modifier.height(0.dp))
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            }
                        }
                        if (signedInAccount == null) {
                            Text(
                                "請先登入 Google 帳號才能同步。",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                        syncSummary?.let { s ->
                            Text("上次同步：掃描 ${s.scanned}／更新 ${s.updated}／未變 ${s.unchanged}／失敗 ${s.failed}")
                        }
                        syncError?.let { err ->
                            Text("同步錯誤：$err", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (pendingNodes.isNotEmpty()) {
                item { SectionHeader("待處理事項") }
                items(pendingNodes, key = { it.nodeId }) { node ->
                    NodeRow(node, onClick = { onNavigateToDetail(node.nodeId) })
                }
            }

            item { SectionHeader("最近節點") }
            if (recentNodes.isEmpty()) {
                item { Text("尚無資料，請先執行「立即同步 Drive」。") }
            }
            items(recentNodes, key = { it.nodeId }) { node ->
                NodeRow(node, onClick = { onNavigateToDetail(node.nodeId) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
}

@Composable
private fun NodeRow(node: NodeEntity, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(node.title) },
            supportingContent = { Text("${node.documentType} · ${node.reviewStatus}") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
        androidx.compose.material3.HorizontalDivider()
    }
}
