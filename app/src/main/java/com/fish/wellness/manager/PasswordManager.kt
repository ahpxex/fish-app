package com.fish.wellness.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.passwordDataStore: DataStore<Preferences> by preferencesDataStore("password_prefs")

@Singleton
class PasswordManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("password_hash")

    fun observeHasPassword(): Flow<Boolean> =
        context.passwordDataStore.data.map { it[key] != null }

    suspend fun hasPassword(): Boolean =
        context.passwordDataStore.data.first()[key] != null

    suspend fun setPassword(password: String) {
        context.passwordDataStore.edit { it[key] = hash(password) }
    }

    suspend fun verifyPassword(input: String): Boolean {
        val stored = context.passwordDataStore.data.first()[key] ?: return false
        return stored == hash(input)
    }

    suspend fun clearPassword() {
        context.passwordDataStore.edit { it.remove(key) }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
