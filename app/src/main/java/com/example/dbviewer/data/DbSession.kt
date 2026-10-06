package com.example.dbviewer.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.floor

class DbSession private constructor(private val db: SQLiteDatabase, val file: File) : AutoCloseable {
    init {
        // PRAGMA assignments return a result cursor on Android and must use rawQuery.
        db.rawQuery("PRAGMA query_only=ON", null).use { it.moveToFirst() }
        db.rawQuery("PRAGMA busy_timeout=3000", null).use { it.moveToFirst() }
        db.rawQuery("PRAGMA query_only", null).use { c -> require(c.moveToFirst() && c.getInt(0) == 1) { "Read-only mode could not be enabled" } }
    }

    fun objects(): List<DbObject> = query("SELECT type, name FROM sqlite_master WHERE type IN ('table','view','index','trigger') AND name NOT LIKE 'sqlite_%' ORDER BY type,name").map { DbObject(it[0] ?: "", it[1] ?: "") }

    fun tables(): List<String> = query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").mapNotNull { it.firstOrNull() }

    fun columns(table: String): List<ColumnInfo> = query("PRAGMA table_xinfo(${quote(table)})").map { ColumnInfo(it.getOrNull(1), it.getOrNull(2), it.getOrNull(3) == "1", it.getOrNull(4), it.getOrNull(5)?.toIntOrNull() ?: 0, it.getOrNull(6)?.toIntOrNull() ?: 0) }

    fun indexes(table: String): List<IndexInfo> = query("PRAGMA index_list(${quote(table)})").map { row ->
        val name = row.getOrNull(1) ?: ""
        val fields = query("PRAGMA index_xinfo(${quote(name)})").mapNotNull { field ->
            val cid = field.getOrNull(1)?.toIntOrNull() ?: -1
            if (cid < 0 || field.getOrNull(5)?.toIntOrNull() == 0) null else IndexField(field.getOrNull(2), field.getOrNull(3)?.toIntOrNull() == 1, field.getOrNull(4))
        }
        IndexInfo(name, row.getOrNull(2) == "1", row.getOrNull(3), row.getOrNull(4) == "1", fields)
    }

    fun triggers(table: String? = null): List<TriggerInfo> {
        val suffix = if (table == null) "" else " AND tbl_name = ?"
        val args = if (table == null) emptyArray() else arrayOf(table)
        return query("SELECT name, tbl_name, sql FROM sqlite_master WHERE type='trigger' AND name NOT LIKE 'sqlite_%'$suffix ORDER BY name", args).map { TriggerInfo(it.getOrNull(0) ?: "", it.getOrNull(1) ?: "", it.getOrNull(2)) }
    }

    fun estimatedRowCount(table: String): RowCount {
        val estimate = runCatching { db.rawQuery("SELECT stat FROM sqlite_stat1 WHERE tbl = ? LIMIT 1", arrayOf(table)).use { c -> if (c.moveToFirst()) c.getString(0)?.substringBefore(' ')?.toLongOrNull() else null } }.getOrNull()
        if (estimate != null && estimate >= 0) return RowCount(estimate, false)
        return RowCount(exactRowCount(table), true)
    }

