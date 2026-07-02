package com.chatapp.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class AuthManager(private val ctx: Context) {

    companion object {
        private val K_TOKEN = stringPreferencesKey("token")
        private val K_UID = stringPreferencesKey("uid")
        private val K_UNAME = stringPreferencesKey("uname")
    }

    val token: Flow<String?> = ctx.dataStore.data.map { it[K_TOKEN] }
    val uid: Flow<String?> = ctx.dataStore.data.map { it[K_UID] }
    val uname: Flow<String?> = ctx.dataStore.data.map { it[K_UNAME] }

    suspend fun save(t: String, id: Long, name: String) {
        ctx.dataStore.edit {
            it[K_TOKEN] = t
            it[K_UID] = id.toString()
            it[K_UNAME] = name
        }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}
