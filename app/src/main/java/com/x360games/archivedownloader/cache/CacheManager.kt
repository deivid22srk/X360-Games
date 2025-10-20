package com.x360games.archivedownloader.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.x360games.archivedownloader.data.ArchiveItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "cache")

class CacheManager(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val CACHED_ITEMS_KEY = stringPreferencesKey("cached_archive_items")
        private val CACHE_TIMESTAMP_KEY = stringPreferencesKey("cache_timestamp")
        private const val CACHE_VALIDITY_MS = 3600000L * 24
    }
    
    suspend fun getCachedItems(): List<ArchiveItem>? {
        val prefs = context.cacheDataStore.data.firstOrNull() ?: return null
        val timestamp = prefs[CACHE_TIMESTAMP_KEY]?.toLongOrNull() ?: return null
        
        if (System.currentTimeMillis() - timestamp > CACHE_VALIDITY_MS) {
            return null
        }
        
        val json = prefs[CACHED_ITEMS_KEY] ?: return null
        
        return try {
            val type = object : TypeToken<List<ArchiveItem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun cacheItems(items: List<ArchiveItem>) {
        context.cacheDataStore.edit { prefs ->
            val json = gson.toJson(items)
            prefs[CACHED_ITEMS_KEY] = json
            prefs[CACHE_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
        }
    }
    
    suspend fun clearCache() {
        context.cacheDataStore.edit { prefs ->
            prefs.remove(CACHED_ITEMS_KEY)
            prefs.remove(CACHE_TIMESTAMP_KEY)
        }
    }
}