    fun exactRowCount(table: String): Long = db.rawQuery("SELECT count(*) FROM ${quote(table)}", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /** CREATE statement of the object plus the DDL of every index and trigger attached to it. */
    fun ddl(table: String): List<DdlItem> {
        val items = mutableListOf<DdlItem>()
        query("SELECT type, name, sql FROM sqlite_master WHERE name = ? AND type IN ('table','view')", arrayOf(table)).firstOrNull()?.let { row ->
            items += DdlItem(row.getOrNull(1) ?: table, row.getOrNull(0) ?: "table", row.getOrNull(2) ?: "")
        }
        query("SELECT type, name, sql FROM sqlite_master WHERE tbl_name = ? AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type, name", arrayOf(table)).forEach { row ->
            items += DdlItem(row.getOrNull(1) ?: "", row.getOrNull(0) ?: "", row.getOrNull(2) ?: "")
        }
        return items
    }

    /**
     * Reads one page of rows using Keyset Pagination (WHERE id > lastId) when possible for optimal performance,
     * or falls back to LIMIT/OFFSET for tables without suitable primary keys.
     * Keyset pagination avoids scanning millions of rows when navigating large tables.
     */
    fun pageAt(table: String, offset: Int, limit: Int, lastSeenId: Long? = null, direction: PageDirection = PageDirection.NEXT): PagedRows {
        val plans = columnPlan(table)
        require(plans.isNotEmpty()) { "Table has no readable columns" }
        val projection = plans.joinToString(", ") { "${it.expression} AS ${quote(it.name)}" }
        val identityPlan = plans.firstOrNull { it.isIdentity }

        // Use Keyset Pagination if we have an INTEGER PRIMARY KEY and are navigating sequentially
        val keysetCursor = identityPlan?.let { plan -> lastSeenId?.let { plan to it } }

        val sql = if (keysetCursor != null) {
            val (keysetPlan, seenId) = keysetCursor
            val operator = if (direction == PageDirection.NEXT) ">" else "<"
            val order = if (direction == PageDirection.NEXT) "ASC" else "DESC"
            "SELECT $projection FROM ${quote(table)} WHERE ${keysetPlan.expression} $operator $seenId ORDER BY ${keysetPlan.expression} $order LIMIT ${limit + 1}"
        } else {
            // Fallback to OFFSET pagination for tables without primary key or first page
            val safeOffset = offset.coerceAtLeast(0)
            "SELECT $projection FROM ${quote(table)}${orderClause(plans)} LIMIT ${limit + 1} OFFSET $safeOffset"
        }

        val visible = plans.withIndex().filter { !it.value.internal }
        val identityIndex = plans.indexOfFirst { it.isIdentity }

        db.rawQuery(sql, null).use { c ->
            val rows = ArrayList<GridRow>(limit)
            var hasMore = false
            var index = 0
            while (c.moveToNext()) {
                if (index == limit) { hasMore = true; break }
                val values = visible.map { cellText(it.value, c, it.index) }
                val id = if (identityIndex >= 0 && c.getType(identityIndex) == Cursor.FIELD_TYPE_INTEGER) c.getLong(identityIndex) else null
                rows += GridRow(position = offset + index, id = id, values = values)
                index++
            }
            // Reverse rows if we queried backwards
            val finalRows = if (keysetCursor != null && direction == PageDirection.PREVIOUS) rows.reversed() else rows
            // Keep the capability flag set for the initial OFFSET page as well. The ViewModel
            // uses it to switch the next sequential navigation request to keyset pagination.
            return PagedRows(finalRows, hasMore, identityPlan != null)
        }
    }

    fun executeReadOnly(sql: String, limit: Int = 5000): QueryResult {
        validate(sql)
        val trimmed = sql.trim().trimEnd(';').trim()
        val head = trimmed.split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase() ?: ""
        // PRAGMA statements cannot be nested in a subquery, every other read-only statement can.
        // The newlines matter: they terminate a trailing "--" comment before the closing parenthesis.
        val statement = if (head == "pragma") trimmed else "SELECT * FROM (\n$trimmed\n) LIMIT ${limit + 1}"
        db.rawQuery(statement, null).use { c ->
            val names = c.columnNames.toList()
            val rows = ArrayList<List<String?>>()
            var hasMore = false
            while (c.moveToNext()) {
                if (rows.size == limit) { hasMore = true; break }
                rows += names.indices.map { readValue(c, it) }
            }
            return QueryResult(names, rows, hasMore)
        }
    }

    private fun validate(sql: String) { val stripped = sql.replace(Regex("(?s)--.*?$|/\\*.*?\\*/"), " ").trim(); val normalized = stripped.trimEnd(';').trim(); require(stripped.count { it == ';' } <= 1 && normalized.isNotEmpty()) { "Only one SQL statement is allowed" }; val first = normalized.trimStart().split(Regex("\\s+"), limit = 2).first().lowercase(); require(first == "select" || first == "with" || first == "pragma") { "Only read-only SELECT, CTE, or PRAGMA statements are allowed" }; require(!Regex("(?i)\\b(insert|update|delete|replace|create|drop|alter|vacuum|reindex|attach|detach|begin|commit|rollback|savepoint|release|load_extension|pragma\\s+(?!query_only|busy_timeout|table_info|table_xinfo|index_list|index_info|foreign_key_list|compile_options)\\w+)\\b").containsMatchIn(stripped)) { "Write or state-changing SQL is rejected" } }

    private fun readValue(c: Cursor, index: Int): String? = when (c.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(index).toString()
        Cursor.FIELD_TYPE_FLOAT -> {
            val value = c.getDouble(index)
            if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) value.toLong().toString() else value.toString()
        }
        Cursor.FIELD_TYPE_BLOB -> runCatching { c.getBlob(index)?.size }.getOrNull()?.let { "<BLOB $it B>" } ?: "<BLOB>"
        else -> c.getString(index)?.let { if (it.length > MAX_CELL_CHARS) it.take(MAX_CELL_CHARS) + "…" else it }
    }

