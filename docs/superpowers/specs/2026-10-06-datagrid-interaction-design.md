# DataGrid 交互增强设计文档

**日期**: 2026-10-06  
**作者**: Claude  
**状态**: 已实现

## 概述

为 DataGrid 组件添加完整的表格交互功能，包括列排序、筛选、固定列、调整列宽等，提升数据浏览体验，参考 AG Grid 和 Tanstack Table 的交互模式。

## 目标

- 支持点击列标题进行升序/降序/无排序切换
- 支持列头右键菜单：隐藏列、固定列、调整列宽
- 实现任意列固定，横向滚动时保持固定列可见
- 支持拖拽调整列宽，并按表名持久化保存
- 添加快速筛选：在列头显示筛选图标，支持包含/不包含文本匹配

## 架构方案

采用**渐进式增强**方案，在现有 DataGrid 基础上逐步添加交互功能，保持与 Keyset Pagination 和 LRU 缓存的兼容性。

### 核心组件

1. **TablePreferences** - 表格偏好设置的数据模型
2. **PreferencesStore** - 持久化存储管理器
3. **DbViewModel** - 表格状态管理和 SQL 生成
4. **DataGrid** - UI 组件，渲染增强的表头和交互逻辑

## 详细设计

### 1. 数据模型

#### TablePreferences
```kotlin
@Serializable
data class TablePreferences(
    val columnWidths: Map<String, Float> = emptyMap(),
    val pinnedColumns: Set<String> = emptySet(),
)
```

存储每个表的列宽和固定列设置，使用 kotlinx-serialization 序列化为 JSON。

#### ColumnFilter
```kotlin
data class ColumnFilter(
    val column: String,
    val type: FilterType,
    val value: String,
)

enum class FilterType {
    CONTAINS,      // 包含
    NOT_CONTAINS   // 不包含
}
```

列筛选条件，支持文本包含和不包含两种模式。

### 2. 持久化存储

扩展 `PreferencesStore` 以支持表格偏好设置的保存和加载：

- **存储键**: `"table_prefs_$dbPath:$tableName"`
- **格式**: JSON
- **作用域**: 按数据库路径和表名组合作为键，每个表独立保存

**Why**: 不同数据库可能有同名表但结构不同，使用 `dbPath:tableName` 作为复合键可以避免冲突。

**How to apply**: 在 ViewModel 中切换表时自动加载对应的偏好设置，用户调整列宽或固定列时立即保存。

### 3. ViewModel 状态管理

在 `DbViewModel.UiState` 中添加交互状态：

```kotlin
// 数据浏览表格的交互状态
val sortColumn: String? = null,
val sortDirection: SortDirection = SortDirection.NONE,
val pinnedColumns: Set<String> = emptySet(),
val columnWidths: Map<String, Float> = emptyMap(),
val columnFilters: Map<String, ColumnFilter> = emptyMap(),

// 查询结果表格的交互状态（独立）
val querySortColumn: String? = null,
val querySortDirection: SortDirection = SortDirection.NONE,
val queryPinnedColumns: Set<String> = emptySet(),
val queryColumnWidths: Map<String, Float> = emptyMap(),
```

**Why**: 数据浏览表格和查询结果表格需要独立的交互状态，因为它们的列定义、排序逻辑和用户意图都不同。

**How to apply**: 
- 数据浏览表格的排序和筛选直接修改 SQL 的 `ORDER BY` 和 `WHERE` 子句
- 查询结果表格的排序仅影响显示，不修改用户输入的 SQL
- 列宽和固定列设置仅影响 UI 布局，不改变数据

### 4. SQL 生成逻辑

#### 排序 (ORDER BY)
```kotlin
private fun buildOrderByClause(sortColumn: String?, sortDirection: SortDirection): String {
    return when (sortDirection) {
        SortDirection.ASC -> "ORDER BY \"$sortColumn\" ASC"
        SortDirection.DESC -> "ORDER BY \"$sortColumn\" DESC"
        SortDirection.NONE -> ""
    }
}
```

**注意**: 排序会清空 Keyset Pagination 状态，回退到 OFFSET 分页模式。

**Why**: Keyset Pagination 依赖固定的排序字段（主键），用户自定义排序会破坏这个前提。

#### 筛选 (WHERE)
```kotlin
private fun buildWhereClause(filters: Map<String, ColumnFilter>): String {
    if (filters.isEmpty()) return ""
    
    val conditions = filters.values.map { filter ->
        when (filter.type) {
            FilterType.CONTAINS -> "\"${filter.column}\" LIKE '%${filter.value.escapeSql()}%'"
            FilterType.NOT_CONTAINS -> "\"${filter.column}\" NOT LIKE '%${filter.value.escapeSql()}%'"
        }
    }
    
    return "WHERE " + conditions.joinToString(" AND ")
}
```

**SQL 注入防护**: 使用参数化查询或转义特殊字符（`%`, `_`, `'`）。

**How to apply**: 筛选条件通过 AND 连接，所有条件必须同时满足。

### 5. UI 组件设计

#### EnhancedHeaderCell
增强的列头单元格，包含：
- **排序图标**: 点击切换升序/降序/无排序
- **筛选图标**: 点击打开筛选对话框
- **固定图标**: 标识已固定的列
- **右键菜单**: 固定列、隐藏列
- **拖拽手柄**: 列边缘拖拽调整宽度

```kotlin
@Composable
private fun EnhancedHeaderCell(
    column: GridColumn,
    isSorted: Boolean,
    sortDirection: SortDirection,
    isPinned: Boolean,
    hasFilter: Boolean,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    onTogglePin: () -> Unit,
    onResize: (Float) -> Unit,
)
```

