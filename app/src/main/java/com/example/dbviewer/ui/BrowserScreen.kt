package com.example.dbviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.data.ColumnInfo
import com.example.dbviewer.data.DbObject
import com.example.dbviewer.data.DdlItem
import com.example.dbviewer.data.GridRow
import com.example.dbviewer.data.IndexInfo
import com.example.dbviewer.presentation.DbViewModel
import com.example.dbviewer.ui.components.DataGrid
import com.example.dbviewer.ui.components.EmptyHint
import com.example.dbviewer.ui.components.LoadingBox
import com.example.dbviewer.ui.components.PaginationBar
import com.example.dbviewer.ui.components.ReadOnlyBadge
import com.example.dbviewer.ui.components.RowDetailSheet
import com.example.dbviewer.ui.components.ToolbarAction
import com.example.dbviewer.ui.components.gridColumnsFromSchema
import com.example.dbviewer.ui.theme.DbColors

private val BROWSER_TABS = listOf("数据", "列", "索引", "DDL")

@Composable
fun BrowserScreen(
    vm: DbViewModel,
    state: DbViewModel.UiState,
    onExport: (ExportRequest) -> Unit,
    onOpenQuery: (String) -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    when {
        state.selectedTable != null && wide -> Row(Modifier.fillMaxSize()) {
            ObjectListPane(state, vm, Modifier.width(244.dp).fillMaxHeight(), compact = true)
            Box(Modifier.width(1.dp).fillMaxHeight().background(DbColors.Divider))
            TableBrowser(vm, state, onExport, onOpenQuery, Modifier.weight(1f))
        }
        state.selectedTable != null -> TableBrowser(vm, state, onExport, onOpenQuery, Modifier.fillMaxSize())
        else -> ObjectListPane(state, vm, Modifier.fillMaxSize(), compact = false)
    }
}

/* ------------------------------------------------------------------ object list */

@Composable
private fun ObjectListPane(state: DbViewModel.UiState, vm: DbViewModel, modifier: Modifier, compact: Boolean) {
    LazyColumn(modifier.background(DbColors.Background)) {
        if (!compact) item { DatabaseSummaryCard(state) }
        item { ObjectFilterField(state.objectFilter, vm::setObjectFilter) }
        if (state.browserObjects.isNotEmpty()) {
            item { SectionHeader("表与视图", state.browserObjects.size) }
            items(state.browserObjects, key = { "b${it.kind}${it.name}" }) { obj ->
                ObjectRow(obj, selected = state.selectedTable == obj.name, navigable = true) { vm.selectTable(obj.name) }
            }
        }
        if (state.secondaryObjects.isNotEmpty()) {
            item { SectionHeader("索引与触发器", state.secondaryObjects.size) }
            items(state.secondaryObjects, key = { "s${it.kind}${it.name}" }) { obj ->
                ObjectRow(obj, selected = false, navigable = false) {}
            }
        }
        if (state.objects.isEmpty()) item { EmptyHint("这个数据库没有可浏览的对象") }
    }
}

@Composable
private fun DatabaseSummaryCard(state: DbViewModel.UiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(DbColors.AccentSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(19.dp), tint = DbColors.Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DbColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SQLite 只读会话", fontSize = 11.sp, color = DbColors.TextSecondary)
            }
            ReadOnlyBadge()
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("表", state.tableCount, Modifier.weight(1f))
            StatTile("视图", state.viewCount, Modifier.weight(1f))
            StatTile("索引", state.indexCount, Modifier.weight(1f))
            StatTile("触发器", state.triggerCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = DbColors.Surface, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(formatCount(value.toLong()), fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DbColors.Accent)
            Text(label, fontSize = 10.sp, color = DbColors.TextMuted)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = DbColors.TextMuted, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = DbColors.TextMuted)
    }
}

@Composable
private fun ObjectFilterField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = { Text("搜索表 / 视图", fontSize = 13.sp, color = DbColors.TextMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = DbColors.TextMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(15.dp), tint = DbColors.TextMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(9.dp),
        textStyle = TextStyle(fontSize = 13.sp, color = DbColors.TextPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DbColors.Accent,
            unfocusedBorderColor = DbColors.Border,
            focusedContainerColor = DbColors.Surface,
            unfocusedContainerColor = DbColors.Surface,
        ),
    )
}

