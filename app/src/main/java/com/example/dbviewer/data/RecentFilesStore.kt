package com.example.dbviewer.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: String, val name: String, val lastAccess: Long)

class RecentFilesStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun list(): List<RecentFile> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            RecentFile(item.getString("uri"), item.getString("name"), item.getLong("lastAccess"))
        }.sortedByDescending { it.lastAccess }
    }.getOrDefault(emptyList())

    fun remember(uri: Uri, name: String) {
        val current = list().filterNot { it.uri == uri.toString() }.take(9).toMutableList()
        current.add(0, RecentFile(uri.toString(), name, System.currentTimeMillis()))
        save(current)
    }

    fun forget(uri: String) { save(list().filterNot { it.uri == uri }) }

    fun clear() { prefs.edit().remove("items").apply() }

    private fun save(items: List<RecentFile>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("lastAccess", it.lastAccess)) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
