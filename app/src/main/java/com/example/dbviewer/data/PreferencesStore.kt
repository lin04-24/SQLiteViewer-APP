package com.example.dbviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesStore(private val context: Context) {
    private val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
    private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    private val tablePreferences = TablePreferencesStore(context)

    val autoCheckUpdates: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CHECK_UPDATES] ?: false
    }

    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK] ?: 0L
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CHECK_UPDATES] = enabled
        }
    }

    suspend fun updateLastCheckTime() {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK] = System.currentTimeMillis()
        }
    }

    /**
     * Loads table-specific grid preferences using a stable database URI/path key.
     */
    suspend fun loadTablePreferences(dbPath: String, tableName: String): TablePreferences =
        tablePreferences.load(dbPath, tableName)

    /**
     * Saves table-specific grid preferences across app sessions.
     */
    suspend fun saveTablePreferences(dbPath: String, tableName: String, preferences: TablePreferences) {
        tablePreferences.save(dbPath, tableName, preferences)
    }
}
