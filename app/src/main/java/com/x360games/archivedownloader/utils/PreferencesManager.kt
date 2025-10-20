package com.x360games.archivedownloader.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val COOKIES_KEY = stringPreferencesKey("cookies")
    }
    
    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }
    
    val cookies: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[COOKIES_KEY]
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
}
