package com.example.dbviewer.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.dbviewer.data.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "发现新版本",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Version Info
                Text(
                    text = updateInfo.versionName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "版本 ${updateInfo.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Changelog
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = updateInfo.changelog.ifBlank { "无更新说明" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://github.com/lin04-24/SQLiteViewer-APP/releases")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("查看详情")
                    }

                    Button(
                        onClick = { onDownload(updateInfo.downloadUrl) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下载更新")
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("稍后提醒")
                }
            }
        }
    }
}

@Composable
fun FirstTimeUpdateDialog(
    onEnableAutoCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Update,
                contentDescription = null
            )
        },
        title = {
            Text("启用自动更新检查")
        },
        text = {
            Column {
                Text("SQLite Viewer 可以在启动时自动检查新版本。")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "更新检查仅向 GitHub API 发起单次请求，不上传任何用户数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onEnableAutoCheck) {
                Text("启用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不启用")
            }
        }
    )
}