@Composable
private fun ObjectRow(obj: DbObject, selected: Boolean, navigable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) DbColors.AccentSoft else Color.Transparent)
            .clickable(enabled = navigable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = objectIcon(obj.kind),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (selected) DbColors.Accent else DbColors.TextMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = obj.name,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (navigable) DbColors.TextPrimary else DbColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(objectKindLabel(obj.kind), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DbColors.TextMuted)
        if (navigable) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = DbColors.TextMuted)
        }
    }
    HorizontalDivider(color = DbColors.Divider)
}

private fun objectIcon(kind: String): ImageVector = when (kind.lowercase()) {
    "view" -> Icons.Default.DataObject
    "index" -> Icons.Default.Key
    else -> Icons.Default.TableChart
}

/* ------------------------------------------------------------------ table browser */

@Composable
private fun TableBrowser(
    vm: DbViewModel,
    state: DbViewModel.UiState,
    onExport: (ExportRequest) -> Unit,
    onOpenQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember(state.selectedTable) { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        BreadcrumbBar(state, vm)
        TabRow(
            selectedTabIndex = tab,
            containerColor = DbColors.Surface,
            contentColor = DbColors.Accent,
            divider = { HorizontalDivider(color = DbColors.Divider) },
        ) {
            BROWSER_TABS.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, fontSize = 13.sp, fontWeight = if (tab == index) FontWeight.Medium else FontWeight.Normal) },
                    unselectedContentColor = DbColors.TextSecondary,
                )
            }
        }
        TableStrip(state, vm)
        when (tab) {
            0 -> DataTab(vm, state, onExport, onOpenQuery, Modifier.weight(1f))
            1 -> ColumnsTab(state, Modifier.weight(1f))
            2 -> IndexesTab(state, Modifier.weight(1f))
            else -> DdlTab(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BreadcrumbBar(state: DbViewModel.UiState, vm: DbViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).background(DbColors.Surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.clearTableSelection() }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回对象列表", modifier = Modifier.size(17.dp), tint = DbColors.TextSecondary)
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = DbColors.TextMuted)) { append(state.title) }
                withStyle(SpanStyle(color = DbColors.TextMuted)) { append("  >  ") }
                withStyle(SpanStyle(color = DbColors.TextPrimary, fontWeight = FontWeight.SemiBold)) { append(state.selectedTable ?: "") }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp, end = 8.dp),
        )
        if (state.schemaLoading) Text("读取中…", fontSize = 11.sp, color = DbColors.TextMuted, modifier = Modifier.padding(end = 6.dp))
        IconButton(onClick = { vm.refresh() }, enabled = !state.loading, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(17.dp),
                tint = if (state.loading) DbColors.TextMuted else DbColors.TextSecondary,
            )
        }
    }
    HorizontalDivider(color = DbColors.Divider)
}

/**
 * Table/view picker shown as a horizontal strip right under the 数据 tab, so switching objects
 * is one tap instead of a dropdown: the strip scrolls sideways, keeps the open object highlighted
 * and scrolls it into view whenever the selection changes.
 */
@Composable
private fun TableStrip(state: DbViewModel.UiState, vm: DbViewModel) {
    val objects = state.browserObjects
    val selectedIndex = objects.indexOfFirst { it.name == state.selectedTable }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, objects.size) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    Column(Modifier.fillMaxWidth().background(DbColors.Surface)) {
        if (objects.isEmpty()) {
            Text(
                text = if (state.objectFilter.isBlank()) "没有可浏览的表或视图" else "没有匹配「${state.objectFilter}」的表或视图",
                fontSize = 11.5.sp,
                color = DbColors.TextMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            )
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(objects, key = { "chip:${it.kind}:${it.name}" }) { obj ->
                    TableChip(obj = obj, selected = obj.name == state.selectedTable) { vm.selectTable(obj.name) }
                }
            }
        }
        HorizontalDivider(color = DbColors.Divider)
    }
}

@Composable
private fun TableChip(obj: DbObject, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) DbColors.AccentSoft else DbColors.SurfaceHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) DbColors.Accent else Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = objectIcon(obj.kind),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (selected) DbColors.Accent else DbColors.TextMuted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = obj.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) DbColors.Accent else DbColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

/* ------------------------------------------------------------------ data tab */

