package com.example.dbviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.data.GridRow
import com.example.dbviewer.data.SortDirection
import com.example.dbviewer.presentation.DbViewModel
import com.example.dbviewer.ui.components.DataGrid
import com.example.dbviewer.ui.components.EmptyHint
import com.example.dbviewer.ui.components.LoadingBox
import com.example.dbviewer.ui.components.MetaChip
import com.example.dbviewer.ui.components.ReadOnlyBadge
import com.example.dbviewer.ui.components.RowDetailSheet
import com.example.dbviewer.ui.components.ToolbarAction
import com.example.dbviewer.ui.components.gridColumnsFromNames
import com.example.dbviewer.ui.theme.DbColors

@Composable
fun QueryScreen(
    vm: DbViewModel,
    state: DbViewModel.UiState,
    onExport: (ExportRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<GridRow?>(null) }
    val clipboard = LocalClipboardManager.current
    var columns by remember { mutableStateOf(emptyList<com.example.dbviewer.ui.components.GridColumn>()) }
    LaunchedEffect(state.queryColumns, state.queryRows) {
        columns = gridColumnsFromNames(state.queryColumns, state.queryRows)
    }
    val displayValues = remember(
        state.queryRows,
        state.queryColumns,
        state.querySortColumn,
        state.querySortDirection,
    ) {
        val sortIndex = state.querySortColumn?.let { state.queryColumns.indexOf(it) } ?: -1
        if (sortIndex < 0 || state.querySortDirection == SortDirection.NONE) {
            state.queryRows
        } else {
            state.queryRows.sortedWith { left, right ->
                val leftValue = left.getOrNull(sortIndex)
                val rightValue = right.getOrNull(sortIndex)
                val comparison = when {
                    leftValue == null && rightValue == null -> 0
                    leftValue == null -> -1
                    rightValue == null -> 1
                    else -> leftValue.compareTo(rightValue, ignoreCase = true)
                }
                if (state.querySortDirection == SortDirection.ASC) comparison else -comparison
            }
        }
    }
    val rows = remember(displayValues) { displayValues.mapIndexed { index, values -> GridRow(position = index, id = null, values = values) } }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(DbColors.Surface).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp), tint = DbColors.Accent)
                Spacer(Modifier.width(7.dp))
                Text("SQL 查询", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DbColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                ReadOnlyBadge()
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.sqlDraft,
                onValueChange = vm::setSqlDraft,
                modifier = Modifier.fillMaxWidth().height(132.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = DbColors.TextPrimary),
                placeholder = { Text("SELECT * FROM table_name LIMIT 100;", fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = DbColors.TextMuted) },
                shape = RoundedCornerShape(9.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DbColors.Accent,
                    unfocusedBorderColor = DbColors.Border,
                    focusedContainerColor = DbColors.Background,
                    unfocusedContainerColor = DbColors.Background,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.execute() },
                    enabled = !state.queryRunning,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DbColors.Accent, contentColor = Color(0xFF08101F), disabledContainerColor = DbColors.SurfaceHigh, disabledContentColor = DbColors.TextMuted),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.queryRunning) "执行中…" else "执行", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { vm.setSqlDraft("") }, enabled = state.sqlDraft.isNotEmpty()) {
                    Text("清空", fontSize = 12.sp, color = DbColors.TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.sqlDraft)) }, enabled = state.sqlDraft.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制 SQL", fontSize = 12.sp, color = DbColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.queryRan) {
                    MetaChip("耗时 ${formatElapsed(state.queryElapsedMs)}")
                    if (state.queryError == null) MetaChip("${state.queryRows.size} 行")
                    if (state.queryTruncated) MetaChip("结果已截断", accent = true)
                } else {
                    Text("仅允许只读的 SELECT / CTE / PRAGMA 语句", fontSize = 11.sp, color = DbColors.TextMuted)
                }
            }
            state.queryError?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(message, color = DbColors.Error, fontSize = 11.5.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = DbColors.Divider)
        when {
            state.queryRunning && rows.isEmpty() -> LoadingBox(Modifier.weight(1f))
            rows.isNotEmpty() -> {
                Row(
                    modifier = Modifier.fillMaxWidth().height(42.dp).background(DbColors.Surface).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("结果", fontSize = 12.sp, color = DbColors.TextSecondary, modifier = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.weight(1f))
                    ToolbarAction(Icons.Default.Download, "导出 CSV") {
                        onExport(ExportRequest(exportFileName("query", "csv"), "text/csv", buildCsv(state.queryColumns, state.queryRows)))
                    }
                    ToolbarAction(Icons.Default.ContentCopy, "复制 JSON") {
                        clipboard.setText(AnnotatedString(buildJson(state.queryColumns, state.queryRows)))
                    }
                }
                HorizontalDivider(color = DbColors.Divider)
                DataGrid(
                    columns = columns,
                    rows = rows,
                    modifier = Modifier.weight(1f),
                    onRowClick = { detail = it },
                    onRowLongClick = { clipboard.setText(AnnotatedString(buildJson(state.queryColumns, listOf(it.values)))) },
                    emptyText = "查询没有返回行",
                    // 查询结果的交互功能
                    sortColumn = state.querySortColumn,
                    sortDirection = state.querySortDirection,
                    pinnedColumns = state.queryPinnedColumns,
                    columnWidths = state.queryColumnWidths,
                    onSort = vm::toggleQuerySort,
                    onTogglePin = vm::toggleQueryPinColumn,
                    onResizeColumn = vm::setQueryColumnWidth,
                )
            }
            state.queryRan && state.queryError == null -> Box(Modifier.weight(1f)) { EmptyHint("查询没有返回行") }
            !state.queryRan -> Box(Modifier.weight(1f)) { EmptyHint("输入只读查询语句后点击执行") }
            else -> Box(Modifier.weight(1f))
        }
    }
    detail?.let { row -> RowDetailSheet(columns = columns, row = row, onDismiss = { detail = null }) }
}
