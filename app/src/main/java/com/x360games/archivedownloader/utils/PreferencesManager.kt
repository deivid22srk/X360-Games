package com.x360games.archivedownloader.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val COOKIES_KEY = stringPreferencesKey("cookies")
        private val DOWNLOAD_PATH_KEY = stringPreferencesKey("download_path")
        private val AUTO_EXTRACT_KEY = booleanPreferencesKey("auto_extract")
        private val EXTRACTION_PATH_KEY = stringPreferencesKey("extraction_path")
        private val SETUP_COMPLETED_KEY = booleanPreferencesKey("setup_completed")
        private val CONCURRENT_DOWNLOADS_KEY = intPreferencesKey("concurrent_downloads")
        private val DOWNLOAD_PARTS_KEY = intPreferencesKey("download_parts")
        private val DATA_SOURCE_KEY = stringPreferencesKey("data_source")
    }
    
    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }
    
    val cookies: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[COOKIES_KEY]
    }
    
    val downloadPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_PATH_KEY]
    }
    
    val autoExtract: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_EXTRACT_KEY] ?: false
    }
    
    val extractionPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EXTRACTION_PATH_KEY]
    }
    
    val setupCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED_KEY] ?: false
    }
    
    val concurrentDownloads: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CONCURRENT_DOWNLOADS_KEY] ?: 1
    }
    
    val downloadParts: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_PARTS_KEY] ?: 4
    }
    
    val dataSource: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DATA_SOURCE_KEY] ?: "internet_archive"
    }
    
    suspend fun saveCredentials(username: String, cookies: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            preferences[COOKIES_KEY] = cookies
        }
    }
    
    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(USERNAME_KEY)
            preferences.remove(COOKIES_KEY)
        }
    }
    
    suspend fun saveDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH_KEY] = path
        }
    }
    
    suspend fun setAutoExtract(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_EXTRACT_KEY] = enabled
        }
    }
    
    suspend fun saveExtractionPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[EXTRACTION_PATH_KEY] = path
        }
    }
    
    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETED_KEY] = completed
        }
    }
    
    suspend fun setConcurrentDownloads(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONCURRENT_DOWNLOADS_KEY] = count
        }
    }
    
    suspend fun setDownloadParts(parts: Int) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PARTS_KEY] = parts.coerceIn(1, 16)
        }
    }
    
    suspend fun setDataSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[DATA_SOURCE_KEY] = source
        }
    }
}
