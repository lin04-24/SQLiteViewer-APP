package com.example.dbviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.presentation.DbViewModel
import com.example.dbviewer.presentation.HistoryEntry
import com.example.dbviewer.ui.components.EmptyHint
import com.example.dbviewer.ui.components.MetaChip
import com.example.dbviewer.ui.theme.DbColors

@Composable
fun HistoryScreen(
    state: DbViewModel.UiState,
    onUse: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxSize().background(DbColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(DbColors.Surface).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp), tint = DbColors.TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text("查询历史", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DbColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (state.history.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空", fontSize = 12.sp, color = DbColors.TextSecondary) }
            }
        }
        HorizontalDivider(color = DbColors.Divider)
        if (state.history.isEmpty()) {
            EmptyHint("还没有执行过查询")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.history.reversed(), key = { "${it.at}-${it.sql.hashCode()}" }) { entry ->
                    HistoryCard(entry, onUse) { clipboard.setText(AnnotatedString(entry.sql)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onUse: (String) -> Unit, onCopy: () -> Unit) {
    Surface(color = DbColors.Surface, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
            Text(
                text = entry.sql,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (entry.error == null) DbColors.TextPrimary else DbColors.TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaChip(formatTime(entry.at))
                MetaChip(formatElapsed(entry.elapsedMs))
                if (entry.rowCount != null) MetaChip("${entry.rowCount} 行")
                entry.error?.let {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(13.dp), tint = DbColors.Error)
                    Spacer(Modifier.width(4.dp))
                    Text("失败", fontSize = 10.sp, color = DbColors.Error)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制 SQL", modifier = Modifier.size(14.dp), tint = DbColors.TextMuted)
                }
                IconButton(onClick = { onUse(entry.sql) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "载入编辑器", modifier = Modifier.size(16.dp), tint = DbColors.Accent)
                }
            }
            entry.error?.let { message ->
                Text(message, fontSize = 10.5.sp, color = DbColors.Error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
