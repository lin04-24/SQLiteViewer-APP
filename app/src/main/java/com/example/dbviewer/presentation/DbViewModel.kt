package com.example.dbviewer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dbviewer.data.ColumnInfo
import com.example.dbviewer.data.DbObject
import com.example.dbviewer.data.DbRepository
import com.example.dbviewer.data.DbSession
import com.example.dbviewer.data.DdlItem
import com.example.dbviewer.data.GridRow
import com.example.dbviewer.data.IndexInfo
import com.example.dbviewer.data.PageDirection
import com.example.dbviewer.data.PagedRows
import com.example.dbviewer.data.RowCount
import com.example.dbviewer.data.SortDirection
import com.example.dbviewer.data.ColumnFilter
import com.example.dbviewer.data.FilterType
import com.example.dbviewer.data.PreferencesStore
import com.example.dbviewer.data.TablePreferences
import com.example.dbviewer.data.persistedName
import com.example.dbviewer.data.toSortDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryEntry(val sql: String, val at: Long, val elapsedMs: Long?, val rowCount: Int?, val error: String?)

data class PageCache(
    val pageIndex: Int,
    val rows: List<GridRow>,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val firstId: Long?,
    val lastId: Long?,
    val usedKeyset: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class DbViewModel(
    private val repo: DbRepository,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    data class UiState(
        val title: String = "SQLite Viewer",
        val session: DbSession? = null,
        val objects: List<DbObject> = emptyList(),
        val tables: List<String> = emptyList(),
        val selectedTable: String? = null,
        val columns: List<ColumnInfo> = emptyList(),
        val indexes: List<IndexInfo> = emptyList(),
        val ddl: List<DdlItem> = emptyList(),
        val rows: List<GridRow> = emptyList(),
        val totalRows: Long = 0L,
        val totalExact: Boolean = true,
        val pageIndex: Int = 0,
        val pageSize: Int = DEFAULT_PAGE_SIZE,
        val hasNextPage: Boolean = false,
        val hasPreviousPage: Boolean = false,
        val firstIdOnPage: Long? = null,
        val lastIdOnPage: Long? = null,
        val usingKeysetPagination: Boolean = false,
        val selection: Set<Int> = emptySet(),
        val loading: Boolean = false,
        val schemaLoading: Boolean = false,
        val error: String? = null,
        val objectFilter: String = "",
        val sqlDraft: String = DEFAULT_SQL,
        val queryColumns: List<String> = emptyList(),
        val queryRows: List<List<String?>> = emptyList(),
        val queryElapsedMs: Long? = null,
        val queryTruncated: Boolean = false,
        val queryError: String? = null,
        val queryRunning: Boolean = false,
        val queryRan: Boolean = false,
        val history: List<HistoryEntry> = emptyList(),
        val preloadingNextPage: Boolean = false,
        // 新增：表格交互状态
        val sortColumn: String? = null,
        val sortDirection: SortDirection = SortDirection.NONE,
        val pinnedColumns: Set<String> = emptySet(),
        val columnWidths: Map<String, Float> = emptyMap(),
        val columnFilters: Map<String, ColumnFilter> = emptyMap(),
        // 查询结果的交互状态（独立）
        val querySortColumn: String? = null,
        val querySortDirection: SortDirection = SortDirection.NONE,
        val queryPinnedColumns: Set<String> = emptySet(),
        val queryColumnWidths: Map<String, Float> = emptyMap(),
    ) {
        val pageCount: Int get() = if (totalRows <= 0) 1 else (((totalRows - 1) / pageSize) + 1).toInt().coerceAtLeast(1)
        val firstRowNumber: Int get() = if (rows.isEmpty()) 0 else pageIndex * pageSize + 1
        val lastRowNumber: Int get() = pageIndex * pageSize + rows.size
        val browserObjects: List<DbObject> get() = filteredObjects.filter { it.kind.equals("table", true) || it.kind.equals("view", true) }
        val secondaryObjects: List<DbObject> get() = filteredObjects.filterNot { it.kind.equals("table", true) || it.kind.equals("view", true) }
        val filteredObjects: List<DbObject>
            get() = if (objectFilter.isBlank()) objects else objects.filter { it.name.contains(objectFilter, ignoreCase = true) }
        /** Columns shown in the grid, i.e. everything the paging query selects. */
        val gridColumns: List<ColumnInfo> get() = columns.filter { it.hidden == 0 }
        val tableCount: Int get() = objects.count { it.kind.equals("table", true) }
        val viewCount: Int get() = objects.count { it.kind.equals("view", true) }
        val indexCount: Int get() = objects.count { it.kind.equals("index", true) }
        val triggerCount: Int get() = objects.count { it.kind.equals("trigger", true) }
        val hasActiveFilters: Boolean get() = columnFilters.isNotEmpty()
        val hasActiveSort: Boolean get() = sortColumn != null && sortDirection != SortDirection.NONE
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var current: DbSession? = null
    private var openJob: Job? = null
    private var gridJob: Job? = null
    private var queryJob: Job? = null
    private var preloadJob: Job? = null
    private var currentDbPath: String? = null

    // LRU 缓存：最多缓存 3 页（前一页、当前页、后一页）
    private val pageCache = LinkedHashMap<Int, PageCache>(3, 0.75f, true)
    private val maxCacheSize = 3

    fun open(uri: Uri) {
        openJob?.cancel()
        gridJob?.cancel()
        preloadJob?.cancel()
        current?.let { runCatching { it.close() } }
        current = null
        currentDbPath = uri.toString()
        pageCache.clear()
        _state.value = UiState(title = uri.lastPathSegment ?: "Database", loading = true)
        openJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.open(uri) }
                .onSuccess { session ->
                    current = session
                    val objects = session.objects()
                    _state.value = _state.value.copy(session = session, objects = objects, tables = session.tables(), loading = false, error = null)
                    objects.firstOrNull { it.kind.equals("table", true) || it.kind.equals("view", true) }?.let { selectTable(it.name) }
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "无法打开数据库") }
        }
    }

    fun closeDatabase() {
        openJob?.cancel()
        gridJob?.cancel()
        queryJob?.cancel()
        preloadJob?.cancel()
        current?.let { runCatching { it.close() } }
        current = null
        currentDbPath = null
        pageCache.clear()
        _state.value = UiState()
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    fun selectTable(table: String) {
        val session = _state.value.session ?: return
        gridJob?.cancel()
        preloadJob?.cancel()
        pageCache.clear()
        val pageSize = _state.value.pageSize
        _state.value = _state.value.copy(
            selectedTable = table,
            columns = emptyList(),
            indexes = emptyList(),
            ddl = emptyList(),
            rows = emptyList(),
            selection = emptySet(),
            pageIndex = 0,
            totalRows = 0L,
            totalExact = true,
            hasNextPage = false,
            hasPreviousPage = false,
            firstIdOnPage = null,
            lastIdOnPage = null,
            usingKeysetPagination = false,
            schemaLoading = true,
            loading = true,
            preloadingNextPage = false,
            error = null,
            // 重置排序和筛选状态
            sortColumn = null,
            sortDirection = SortDirection.NONE,
            pinnedColumns = emptySet(),
            columnWidths = emptyMap(),
            columnFilters = emptyMap(),
        )
        gridJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val count: RowCount = session.estimatedRowCount(table)
                val preferences = currentDbPath?.let { preferencesStore.loadTablePreferences(it, table) } ?: TablePreferences()
                TableSnapshot(
                    columns = session.columns(table),
                    indexes = session.indexes(table),
                    ddl = session.ddl(table),
                    count = count,
                    preferences = preferences,
                    page = session.pageAt(
                        table = table,
                        offset = 0,
                        limit = pageSize,
                        sortColumn = preferences.sortColumn,
                        sortDirection = preferences.sortDirection,
                    ),
                )
            }.onSuccess { snapshot ->
                val firstId = snapshot.page.rows.firstOrNull()?.id
                val lastId = snapshot.page.rows.lastOrNull()?.id
                addToCache(0, PageCache(
                    pageIndex = 0,
                    rows = snapshot.page.rows,
                    hasNextPage = snapshot.page.hasMore,
                    hasPreviousPage = false,
                    firstId = firstId,
                    lastId = lastId,
                    usedKeyset = snapshot.page.usedKeyset,
                ))
                _state.value = _state.value.copy(
                    columns = snapshot.columns,
                    indexes = snapshot.indexes,
                    ddl = snapshot.ddl,
                    totalRows = snapshot.count.value,
                    totalExact = snapshot.count.exact,
                    sortColumn = snapshot.preferences.sortColumn,
                    sortDirection = snapshot.preferences.sortDirection.toSortDirection(),
                    pinnedColumns = snapshot.preferences.pinnedColumns.take(MAX_PINNED_COLUMNS).toSet(),
                    columnWidths = snapshot.preferences.columnWidths,
                    rows = snapshot.page.rows,
                    hasNextPage = snapshot.page.hasMore,
                    hasPreviousPage = false,
                    firstIdOnPage = firstId,
                    lastIdOnPage = lastId,
                    usingKeysetPagination = snapshot.page.usedKeyset,
                    schemaLoading = false,
                    loading = false,
                    preloadingNextPage = false,
                )
                if (snapshot.page.hasMore) preloadPage(1)
            }.onFailure { _state.value = _state.value.copy(schemaLoading = false, loading = false, error = it.message ?: "无法读取该表") }
        }
    }

    fun clearTableSelection() {
        gridJob?.cancel()
        preloadJob?.cancel()
        pageCache.clear()
        _state.value = _state.value.copy(
            selectedTable = null,
            columns = emptyList(),
            indexes = emptyList(),
            ddl = emptyList(),
            rows = emptyList(),
            selection = emptySet(),
            pageIndex = 0,
            totalRows = 0L,
            hasNextPage = false,
            hasPreviousPage = false,
            firstIdOnPage = null,
            lastIdOnPage = null,
            usingKeysetPagination = false,
            loading = false,
            preloadingNextPage = false,
        )
    }

    fun loadPage(index: Int) {
        val session = _state.value.session ?: return
        val table = _state.value.selectedTable ?: return
        val pageSize = _state.value.pageSize
        val target = index.coerceAtLeast(0)
        val state = _state.value

        // 检查缓存
        pageCache[target]?.let { cached ->
            _state.value = _state.value.copy(
                pageIndex = target,
                rows = cached.rows,
                hasNextPage = cached.hasNextPage,
                hasPreviousPage = cached.hasPreviousPage,
                firstIdOnPage = cached.firstId,
                lastIdOnPage = cached.lastId,
                usingKeysetPagination = cached.usedKeyset,
                selection = emptySet(),
                loading = false,
                error = null
            )
            // 预加载下一页
            if (cached.hasNextPage) {
                preloadPage(target + 1)
            }
            return
        }

        gridJob?.cancel()
        _state.value = _state.value.copy(loading = true, pageIndex = target, selection = emptySet(), error = null)
        gridJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                session.pageAt(
                    table = table,
                    offset = target * pageSize,
                    limit = pageSize,
                    sortColumn = state.sortColumn,
                    sortDirection = state.sortDirection.name,
                    filters = state.columnFilters.values.toList()
                )
            }.onSuccess { page ->
                val firstId = page.rows.firstOrNull()?.id
                val lastId = page.rows.lastOrNull()?.id

                // 缓存当前页
                addToCache(target, PageCache(
                    pageIndex = target,
                    rows = page.rows,
                    hasNextPage = page.hasMore,
                    hasPreviousPage = target > 0,
                    firstId = firstId,
                    lastId = lastId,
                    usedKeyset = page.usedKeyset,
                ))

                _state.value = _state.value.copy(
                    rows = page.rows,
                    hasNextPage = page.hasMore,
                    hasPreviousPage = target > 0,
                    firstIdOnPage = firstId,
                    lastIdOnPage = lastId,
                    usingKeysetPagination = page.usedKeyset,
                    loading = false
                )

                // 预加载下一页
                if (page.hasMore) {
                    preloadPage(target + 1)
                }
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "无法读取数据") }
        }
    }

    fun refresh() {
        val session = _state.value.session ?: return
        val table = _state.value.selectedTable ?: return
        val state = _state.value
        gridJob?.cancel()
        preloadJob?.cancel()
        pageCache.clear()
        _state.value = state.copy(loading = true, preloadingNextPage = false, error = null)
        gridJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val count = session.estimatedRowCount(table)
                val page: PagedRows = session.pageAt(
                    table = table,
                    offset = state.pageIndex * state.pageSize,
                    limit = state.pageSize,
                    sortColumn = state.sortColumn,
                    sortDirection = state.sortDirection.name,
                    filters = state.columnFilters.values.toList()
                )
                count to page
            }.onSuccess { (count, page) ->
                val firstId = page.rows.firstOrNull()?.id
                val lastId = page.rows.lastOrNull()?.id
                addToCache(state.pageIndex, PageCache(
                    pageIndex = state.pageIndex,
                    rows = page.rows,
                    hasNextPage = page.hasMore,
                    hasPreviousPage = state.pageIndex > 0,
                    firstId = firstId,
                    lastId = lastId,
                    usedKeyset = page.usedKeyset,
                ))
                _state.value = _state.value.copy(
                    totalRows = count.value,
                    totalExact = count.exact,
                    rows = page.rows,
                    hasNextPage = page.hasMore,
                    hasPreviousPage = state.pageIndex > 0,
                    firstIdOnPage = firstId,
                    lastIdOnPage = lastId,
                    usingKeysetPagination = page.usedKeyset,
                    loading = false,
                    preloadingNextPage = false,
                )
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "刷新失败") }
        }
    }

    fun nextPage() {
        if (!_state.value.hasNextPage) return
        val session = _state.value.session ?: return
        val table = _state.value.selectedTable ?: return
        val state = _state.value
        pageCache[state.pageIndex + 1]?.let { cached ->
            _state.value = state.copy(
                pageIndex = state.pageIndex + 1,
                rows = cached.rows,
                hasNextPage = cached.hasNextPage,
                hasPreviousPage = true,
                firstIdOnPage = cached.firstId,
                lastIdOnPage = cached.lastId,
                usingKeysetPagination = cached.usedKeyset,
                selection = emptySet(),
                loading = false,
                error = null,
            )
            if (cached.hasNextPage) preloadPage(state.pageIndex + 2)
            return
        }

        // Use Keyset Pagination if available and no sorting/filtering
        if (state.usingKeysetPagination && state.lastIdOnPage != null && state.sortColumn == null && state.columnFilters.isEmpty()) {
            gridJob?.cancel()
            _state.value = state.copy(loading = true, selection = emptySet(), error = null, pageIndex = state.pageIndex + 1)
            gridJob = viewModelScope.launch(Dispatchers.IO) {
                runCatching { session.pageAt(table, (state.pageIndex + 1) * state.pageSize, state.pageSize, state.lastIdOnPage, PageDirection.NEXT) }
                    .onSuccess { page ->
                        val firstId = page.rows.firstOrNull()?.id
                        val lastId = page.rows.lastOrNull()?.id
                        addToCache(state.pageIndex + 1, PageCache(
                            pageIndex = state.pageIndex + 1,
                            rows = page.rows,
                            hasNextPage = page.hasMore,
                            hasPreviousPage = true,
                            firstId = firstId,
                            lastId = lastId,
                            usedKeyset = page.usedKeyset,
                        ))
                        _state.value = _state.value.copy(
                            rows = page.rows,
                            hasNextPage = page.hasMore,
                            hasPreviousPage = true,
                            firstIdOnPage = firstId,
                            lastIdOnPage = lastId,
                            usingKeysetPagination = page.usedKeyset,
                            loading = false
                        )
                    }
                    .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "无法读取数据") }
            }
        } else {
            loadPage(state.pageIndex + 1)
        }
    }

    fun previousPage() {
        if (!_state.value.hasPreviousPage) return
        val session = _state.value.session ?: return
        val table = _state.value.selectedTable ?: return
        val state = _state.value

        // Use Keyset Pagination if available and no sorting/filtering
        if (state.usingKeysetPagination && state.firstIdOnPage != null && state.pageIndex > 0 && state.sortColumn == null && state.columnFilters.isEmpty()) {
            gridJob?.cancel()
            _state.value = state.copy(loading = true, selection = emptySet(), error = null, pageIndex = state.pageIndex - 1)
            gridJob = viewModelScope.launch(Dispatchers.IO) {
                runCatching { session.pageAt(table, (state.pageIndex - 1) * state.pageSize, state.pageSize, state.firstIdOnPage, PageDirection.PREVIOUS) }
                    .onSuccess { page ->
                        val firstId = page.rows.firstOrNull()?.id
                        val lastId = page.rows.lastOrNull()?.id
                        _state.value = _state.value.copy(
                            rows = page.rows,
                            hasNextPage = true,
                            hasPreviousPage = state.pageIndex > 1,
                            firstIdOnPage = firstId,
                            lastIdOnPage = lastId,
                            loading = false
                        )
                    }
                    .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "无法读取数据") }
            }
        } else {
            loadPage(state.pageIndex - 1)
        }
    }

    fun firstPage() {
        if (_state.value.pageIndex > 0) {
            // Reset to first page, clear keyset state
            _state.value = _state.value.copy(
                firstIdOnPage = null,
                lastIdOnPage = null,
                usingKeysetPagination = false
            )
            loadPage(0)
        }
    }

    fun lastPage() {
        val state = _state.value
        // Last page navigation is not efficient with Keyset Pagination
        // Fall back to OFFSET for this operation
        if (state.usingKeysetPagination) {
            _state.value = state.copy(
                firstIdOnPage = null,
                lastIdOnPage = null,
                usingKeysetPagination = false
            )
        }
        loadPage((state.pageCount - 1).coerceAtLeast(0))
    }

    fun setPageSize(size: Int) {
        if (size == _state.value.pageSize) return
        preloadJob?.cancel()
        pageCache.clear()
        _state.value = _state.value.copy(pageSize = size, preloadingNextPage = false)
        loadPage(0)
    }

    fun toggleRow(position: Int) {
        val selection = _state.value.selection
        _state.value = _state.value.copy(selection = if (position in selection) selection - position else selection + position)
    }

    fun toggleAllOnPage() {
        val state = _state.value
        val pagePositions = state.rows.indices.map { state.pageIndex * state.pageSize + it }
        if (pagePositions.isEmpty()) return
        val selection = state.selection
        _state.value = state.copy(selection = if (pagePositions.all { it in selection }) selection - pagePositions.toSet() else selection + pagePositions.toSet())
    }

    fun clearSelection() { _state.value = _state.value.copy(selection = emptySet()) }

    fun setObjectFilter(value: String) { _state.value = _state.value.copy(objectFilter = value) }

    fun setSqlDraft(text: String) { _state.value = _state.value.copy(sqlDraft = text) }

    fun useHistory(sql: String) { _state.value = _state.value.copy(sqlDraft = sql) }

    fun clearHistory() { _state.value = _state.value.copy(history = emptyList()) }

    fun execute(sql: String = _state.value.sqlDraft) {
        val session = _state.value.session ?: return
        val statement = sql.trim()
        if (statement.isEmpty()) return
        queryJob?.cancel()
        _state.value = _state.value.copy(sqlDraft = statement, queryRunning = true, queryRan = true, queryError = null, queryTruncated = false, queryElapsedMs = null, queryColumns = emptyList(), queryRows = emptyList())
        queryJob = viewModelScope.launch(Dispatchers.IO) {
            val started = System.nanoTime()
            runCatching { session.executeReadOnly(statement) }
                .onSuccess { result ->
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    _state.value = _state.value.copy(
                        queryColumns = result.columns,
                        queryRows = result.rows,
                        queryTruncated = result.hasMore,
                        queryElapsedMs = elapsed,
                        queryRunning = false,
                        history = (_state.value.history + HistoryEntry(statement, System.currentTimeMillis(), elapsed, result.rows.size, null)).takeLast(MAX_HISTORY),
                    )
                }
                .onFailure { error ->
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    val message = error.message ?: "查询失败"
                    _state.value = _state.value.copy(
                        queryError = message,
                        queryElapsedMs = elapsed,
                        queryRunning = false,
                        history = (_state.value.history + HistoryEntry(statement, System.currentTimeMillis(), elapsed, null, message)).takeLast(MAX_HISTORY),
                    )
                }
        }
    }

    override fun onCleared() {
        current?.let { runCatching { it.close() } }
        current = null
        pageCache.clear()
        super.onCleared()
    }

    private fun addToCache(pageIndex: Int, cache: PageCache) {
        pageCache[pageIndex] = cache
        // LRU 策略：移除最旧的条目
        if (pageCache.size > maxCacheSize) {
            val oldestKey = pageCache.keys.first()
            pageCache.remove(oldestKey)
        }
    }

    private fun preloadPage(pageIndex: Int) {
        // 如果已经在缓存中或正在加载，跳过
        if (pageCache.containsKey(pageIndex) || _state.value.preloadingNextPage) return

        val session = _state.value.session ?: return
        val table = _state.value.selectedTable ?: return
        val pageSize = _state.value.pageSize
        val currentState = _state.value
        val keysetCursor = currentState.lastIdOnPage
            ?.takeIf { currentState.usingKeysetPagination && pageIndex == currentState.pageIndex + 1 && currentState.sortColumn == null && currentState.columnFilters.isEmpty() }

        preloadJob?.cancel()
        _state.value = _state.value.copy(preloadingNextPage = true)
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                session.pageAt(
                    table = table,
                    offset = pageIndex * pageSize,
                    limit = pageSize,
                    lastSeenId = keysetCursor,
                    direction = PageDirection.NEXT,
                    sortColumn = currentState.sortColumn,
                    sortDirection = currentState.sortDirection.name,
                    filters = currentState.columnFilters.values.toList()
                )
            }.onSuccess { page ->
                val firstId = page.rows.firstOrNull()?.id
                val lastId = page.rows.lastOrNull()?.id

                // 缓存预加载的页面
                addToCache(pageIndex, PageCache(
                    pageIndex = pageIndex,
                    rows = page.rows,
                    hasNextPage = page.hasMore,
                    hasPreviousPage = pageIndex > 0,
                    firstId = firstId,
                    lastId = lastId,
                    usedKeyset = page.usedKeyset,
                ))

                _state.value = _state.value.copy(preloadingNextPage = false)
            }.onFailure {
                _state.value = _state.value.copy(preloadingNextPage = false)
            }
        }
    }

    fun onScrollNearBottom() {
        // 当滚动接近底部时触发预加载
        if (_state.value.hasNextPage && !_state.value.loading && !_state.value.preloadingNextPage) {
            preloadPage(_state.value.pageIndex + 1)
        }
    }

    // ========== 新增：列排序功能 ==========

    /**
     * 切换列排序
     */
    fun toggleSort(columnName: String) {
        val state = _state.value
        val newDirection = if (state.sortColumn == columnName) {
            state.sortDirection.next()
        } else {
            SortDirection.ASC
        }

        if (newDirection == SortDirection.NONE) {
            // 清除排序
            _state.value = state.copy(
                sortColumn = null,
                sortDirection = SortDirection.NONE,
            )
        } else {
            _state.value = state.copy(
                sortColumn = columnName,
                sortDirection = newDirection,
            )
        }

        persistTablePreferences()

        // 清空缓存并重新加载第一页
        pageCache.clear()
        loadPage(0)
    }

    // ========== 新增：列固定功能 ==========

    /**
     * 切换列固定状态（最多固定 3 列）
     */
    fun togglePinColumn(columnName: String) {
        val state = _state.value
        val newPinned = if (columnName in state.pinnedColumns) {
            state.pinnedColumns - columnName
        } else {
            if (state.pinnedColumns.size >= MAX_PINNED_COLUMNS) {
                // 已达到最大固定列数
                return
            }
            state.pinnedColumns + columnName
        }
        _state.value = state.copy(pinnedColumns = newPinned)
        persistTablePreferences()
    }

    /**
     * 清除所有固定列
     */
    fun clearPinnedColumns() {
        _state.value = _state.value.copy(pinnedColumns = emptySet())
        persistTablePreferences()
    }

    // ========== 新增：列宽调整功能 ==========

    /**
     * 设置列宽
     */
    fun setColumnWidth(columnName: String, widthDp: Float) {
        val state = _state.value
        _state.value = state.copy(
            columnWidths = state.columnWidths + (columnName to widthDp.coerceIn(MIN_COLUMN_WIDTH_DP, MAX_COLUMN_WIDTH_DP))
        )
        persistTablePreferences()
    }

    /**
     * 重置所有列宽到默认值
     */
    fun resetColumnWidths() {
        _state.value = _state.value.copy(columnWidths = emptyMap())
        persistTablePreferences()
    }

    // ========== 新增：列筛选功能 ==========

    /**
     * 设置列筛选
     */
    fun setColumnFilter(columnName: String, filterType: FilterType, value: String) {
        val state = _state.value
        if (value.isBlank()) {
            // 清除筛选
            _state.value = state.copy(
                columnFilters = state.columnFilters - columnName
            )
        } else {
            _state.value = state.copy(
                columnFilters = state.columnFilters + (columnName to ColumnFilter(columnName, filterType, value))
            )
        }

        persistTablePreferences()

        // 清空缓存并重新加载第一页
        pageCache.clear()
        loadPage(0)
    }

    /**
     * 清除指定列的筛选
     */
    fun clearColumnFilter(columnName: String) {
        val state = _state.value
        _state.value = state.copy(
            columnFilters = state.columnFilters - columnName
        )

        persistTablePreferences()

        // 清空缓存并重新加载第一页
        pageCache.clear()
        loadPage(0)
    }

    /**
     * 清除所有筛选
     */
    fun clearAllFilters() {
        _state.value = _state.value.copy(columnFilters = emptyMap())

        persistTablePreferences()

        // 清空缓存并重新加载第一页
        pageCache.clear()
        loadPage(0)
    }

    // ========== 新增：查询结果的交互功能 ==========

    /**
     * 切换查询结果列排序
     */
    fun toggleQuerySort(columnName: String) {
        val state = _state.value
        val newDirection = if (state.querySortColumn == columnName) {
            state.querySortDirection.next()
        } else {
            SortDirection.ASC
        }

        _state.value = state.copy(
            querySortColumn = if (newDirection == SortDirection.NONE) null else columnName,
            querySortDirection = newDirection,
        )
    }

    /**
     * 切换查询结果列固定
     */
    fun toggleQueryPinColumn(columnName: String) {
        val state = _state.value
        val newPinned = if (columnName in state.queryPinnedColumns) {
            state.queryPinnedColumns - columnName
        } else {
            if (state.queryPinnedColumns.size >= MAX_PINNED_COLUMNS) return
            state.queryPinnedColumns + columnName
        }
        _state.value = state.copy(queryPinnedColumns = newPinned)
    }

    /**
     * 设置查询结果列宽
     */
    fun setQueryColumnWidth(columnName: String, widthDp: Float) {
        val state = _state.value
        _state.value = state.copy(
            queryColumnWidths = state.queryColumnWidths + (columnName to widthDp)
        )
    }

    private data class TableSnapshot(
        val columns: List<ColumnInfo>,
        val indexes: List<IndexInfo>,
        val ddl: List<DdlItem>,
        val count: RowCount,
        val preferences: TablePreferences,
        val page: PagedRows,
    )

    private fun persistTablePreferences() {
        val table = _state.value.selectedTable ?: return
        val dbPath = currentDbPath ?: return
        val state = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                preferencesStore.saveTablePreferences(
                    dbPath = dbPath,
                    tableName = table,
                    preferences = TablePreferences(
                        columnWidths = state.columnWidths,
                        pinnedColumns = state.pinnedColumns,
                        sortColumn = state.sortColumn,
                        sortDirection = state.sortDirection.persistedName,
                    ),
                )
            }
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_HISTORY = 40
        private const val DEFAULT_SQL = "SELECT name, type FROM sqlite_master ORDER BY name"
        private const val MAX_PINNED_COLUMNS = 3
        private const val MIN_COLUMN_WIDTH_DP = 50f
        private const val MAX_COLUMN_WIDTH_DP = 600f
        val PAGE_SIZES = listOf(50, 100, 200, 500)
        fun factory(repo: DbRepository, preferencesStore: PreferencesStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DbViewModel(repo, preferencesStore) as T
        }
    }
}