    private fun cellText(plan: ColumnPlan, c: Cursor, index: Int): String? {
        val raw = readValue(c, index) ?: return null
        return if (plan.blobLength) "<BLOB $raw B>" else raw
    }

    private fun columnPlan(table: String): List<ColumnPlan> {
        data class Meta(val name: String, val type: String, val pk: Int, val hidden: Int)
        val metas = query("PRAGMA table_xinfo(${quote(table)})").mapNotNull { row ->
            val name = row.getOrNull(1) ?: return@mapNotNull null
            Meta(name, row.getOrNull(2) ?: "", row.getOrNull(5)?.toIntOrNull() ?: 0, row.getOrNull(6)?.toIntOrNull() ?: 0)
        }.filter { it.hidden == 0 }
        val identityName = metas.firstOrNull { it.pk == 1 && it.type.contains("INT", ignoreCase = true) }?.name
        val plans = mutableListOf<ColumnPlan>()
        if (identityName == null && hasRowId(table)) plans += ColumnPlan("rowid", "rowid", isIdentity = true, internal = true, blobLength = false, pkOrder = 0)
        metas.forEach { meta ->
            val blob = meta.type.contains("BLOB", ignoreCase = true)
            plans += ColumnPlan(
                name = meta.name,
                // A blob is read as its byte length so browsing never pulls megabytes into the cell.
                expression = if (blob) "length(${quote(meta.name)})" else quote(meta.name),
                isIdentity = meta.name == identityName,
                internal = false,
                blobLength = blob,
                pkOrder = meta.pk,
            )
        }
        return plans
    }

    private fun orderClause(plans: List<ColumnPlan>): String {
        plans.firstOrNull { it.isIdentity }?.let { return " ORDER BY ${it.expression}" }
        val keys = plans.filter { it.pkOrder > 0 }.sortedBy { it.pkOrder }
        if (keys.isEmpty()) return ""
        return " ORDER BY " + keys.joinToString(", ") { it.expression }
    }

    private fun query(sql: String, args: Array<String> = emptyArray()): List<List<String?>> = db.rawQuery(sql, args).use { c -> val out = mutableListOf<List<String?>>(); while (c.moveToNext()) out += (0 until c.columnCount).map { i -> readValue(c, i) }; out }

    private fun hasRowId(table: String): Boolean = runCatching { db.rawQuery("SELECT rowid FROM ${quote(table)} LIMIT 1", null).use { true } }.getOrDefault(false)

    private fun quote(s: String) = "\"${s.replace("\"", "\"\"")}\""

    override fun close() { runCatching { db.close() }; file.delete() }

    private data class ColumnPlan(val name: String, val expression: String, val isIdentity: Boolean, val internal: Boolean, val blobLength: Boolean, val pkOrder: Int)

    companion object {
        fun open(context: Context, uri: Uri): DbSession { val dst = File.createTempFile("dbviewer_", ".db", context.cacheDir); try { context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Cannot read selected file" }; FileOutputStream(dst).use { output -> val buffer = ByteArray(DEFAULT_BUFFER); var total = 0L; while (true) { val count = input.read(buffer); if (count < 0) break; if (context.cacheDir.usableSpace < count) throw IOException("Not enough cache storage"); output.write(buffer, 0, count); total += count; if (total > MAX_DATABASE_BYTES) throw IOException("Database exceeds supported size") } } }; val db = SQLiteDatabase.openDatabase(dst.path, null, SQLiteDatabase.OPEN_READONLY); return DbSession(db, dst) } catch (t: Throwable) { dst.delete(); throw t } }
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val MAX_DATABASE_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_CELL_CHARS = 512
    }
}

enum class PageDirection { NEXT, PREVIOUS }

data class DbObject(val kind: String, val name: String)
data class GridRow(val position: Int, val id: Long?, val values: List<String?>)
data class PagedRows(val rows: List<GridRow>, val hasMore: Boolean, val usedKeyset: Boolean = false)
data class QueryResult(val columns: List<String>, val rows: List<List<String?>>, val hasMore: Boolean)
data class DdlItem(val name: String, val kind: String, val sql: String)
data class ColumnInfo(val name: String?, val type: String?, val notNull: Boolean, val defaultValue: String?, val primaryKeyOrder: Int, val hidden: Int = 0)
data class IndexInfo(val name: String, val unique: Boolean, val origin: String? = null, val partial: Boolean = false, val fields: List<IndexField> = emptyList())
data class IndexField(val name: String?, val descending: Boolean, val collation: String?)
data class TriggerInfo(val name: String, val table: String, val sql: String?)
data class RowCount(val value: Long, val exact: Boolean)