#### 固定列实现
使用双列布局：
```
Row {
    // 固定列区域（不滚动）
    Column { /* 渲染固定列 */ }
    
    // 可滚动区域
    Row(Modifier.horizontalScroll()) {
        Column { /* 渲染非固定列 */ }
    }
}
```

**Why**: 将固定列和滚动列分离渲染，固定列使用独立的 Column 不参与横向滚动。

**How to apply**: 
- 固定列最多 3 列，超过限制时不允许继续固定
- 固定列始终显示在左侧，保持原始顺序
- 主键列默认建议固定（显示固定图标提示）

#### 列宽调整
在列头右边缘添加拖拽热区：
```kotlin
Modifier.pointerInput(Unit) {
    detectHorizontalDragGestures { _, dragAmount ->
        val newWidth = (currentWidth + dragAmount).coerceAtLeast(MIN_COL_WIDTH)
        onResize(newWidth)
    }
}
```

**默认列宽计算**: 基于列名和数据类型的视觉宽度：
- 数值类型: 120dp
- 文本类型: `max(列名宽度 + 60dp, 150dp)`
- 主键列: `列名宽度 + 40dp`

### 6. 筛选对话框

```kotlin
@Composable
private fun FilterDialog(
    columnName: String,
    currentFilter: String,
    currentFilterType: FilterType,
    onDismiss: () -> Unit,
    onConfirm: (FilterType, String) -> Unit,
)
```

包含：
- 筛选类型选择器（包含/不包含）
- 文本输入框
- 确定/取消按钮

**交互流程**:
1. 点击列头筛选图标打开对话框
2. 选择筛选类型（包含/不包含）
3. 输入筛选值
4. 点击确定应用筛选，清空分页缓存并重新加载第一页

## 实现细节

### 状态更新流程

#### 排序
```
用户点击列头 
  → toggleSort(columnName)
  → 更新 sortColumn/sortDirection
  → 清空页面缓存
  → 重新生成 SQL (带 ORDER BY)
  → 加载第一页数据
```

#### 筛选
```
用户输入筛选条件
  → setColumnFilter(columnName, type, value)
  → 更新 columnFilters
  → 清空页面缓存
  → 重新生成 SQL (带 WHERE)
  → 加载第一页数据
```

#### 固定列
```
用户点击右键菜单"固定列"
  → togglePinColumn(columnName)
  → 更新 pinnedColumns
  → 保存到 PreferencesStore
  → 触发 UI 重新布局
```

#### 列宽调整
```
用户拖拽列边缘
  → onResize 回调实时更新 columnWidths
  → 拖拽结束后保存到 PreferencesStore
  → UI 实时响应宽度变化
```

### 性能优化

1. **缓存失效策略**: 排序和筛选会清空 LRU 缓存，避免显示陈旧数据
2. **延迟保存**: 列宽调整使用 debounce，拖拽过程中不频繁写入存储
3. **增量更新**: 只更新变化的状态字段，避免全量状态刷新

### 边界情况处理

1. **空表**: 禁用排序和筛选功能
2. **超长列名**: 列头文本使用 `TextOverflow.Ellipsis` 截断
3. **最小列宽**: 限制最小宽度 80dp，避免列过窄无法操作
4. **固定列数量**: 最多固定 3 列，避免挤占可滚动区域
5. **SQL 注入**: 列名使用双引号包裹，筛选值进行转义

## 测试要点

1. **排序功能**: 升序、降序、取消排序的切换
2. **筛选功能**: 包含、不包含的文本匹配
3. **固定列**: 固定/取消固定，横向滚动时固定列保持可见
4. **列宽调整**: 拖拽调整，持久化保存，跨会话恢复
5. **组合使用**: 同时应用排序、筛选、固定列
6. **性能**: 大表（10万行）的排序和筛选响应时间
7. **边界**: 空表、单列表、超长列名

## 已知限制

1. **Keyset Pagination 降级**: 使用自定义排序时，分页性能从 O(1) 降级到 O(n)
2. **筛选类型有限**: 仅支持文本包含/不包含，不支持数值比较、日期范围等
3. **单表筛选**: 不支持跨表 JOIN 的筛选条件
4. **客户端排序**: 查询结果表格的排序在客户端进行，不修改原始 SQL

## 未来扩展

1. 支持更多筛选类型（数值比较、日期范围、正则表达式）
2. 支持列排序（拖拽改变列顺序）
3. 支持列分组（按某列分组显示）
4. 支持列聚合（求和、平均值、计数）
5. 支持列隐藏/显示切换
6. 导出时保留当前的排序和筛选设置

## 实现文件清单

- `app/src/main/java/com/example/dbviewer/data/TablePreferences.kt` - 数据模型
- `app/src/main/java/com/example/dbviewer/data/PreferencesStore.kt` - 持久化存储
- `app/src/main/java/com/example/dbviewer/data/DbSession.kt` - SQL 生成逻辑
- `app/src/main/java/com/example/dbviewer/presentation/DbViewModel.kt` - 状态管理
- `app/src/main/java/com/example/dbviewer/ui/components/DataGrid.kt` - UI 组件
- `app/src/main/java/com/example/dbviewer/ui/BrowserScreen.kt` - 数据浏览页面集成
- `app/src/main/java/com/example/dbviewer/ui/QueryScreen.kt` - 查询结果页面集成

## 参考资料

- [AG Grid Documentation](https://www.ag-grid.com/)
- [Tanstack Table](https://tanstack.com/table)
- [SQLite ORDER BY](https://www.sqlite.org/lang_select.html#orderby)
- [SQLite WHERE](https://www.sqlite.org/lang_select.html#where)