@Composable
private fun DataTab(
    vm: DbViewModel,
    state: DbViewModel.UiState,
    onExport: (ExportRequest) -> Unit,
    onOpenQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<GridRow?>(null) }
    val clipboard = LocalClipboardManager.current

    // 使用 LaunchedEffect 确保列宽计算在协程中异步执行
    var columns by remember { mutableStateOf<List<com.example.dbviewer.ui.components.GridColumn>>(emptyList()) }
    LaunchedEffect(state.columns, state.rows) {
        columns = gridColumnsFromSchema(state.gridColumns, state.rows)
    }

    val selectedCount = state.selection.size
    val names = state.gridColumns.mapNotNull { it.name }

    fun visibleRows(onlySelected: Boolean): List<GridRow> = if (onlySelected) state.rows.filter { it.position in state.selection } else state.rows

    fun requestExport(extension: String, content: String, mime: String) {
        val table = state.selectedTable ?: return
        onExport(ExportRequest(exportFileName(table, extension), mime, content))
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).background(DbColors.Surface).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (selectedCount > 0) {
                    Text("已选 $selectedCount 行", fontSize = 12.sp, color = DbColors.Accent, modifier = Modifier.padding(horizontal = 10.dp), maxLines = 1)
                    ToolbarAction(Icons.Default.Download, "导出选中") {
                        requestExport("csv", buildCsv(names, visibleRows(true).map { it.values }), "text/csv")
                    }
                    ToolbarAction(Icons.Default.ContentCopy, "复制") {
                        clipboard.setText(AnnotatedString(buildJson(names, visibleRows(true).map { it.values })))
                    }
                    ToolbarAction(Icons.Default.Close, "取消选择") { vm.clearSelection() }
                } else {
                    // 显示筛选/排序状态
                    if (state.hasActiveFilters || state.hasActiveSort) {
                        Text(
                            text = buildString {
                                if (state.hasActiveSort) append("已排序 ")
                                if (state.hasActiveFilters) append("已筛选 ")
                            }.trim(),
                            fontSize = 11.sp,
                            color = DbColors.Accent,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        ToolbarAction(Icons.Default.Close, "清除筛选") {
                            vm.clearAllFilters()
                        }
                    }

                    ExportMenu(
                        enabled = state.rows.isNotEmpty(),
                        onCsv = { requestExport("csv", buildCsv(names, visibleRows(false).map { it.values }), "text/csv") },
                        onJson = { requestExport("json", buildJson(names, visibleRows(false).map { it.values }), "application/json") },
                        onCopy = { clipboard.setText(AnnotatedString(buildJson(names, visibleRows(false).map { it.values }))) },
                    )
                    ToolbarAction(Icons.Default.Refresh, "刷新", enabled = !state.loading) { vm.refresh() }
                    ToolbarAction(Icons.Default.Code, "查询") {
                        val table = state.selectedTable
                        if (table != null) onOpenQuery("SELECT * FROM \"$table\" LIMIT 100;")
                    }
                }
            }
            ReadOnlyBadge()
        }
        HorizontalDivider(color = DbColors.Divider)
        if (state.rows.isEmpty() && (state.loading || state.schemaLoading)) {
            LoadingBox(Modifier.weight(1f))
        } else {
            DataGrid(
                columns = columns,
                rows = state.rows,
                modifier = Modifier.weight(1f),
                selection = state.selection,
                onToggleRow = vm::toggleRow,
                onToggleAll = vm::toggleAllOnPage,
                onRowClick = { detail = it },
                onRowLongClick = { clipboard.setText(AnnotatedString(buildJson(names, listOf(it.values)))) },
                emptyText = "该表没有数据",
                onScrollNearBottom = { vm.onScrollNearBottom() },
                // 新增交互功能参数
                sortColumn = state.sortColumn,
                sortDirection = state.sortDirection,
                pinnedColumns = state.pinnedColumns,
                columnWidths = state.columnWidths,
                columnFilters = state.columnFilters,
                onSort = vm::toggleSort,
                onTogglePin = vm::togglePinColumn,
                onFilter = vm::setColumnFilter,
                onResizeColumn = vm::setColumnWidth,
            )
        }
        HorizontalDivider(color = DbColors.Divider)
        PaginationBar(
            rangeStart = state.firstRowNumber,
            rangeEnd = state.lastRowNumber,
            totalRows = state.totalRows,
            totalExact = state.totalExact,
            pageIndex = state.pageIndex,
            pageCount = state.pageCount,
            pageSize = state.pageSize,
            pageSizes = DbViewModel.PAGE_SIZES,
            hasNext = state.hasNextPage,
            hasPrevious = state.hasPreviousPage,
            usingKeysetPagination = state.usingKeysetPagination,
            enabled = !state.loading,
            preloading = state.preloadingNextPage,
            onPageSize = vm::setPageSize,
            onFirst = vm::firstPage,
            onPrevious = vm::previousPage,
            onNext = vm::nextPage,
            onLast = vm::lastPage,
        )
    }
    detail?.let { row -> RowDetailSheet(columns = columns, row = row, onDismiss = { detail = null }) }
}

