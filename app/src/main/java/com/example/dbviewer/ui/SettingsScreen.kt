package com.example.dbviewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sqliteviewer.BuildConfig
import com.example.dbviewer.presentation.UpdateState
import com.example.dbviewer.presentation.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    updateViewModel: UpdateViewModel,
    onNavigateBack: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val updateState by updateViewModel.updateState.collectAsState()
    val autoCheckEnabled by updateViewModel.autoCheckEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Update Settings Section
            Text(
                text = "更新",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
            )

            HorizontalDivider()

            // Auto Check Updates Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动检查更新",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "启动时自动检查新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = { updateViewModel.setAutoCheckEnabled(it) }
                )
            }

            // Manual Check Button
            OutlinedButton(
                onClick = { updateViewModel.checkForUpdates() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = updateState !is UpdateState.Checking
            ) {
                Icon(Icons.Default.Update, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (updateState) {
                        is UpdateState.Checking -> "检查中..."
                        else -> "手动检查更新"
                    }
                )
            }

            // Update Status Message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val state = updateState) {
                    is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("检查中...", style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateState.Available -> Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "发现新版本 ${state.info.version}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "当前版本: ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is UpdateState.NoUpdate -> Text(
                        text = "已是最新版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is UpdateState.Error -> Text(
                        text = "检查失败: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
            }

            Spacer(Modifier.height(16.dp))

            // About Section
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
            )

            HorizontalDivider()

            // Version Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "版本",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // GitHub Link
            OutlinedButton(
                onClick = onOpenGitHub,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("访问 GitHub")
            }

            Spacer(Modifier.height(8.dp))

            // Privacy Note
            Text(
                text = "隐私说明：更新检查功能仅在用户启用时向 GitHub API 发起请求，不上传任何用户数据或使用信息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
