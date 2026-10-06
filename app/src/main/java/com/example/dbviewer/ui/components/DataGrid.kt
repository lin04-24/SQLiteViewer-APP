package com.example.dbviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.dbviewer.data.ColumnInfo
import com.example.dbviewer.data.GridRow
import com.example.dbviewer.ui.theme.DbColors
import com.example.dbviewer.ui.visualLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

data class GridColumn(val title: String, val type: String?, val width: Dp, val numeric: Boolean, val primaryKey: Boolean)

private val HEADER_HEIGHT = 52.dp
private val ROW_HEIGHT = 44.dp
private val SELECTION_WIDTH = 46.dp
private const val CHAR_WIDTH_DP = 7.9f

/** Builds grid columns from the table schema: type comes from the declaration, width from the widest sample. */
suspend fun gridColumnsFromSchema(columns: List<ColumnInfo>, rows: List<GridRow>): List<GridColumn> = withContext(Dispatchers.Default) {
    val limitedRows = rows.take(50)
    columns.mapIndexed { index, info ->
        val title = info.name ?: "col$index"
        val type = info.type?.takeIf { it.isNotBlank() }
        val samples = limitedRows.map { it.values.getOrNull(index) }
        GridColumn(
            title = title,
            type = type,
            width = columnWidth(title, type, samples),
            numeric = isNumericType(type) || detectNumeric(samples),
            primaryKey = info.primaryKeyOrder > 0,
        )
    }
}

/** Builds grid columns for arbitrary query results, where only the column names are known. */
suspend fun gridColumnsFromNames(names: List<String>, rows: List<List<String?>>): List<GridColumn> = withContext(Dispatchers.Default) {
    val limitedRows = rows.take(50)
    names.mapIndexed { index, name ->
        val samples = limitedRows.map { it.getOrNull(index) }
        GridColumn(
            title = name.ifBlank { "col$index" },
            type = null,
            width = columnWidth(name, null, samples),
            numeric = detectNumeric(samples),
            primaryKey = false,
        )
    }
}

private fun columnWidth(title: String, type: String?, samples: List<String?>): Dp {
    val valueChars = samples.mapNotNull { it?.let(::visualLength) }.maxOrNull() ?: 0
    val headerChars = maxOf(visualLength(title), ((type?.length ?: 0) * 3) / 4)
    val chars = maxOf(valueChars, headerChars).coerceIn(4, 28)
    return (chars * CHAR_WIDTH_DP).dp + 28.dp
}

private fun isNumericType(type: String?): Boolean {
    val upper = type?.uppercase() ?: return false
    return upper.contains("INT") || upper.contains("REAL") || upper.contains("FLOA") || upper.contains("DOUB") || upper.contains("NUM") || upper.contains("DEC")
}

private fun detectNumeric(samples: List<String?>): Boolean {
    val present = samples.filterNotNull()
    if (present.isEmpty()) return false
    return present.count { it.toDoubleOrNull() != null } * 10 >= present.size * 8
}

/**
 * Monospace table with a pinned header, one shared horizontal scroll offset for header and rows,
 * optional row selection and a tap target that opens the full row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataGrid(
    columns: List<GridColumn>,
    rows: List<GridRow>,
    modifier: Modifier = Modifier,
    selection: Set<Int> = emptySet(),
    onToggleRow: ((Int) -> Unit)? = null,
    onToggleAll: (() -> Unit)? = null,
    onRowClick: ((GridRow) -> Unit)? = null,
    onRowLongClick: ((GridRow) -> Unit)? = null,
    emptyText: String = "没有可显示的数据",
    onScrollNearBottom: (() -> Unit)? = null,
) {
    val horizontal = rememberScrollState()
    val listState = rememberLazyListState()
    val allSelected = rows.isNotEmpty() && rows.all { it.position in selection }

    // 检测滚动位置，当接近底部时触发预加载
    LaunchedEffect(listState, rows.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisibleIndex >= totalItems - 5
        }.distinctUntilChanged().collect { nearBottom ->
            if (nearBottom) onScrollNearBottom?.invoke()
        }
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).background(DbColors.SurfaceElevated).horizontalScroll(horizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggleAll != null) {
                Box(Modifier.width(SELECTION_WIDTH).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onToggleAll() },
                        modifier = Modifier.size(22.dp),
                        colors = gridCheckboxColors(),
                    )
                }
            }
            columns.forEach { column -> HeaderCell(column) }
        }
        HorizontalDivider(color = DbColors.Divider)
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text(emptyText, color = DbColors.TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth(), state = listState) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.position },
                    contentType = { _, _ -> "data-row" }
                ) { index, row ->
                    val selected = row.position in selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .background(
                                when {
                                    selected -> DbColors.Accent.copy(alpha = 0.13f)
                                    index % 2 == 1 -> DbColors.Surface
                                    else -> Color.Transparent
                                }
                            )
                            .horizontalScroll(horizontal)
                            .combinedClickable(
                                enabled = onRowClick != null || onRowLongClick != null,
                                onClick = { onRowClick?.invoke(row) },
                                onLongClick = { onRowLongClick?.invoke(row) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onToggleRow != null) {
                            Box(Modifier.width(SELECTION_WIDTH).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleRow(row.position) },
                                    modifier = Modifier.size(22.dp),
                                    colors = gridCheckboxColors(),
                                )
                            }
                        }
                        columns.forEachIndexed { columnIndex, column -> Cell(row.values.getOrNull(columnIndex), column) }
                    }
                    HorizontalDivider(color = DbColors.Divider)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(column: GridColumn) {
    Column(
        modifier = Modifier.width(column.width).fillMaxHeight().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (column.primaryKey) {
                Icon(Icons.Default.Key, contentDescription = "主键", modifier = Modifier.size(11.dp), tint = DbColors.Key)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = column.title,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DbColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = column.type?.uppercase() ?: "",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = DbColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Cell(value: String?, column: GridColumn) {
    val isNull = value == null
    val textColor = when {
        isNull -> DbColors.Null
        column.numeric -> DbColors.Number
        else -> DbColors.TextPrimary
    }

    Box(
        modifier = Modifier
            .width(column.width)
            .padding(horizontal = 12.dp)
            .drawWithCache {
                // 缓存绘制操作，避免每次重组都重新测量
                onDrawBehind { }
            }
    ) {
        Text(
            text = value ?: "NULL",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontStyle = if (isNull) FontStyle.Italic else FontStyle.Normal,
            color = textColor,
        )
    }
}

@Composable
private fun gridCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = DbColors.Accent,
    uncheckedColor = DbColors.TextMuted,
    checkmarkColor = Color(0xFF08101F),
)