@Composable
private fun ExportMenu(enabled: Boolean, onCsv: () -> Unit, onJson: () -> Unit, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarAction(Icons.Default.Download, "导出", enabled = enabled) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("导出 CSV（当前页）", fontSize = 13.sp) }, onClick = { expanded = false; onCsv() })
            DropdownMenuItem(text = { Text("导出 JSON（当前页）", fontSize = 13.sp) }, onClick = { expanded = false; onJson() })
            DropdownMenuItem(text = { Text("复制为 JSON", fontSize = 13.sp) }, onClick = { expanded = false; onCopy() })
        }
    }
}

/* ------------------------------------------------------------------ schema tabs */

@Composable
private fun ColumnsTab(state: DbViewModel.UiState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.background(DbColors.Background)) {
        if (state.columns.isEmpty()) {
            item { EmptyHint("没有列信息") }
        } else {
            items(state.columns, key = { it.name ?: it.hashCode().toString() }) { column -> ColumnRow(column) }
        }
    }
}

@Composable
private fun ColumnRow(column: ColumnInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.primaryKeyOrder > 0) {
            Icon(Icons.Default.Key, contentDescription = "主键", modifier = Modifier.size(13.dp), tint = DbColors.Key)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(column.name ?: "—", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = DbColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            column.defaultValue?.let {
                Text("DEFAULT $it", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = DbColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            text = column.type?.takeIf { it.isNotBlank() } ?: "—",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = DbColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.width(76.dp),
        )
        if (column.notNull) Badge("NOT NULL")
        if (column.primaryKeyOrder > 0) Badge(if (column.primaryKeyOrder > 1) "PK${column.primaryKeyOrder}" else "PK", accent = true)
    }
    HorizontalDivider(color = DbColors.Divider)
}

@Composable
private fun IndexesTab(state: DbViewModel.UiState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.background(DbColors.Background)) {
        if (state.indexes.isEmpty()) {
            item { EmptyHint("该对象没有索引") }
        } else {
            items(state.indexes, key = { it.name }) { index -> IndexRow(index) }
        }
    }
}

@Composable
private fun IndexRow(index: IndexInfo) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (index.unique) DbColors.Success else DbColors.TextMuted)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(index.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = DbColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val fields = index.fields.joinToString(", ") { field -> "${field.name ?: "?"}${if (field.descending) " DESC" else ""}" }
            Text(fields.ifBlank { "—" }, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = DbColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (index.unique) Badge("UNIQUE", accent = true)
        if (index.partial) Badge("PARTIAL")
        Badge(
            when (index.origin) {
                "pk" -> "主键"
                "u" -> "约束"
                else -> "索引"
            }
        )
    }
    HorizontalDivider(color = DbColors.Divider)
}

@Composable
private fun DdlTab(state: DbViewModel.UiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.background(DbColors.Background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.ddl.isEmpty()) {
            item { EmptyHint(if (state.schemaLoading) "读取中…" else "没有可显示的 DDL") }
        } else {
            items(state.ddl, key = { "${it.kind}:${it.name}" }) { item -> DdlCard(item) }
        }
    }
}

@Composable
private fun DdlCard(item: DdlItem) {
    val clipboard = LocalClipboardManager.current
    Surface(color = DbColors.Surface, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(objectKindLabel(item.kind), accent = item.kind.equals("table", true))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = DbColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(item.sql)) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制 DDL", modifier = Modifier.size(14.dp), tint = DbColors.TextMuted)
                }
            }
            Text(
                text = item.sql.trim(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = Color(0xFFA8C7FA),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Badge(text: String, accent: Boolean = false) {
    Surface(
        color = if (accent) DbColors.AccentSoft else DbColors.SurfaceHigh,
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier.padding(start = 6.dp),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            color = if (accent) DbColors.Accent else DbColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}
