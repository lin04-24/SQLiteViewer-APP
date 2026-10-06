package com.example.dbviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.tablePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "table_preferences")

/**
 * 表格交互偏好设置，按 "dbPath:tableName" 为键保存。
 * 包括列宽、固定列、排序状态等用户自定义设置。
 */
@Serializable
data class TablePreferences(
    val columnWidths: Map<String, Float> = emptyMap(),  // 列名 -> 宽度(dp)
    val pinnedColumns: Set<String> = emptySet(),        // 固定的列名集合
    val sortColumn: String? = null,                     // 当前排序列
    val sortDirection: String? = null,                  // ASC, DESC, null
)

enum class SortDirection {
    ASC, DESC, NONE;

    fun next(): SortDirection = when (this) {
        NONE -> ASC
        ASC -> DESC
        DESC -> NONE
    }
}

fun String?.toSortDirection(): SortDirection = when (this?.uppercase()) {
    "ASC" -> SortDirection.ASC
    "DESC" -> SortDirection.DESC
    else -> SortDirection.NONE
}

val SortDirection.persistedName: String?
    get() = when (this) {
        SortDirection.ASC -> "ASC"
        SortDirection.DESC -> "DESC"
        SortDirection.NONE -> null
    }

class TablePreferencesStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 加载指定表的偏好设置
     */
    suspend fun load(dbPath: String, tableName: String): TablePreferences {
        val key = makeKey(dbPath, tableName)
        val prefKey = stringPreferencesKey(key)
        val prefs = context.tablePrefsDataStore.data.first()
        val jsonString = prefs[prefKey] ?: return TablePreferences()
        return runCatching { json.decodeFromString<TablePreferences>(jsonString) }
            .getOrDefault(TablePreferences())
    }

    /**
     * 保存指定表的偏好设置
     */
    suspend fun save(dbPath: String, tableName: String, preferences: TablePreferences) {
        val key = makeKey(dbPath, tableName)
        val prefKey = stringPreferencesKey(key)
        val jsonString = json.encodeToString(preferences)
        context.tablePrefsDataStore.edit { prefs ->
            prefs[prefKey] = jsonString
        }
    }

    /**
     * 清除指定表的偏好设置
     */
    suspend fun clear(dbPath: String, tableName: String) {
        val key = makeKey(dbPath, tableName)
        val prefKey = stringPreferencesKey(key)
        context.tablePrefsDataStore.edit { prefs ->
            prefs.remove(prefKey)
        }
    }

    private fun makeKey(dbPath: String, tableName: String): String {
        return "$dbPath:$tableName"
    }
}
